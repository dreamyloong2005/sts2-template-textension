package com.dreamyloong.template.sts2.android

import android.os.Handler
import android.os.Looper
import com.dreamyloong.template.sts2.STS2_STEAM_BRANCH_DEFAULT
import com.dreamyloong.template.sts2.STS2_STEAM_BRANCH_PUBLIC
import com.dreamyloong.template.sts2.STS2_STEAM_BRANCH_PUBLIC_BETA
import com.dreamyloong.template.sts2.Sts2GameFileCheckMode
import com.dreamyloong.template.sts2.Sts2GameFileCheckSnapshot
import com.dreamyloong.template.sts2.Sts2GameFileCheckStatus
import com.dreamyloong.template.sts2.Sts2GameFileDownloadSnapshot
import com.dreamyloong.template.sts2.Sts2GameFileDownloadStatus
import com.dreamyloong.template.sts2.Sts2GameFilePreferences
import com.dreamyloong.template.sts2.PersistentSts2GameFileStateStore
import com.dreamyloong.template.sts2.Sts2SteamRepairPlanRecord
import com.dreamyloong.template.sts2.Sts2SteamVerificationRecord
import com.dreamyloong.template.sts2.Sts2VersionDefinition
import com.dreamyloong.template.sts2.normalizeSteamBranch
import com.dreamyloong.template.sts2.normalizedGameFileVersion
import com.dreamyloong.template.sts2.sts2Localized as steamLocalized
import com.dreamyloong.tlauncher.sdk.account.LauncherAccount
import com.dreamyloong.tlauncher.sdk.account.LauncherAccountProvider
import com.dreamyloong.tlauncher.sdk.account.SteamAccountLoginMode
import com.dreamyloong.tlauncher.sdk.extension.ExtensionHostPaths
import com.dreamyloong.tlauncher.sdk.extension.ExtensionStateStore
import com.dreamyloong.tlauncher.sdk.host.SteamDepotLocalVerifyEntry
import com.dreamyloong.tlauncher.sdk.host.SteamDepotLocalVerifyResult
import com.dreamyloong.tlauncher.sdk.host.SteamDepotPreflightEntry
import com.dreamyloong.tlauncher.sdk.host.SteamDepotService
import com.dreamyloong.tlauncher.sdk.host.SteamDepotTaskSnapshot
import com.dreamyloong.tlauncher.sdk.host.SteamManifestFileEntry
import com.dreamyloong.tlauncher.sdk.i18n.SupportedLanguage
import com.dreamyloong.tlauncher.sdk.model.GameInstanceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val STS2_STEAM_APP_ID = 2868840
private const val STS2_STEAM_MAX_COUNT = 20
private const val STS2_STEAM_MANIFEST_FILE_LIMIT = 100_000
private const val STS2_STEAM_MANIFEST_CACHE_DIRECTORY = "sts2/steam/manifests"
private const val STS2_STEAM_CHECK_PROGRESS_STEP = 64
private const val STS2_STEAM_PROGRESS_PUBLISH_INTERVAL_MILLIS = 120L
private const val STS2_STEAM_DOWNLOAD_POLL_INTERVAL_MILLIS = 150L
private const val STS2_STEAM_UI_REFRESH_INTERVAL_MILLIS = 400L
private val sts2SteamDepotMainHandler = Handler(Looper.getMainLooper())

internal class Sts2SteamDepotGameFileCoordinator(
    stateStore: ExtensionStateStore,
    private val hostPaths: ExtensionHostPaths,
    private val steamDepot: SteamDepotService?,
) {
    private val store = PersistentSts2GameFileStateStore(stateStore)
    private val checkCancelSignals = ConcurrentHashMap<String, AtomicBoolean>()
    private val downloadCancelSignals = ConcurrentHashMap<String, AtomicBoolean>()
    private val downloadPauseSignals = ConcurrentHashMap<String, AtomicBoolean>()

    fun preferences(instanceId: GameInstanceId): Sts2GameFilePreferences = store.preferences(instanceId)

    fun updateSelectedSteamAccount(
        instanceId: GameInstanceId,
        subjectId: String?,
    ) {
        store.updatePreferences(instanceId) { current ->
            current.copy(selectedSteamAccountSubjectId = subjectId?.trim().takeUnless { it.isNullOrBlank() })
        }
        store.clearCheckSnapshot(instanceId)
    }

    fun updateSteamVerificationEnabled(
        instanceId: GameInstanceId,
        enabled: Boolean,
        language: SupportedLanguage,
        onStateChanged: () -> Unit,
    ) {
        cancelSteamCheck(instanceId, language, onStateChanged)
        store.updatePreferences(instanceId) { current ->
            current.copy(steamVerificationEnabled = enabled)
        }
        store.clearCheckSnapshot(instanceId)
        store.clearDownloadSnapshot(instanceId)
    }

    fun updateSteamBranch(
        instanceId: GameInstanceId,
        steamBranch: String,
        language: SupportedLanguage,
        onStateChanged: () -> Unit,
    ) {
        cancelSteamCheck(instanceId, language, onStateChanged)
        cancelSelectiveDownload(instanceId, language, onStateChanged)
        store.updatePreferences(instanceId) { current ->
            current.copy(steamBranch = normalizeSteamBranch(steamBranch))
        }
        store.clearCheckSnapshot(instanceId)
        store.clearDownloadSnapshot(instanceId)
    }

    fun checkSnapshot(instanceId: GameInstanceId): Sts2GameFileCheckSnapshot = store.checkSnapshot(instanceId)

    fun steamVerificationRecord(
        instanceId: GameInstanceId,
        version: Sts2VersionDefinition?,
        steamBranch: String,
    ): Sts2SteamVerificationRecord = store.steamVerificationRecord(instanceId, version, steamBranch)

    fun downloadSnapshot(instanceId: GameInstanceId): Sts2GameFileDownloadSnapshot = store.downloadSnapshot(instanceId)

    fun cancelSteamCheck(
        instanceId: GameInstanceId,
        language: SupportedLanguage,
        onStateChanged: () -> Unit,
    ) {
        val signal = checkCancelSignals[instanceId.value] ?: return
        signal.set(true)
        val current = store.checkSnapshot(instanceId)
        if (current.status == Sts2GameFileCheckStatus.RUNNING) {
            store.writeCheckSnapshot(
                instanceId,
                current.copy(
                    status = Sts2GameFileCheckStatus.CANCELED,
                    passed = false,
                    message = steamLocalized(language, "android.steam.0001"),
                    checkedAtMillis = System.currentTimeMillis(),
                ),
            )
            notifyUi(onStateChanged)
        }
    }

    fun cancelSelectiveDownload(
        instanceId: GameInstanceId,
        language: SupportedLanguage,
        onStateChanged: () -> Unit,
    ) {
        val signal = downloadCancelSignals[instanceId.value] ?: return
        signal.set(true)
        val current = store.downloadSnapshot(instanceId)
        if (current.status == Sts2GameFileDownloadStatus.RUNNING) {
            store.writeDownloadSnapshot(
                instanceId,
                current.copy(
                    status = Sts2GameFileDownloadStatus.CANCELED,
                    message = steamLocalized(language, "android.steam.0002"),
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
            notifyUi(onStateChanged)
        }
    }

    fun pauseSelectiveDownload(
        instanceId: GameInstanceId,
        language: SupportedLanguage,
        onStateChanged: () -> Unit,
    ) {
        val signal = downloadPauseSignals[instanceId.value] ?: return
        signal.set(true)
        val current = store.downloadSnapshot(instanceId)
        if (current.status == Sts2GameFileDownloadStatus.RUNNING) {
            store.writeDownloadSnapshot(
                instanceId,
                current.copy(
                    message = steamLocalized(language, "android.steam.0003"),
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
            notifyUi(onStateChanged)
        }
    }

    fun startSteamCheck(
        instanceId: GameInstanceId,
        language: SupportedLanguage,
        version: Sts2VersionDefinition?,
        selectedSteamAccount: LauncherAccount?,
        steamBranch: String,
        manageStorageGranted: Boolean,
        onStateChanged: () -> Unit,
    ) {
        val normalizedVersion = version?.normalizedGameFileVersion()
        val normalizedBranch = normalizeSteamBranch(steamBranch)
        val cancelSignal = AtomicBoolean(false)
        val progressPublisher = ThrottledProgressPublisher<Sts2GameFileCheckSnapshot>(
            minIntervalMillis = STS2_STEAM_UI_REFRESH_INTERVAL_MILLIS,
        ) { snapshot ->
            store.writeCheckSnapshot(instanceId, snapshot)
            notifyUi(onStateChanged)
        }
        checkCancelSignals[instanceId.value] = cancelSignal
        store.writeCheckSnapshot(
            instanceId,
            Sts2GameFileCheckSnapshot(
                status = Sts2GameFileCheckStatus.RUNNING,
                mode = Sts2GameFileCheckMode.STEAM,
                versionClientId = normalizedVersion?.clientId,
                gameDirectory = normalizedVersion?.gameDirectory,
                steamAccountSubjectId = selectedSteamAccount?.subjectId,
                steamBranch = normalizedBranch,
                passed = false,
                message = steamLocalized(language, "android.steam.0004"),
                checkedAtMillis = System.currentTimeMillis(),
            ),
        )
        notifyUi(onStateChanged)
        Thread {
            try {
                val result = runCatching {
                    runBlocking(Dispatchers.IO) {
                        performSteamCheck(
                            language = language,
                            version = normalizedVersion,
                            selectedSteamAccount = selectedSteamAccount,
                            steamBranch = normalizedBranch,
                            manageStorageGranted = manageStorageGranted,
                            hostPaths = hostPaths,
                            steamDepot = steamDepot,
                            cancelSignal = cancelSignal,
                            onProgress = { snapshot ->
                                progressPublisher.publish(snapshot)
                            },
                        )
                    }
                }
                val resultSnapshot = result.fold(
                    onSuccess = { snapshot ->
                        if (cancelSignal.get()) {
                            buildSteamCheckCanceledSnapshot(
                                language = language,
                                version = normalizedVersion,
                                selectedSteamAccount = selectedSteamAccount,
                                steamBranch = normalizedBranch,
                                previous = store.checkSnapshot(instanceId),
                            )
                        } else {
                            snapshot
                        }
                    },
                    onFailure = { error ->
                        if (error is Sts2SteamOperationCanceledException || cancelSignal.get()) {
                            buildSteamCheckCanceledSnapshot(
                                language = language,
                                version = normalizedVersion,
                                selectedSteamAccount = selectedSteamAccount,
                                steamBranch = normalizedBranch,
                                previous = store.checkSnapshot(instanceId),
                            )
                        } else {
                            buildSteamCheckFailureSnapshot(
                                language = language,
                                version = normalizedVersion,
                                selectedSteamAccount = selectedSteamAccount,
                                steamBranch = normalizedBranch,
                                message = classifySteamFailureMessage(
                                    language = language,
                                    rawMessage = error.message?.takeIf { it.isNotBlank() }
                                    ?: steamLocalized(language, "android.steam.0005"),
                                ),
                            )
                        }
                    },
                )
                if (
                    resultSnapshot.status == Sts2GameFileCheckStatus.COMPLETED &&
                    store.downloadSnapshot(instanceId).status != Sts2GameFileDownloadStatus.PAUSED
                ) {
                    store.clearDownloadSnapshot(instanceId)
                }
                store.writeSteamVerificationRecord(instanceId, resultSnapshot)
                store.writeSteamRepairPlanRecord(instanceId, resultSnapshot)
                store.writeCheckSnapshot(instanceId, resultSnapshot)
                notifyUi(onStateChanged)
            } finally {
                checkCancelSignals.remove(instanceId.value, cancelSignal)
            }
        }.start()
    }

    fun startSelectiveDownload(
        instanceId: GameInstanceId,
        language: SupportedLanguage,
        version: Sts2VersionDefinition?,
        selectedSteamAccount: LauncherAccount?,
        steamBranch: String,
        manageStorageGranted: Boolean,
        onStateChanged: () -> Unit,
    ) {
        val normalizedVersion = version?.normalizedGameFileVersion()
        val normalizedBranch = normalizeSteamBranch(steamBranch)
        val cancelSignal = AtomicBoolean(false)
        val pauseSignal = AtomicBoolean(false)
        val previousSnapshot = store.downloadSnapshot(instanceId)
        val isResume = previousSnapshot.status == Sts2GameFileDownloadStatus.PAUSED &&
            previousSnapshot.matches(normalizedVersion, normalizedBranch) &&
            previousSnapshot.steamAccountSubjectId == selectedSteamAccount?.subjectId
        val progressPublisher = ThrottledProgressPublisher<Sts2GameFileDownloadSnapshot>(
            minIntervalMillis = STS2_STEAM_UI_REFRESH_INTERVAL_MILLIS,
        ) { snapshot ->
            store.writeDownloadSnapshot(instanceId, snapshot)
            notifyUi(onStateChanged)
        }
        downloadCancelSignals[instanceId.value] = cancelSignal
        downloadPauseSignals[instanceId.value] = pauseSignal
        store.writeDownloadSnapshot(
            instanceId,
            Sts2GameFileDownloadSnapshot(
                status = Sts2GameFileDownloadStatus.RUNNING,
                versionClientId = normalizedVersion?.clientId,
                gameDirectory = normalizedVersion?.gameDirectory,
                steamAccountSubjectId = selectedSteamAccount?.subjectId,
                steamBranch = normalizedBranch,
                message = steamLocalized(
                    language,
                    if (isResume) {
                        "android.steam.download.resuming_paused_task"
                    } else {
                        "android.steam.download.preparing_repair_files"
                    },
                ),
                currentFilePath = previousSnapshot.currentFilePath.takeIf { isResume },
                totalFileCount = previousSnapshot.totalFileCount.takeIf { isResume },
                completedFileCount = previousSnapshot.completedFileCount.takeIf { isResume },
                skippedFileCount = previousSnapshot.skippedFileCount.takeIf { isResume },
                deletedExtraFileCount = previousSnapshot.deletedExtraFileCount.takeIf { isResume },
                currentFileDownloadedBytes = previousSnapshot.currentFileDownloadedBytes.takeIf { isResume },
                currentFileTotalBytes = previousSnapshot.currentFileTotalBytes.takeIf { isResume },
                totalBytes = previousSnapshot.totalBytes.takeIf { isResume },
                downloadedBytes = previousSnapshot.downloadedBytes.takeIf { isResume },
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
        notifyUi(onStateChanged)
        Thread {
            try {
                val result = runCatching {
                    runBlocking(Dispatchers.IO) {
                            performSelectiveDownload(
                                language = language,
                                version = normalizedVersion,
                                selectedSteamAccount = selectedSteamAccount,
                                steamBranch = normalizedBranch,
                                manageStorageGranted = manageStorageGranted,
                                hostPaths = hostPaths,
                                steamDepot = steamDepot,
                                cancelSignal = cancelSignal,
                                pauseSignal = pauseSignal,
                                onProgress = { snapshot ->
                                    progressPublisher.publish(snapshot)
                                },
                                loadCachedRepairPlan = { manifestId ->
                                    store.steamRepairPlanRecord(
                                        instanceId = instanceId,
                                        version = normalizedVersion,
                                        steamBranch = normalizedBranch,
                                        steamManifestId = manifestId,
                                    )
                                },
                            )
                        }
                }
                result.onSuccess { outcome ->
                    if (cancelSignal.get()) {
                        store.writeDownloadSnapshot(
                            instanceId,
                            buildSteamDownloadCanceledSnapshot(
                                language = language,
                                version = normalizedVersion,
                                selectedSteamAccount = selectedSteamAccount,
                                steamBranch = normalizedBranch,
                                previous = store.downloadSnapshot(instanceId),
                            ),
                        )
                    } else {
                        store.writeDownloadSnapshot(instanceId, outcome.downloadSnapshot)
                        outcome.postDownloadCheckSnapshot?.let { snapshot ->
                            store.writeCheckSnapshot(instanceId, snapshot)
                            store.writeSteamVerificationRecord(instanceId, snapshot)
                            store.writeSteamRepairPlanRecord(instanceId, snapshot)
                        }
                    }
                }.onFailure { error ->
                    store.writeDownloadSnapshot(
                        instanceId,
                        if (error is Sts2SteamOperationCanceledException || cancelSignal.get()) {
                            buildSteamDownloadCanceledSnapshot(
                                language = language,
                                version = normalizedVersion,
                                selectedSteamAccount = selectedSteamAccount,
                                steamBranch = normalizedBranch,
                                previous = store.downloadSnapshot(instanceId),
                            )
                        } else {
                            Sts2GameFileDownloadSnapshot(
                                status = Sts2GameFileDownloadStatus.FAILED,
                                versionClientId = normalizedVersion?.clientId,
                                gameDirectory = normalizedVersion?.gameDirectory,
                                steamAccountSubjectId = selectedSteamAccount?.subjectId,
                                steamBranch = normalizedBranch,
                                message = classifySteamFailureMessage(
                                    language = language,
                                    rawMessage = error.message?.takeIf { it.isNotBlank() }
                                    ?: steamLocalized(language, "android.steam.0006"),
                                ),
                                updatedAtMillis = System.currentTimeMillis(),
                            )
                        },
                    )
                }
                notifyUi(onStateChanged)
            } finally {
                downloadCancelSignals.remove(instanceId.value, cancelSignal)
                downloadPauseSignals.remove(instanceId.value, pauseSignal)
            }
        }.start()
    }
}

private data class PreparedSteamManifest(
    val depot: SteamDepotService,
    val manifestPath: String,
    val depotKeyHex: String,
    val requestCode: Long,
    val manifestId: String,
    val manifestFiles: List<SteamManifestFileEntry>,
)

private data class SteamManifestIdentity(
    val depotId: Int,
    val manifestGid: Long,
    val manifestId: String,
)

private data class SteamRepairPlan(
    val checkSnapshot: Sts2GameFileCheckSnapshot,
    val filesToDownload: List<SteamManifestFileEntry>,
    val extraLocalFiles: List<String>,
)

private data class SteamStreamingVerifyResult(
    val verifyResult: SteamDepotLocalVerifyResult,
    val localFileCount: Int,
    val extraLocalFiles: List<String>,
    val manifestFileCount: Int,
)

private data class Sts2SelectiveDownloadOutcome(
    val downloadSnapshot: Sts2GameFileDownloadSnapshot,
    val postDownloadCheckSnapshot: Sts2GameFileCheckSnapshot?,
)

private class Sts2SteamOperationCanceledException(
    message: String,
) : IllegalStateException(message)

private class ThrottledProgressPublisher<T>(
    private val minIntervalMillis: Long,
    private val publishNow: (T) -> Unit,
) {
    private var lastPublishedAtMillis = 0L

    fun publish(snapshot: T) {
        val now = System.currentTimeMillis()
        if (now - lastPublishedAtMillis < minIntervalMillis) {
            return
        }
        lastPublishedAtMillis = now
        publishNow(snapshot)
    }
}

private suspend fun performSelectiveDownload(
    language: SupportedLanguage,
    version: Sts2VersionDefinition?,
    selectedSteamAccount: LauncherAccount?,
    steamBranch: String,
    manageStorageGranted: Boolean,
    hostPaths: ExtensionHostPaths,
    steamDepot: SteamDepotService?,
    cancelSignal: AtomicBoolean,
    pauseSignal: AtomicBoolean,
    onProgress: (Sts2GameFileDownloadSnapshot) -> Unit,
    loadCachedRepairPlan: (String) -> Sts2SteamRepairPlanRecord,
): Sts2SelectiveDownloadOutcome {
    val normalizedVersion = version?.normalizedGameFileVersion()
    require(manageStorageGranted) {
        steamLocalized(language, "android.steam.0007")
    }
    require(normalizedVersion != null) {
        steamLocalized(language, "android.steam.0008")
    }
    require(normalizedVersion.gameDirectory.isNotBlank()) {
        steamLocalized(language, "android.steam.0009")
    }
    val steamId = selectedSteamAccount.requireSteamId(language)
    val steamAccount = requireNotNull(selectedSteamAccount)
    val depot = steamDepot.requireSteamDepotService(language)
        ensureSteamOperationNotCanceled(
            cancelSignal = cancelSignal,
            language = language,
            messageKey = "android.steam.cancel.download",
        )
        onProgress(
            buildSteamDownloadRunningSnapshot(
                version = normalizedVersion,
                selectedSteamAccount = steamAccount,
                steamBranch = steamBranch,
                message = steamLocalized(language, "android.steam.0010"),
            ),
        )
        val latestManifest = fetchLatestSteamManifestIdentity(
            language = language,
            depot = depot,
            steamId = steamId,
            steamBranch = steamBranch,
        )
        val cachedRepairPlanRecord = loadCachedRepairPlan(latestManifest.manifestId)
        val cachedPreparedManifest = cachedRepairPlanRecord.toPreparedSteamManifestOrNull(
            depot = depot,
            latestManifest = latestManifest,
        )
        val preparedManifest = cachedPreparedManifest ?: prepareSteamManifest(
            language = language,
            depot = depot,
            steamId = steamId,
            steamBranch = steamBranch,
            hostPaths = hostPaths,
            latestManifest = latestManifest,
            onStage = { stageMessage ->
                onProgress(
                    buildSteamDownloadRunningSnapshot(
                        version = normalizedVersion,
                        selectedSteamAccount = steamAccount,
                        steamBranch = steamBranch,
                        message = stageMessage,
                    ),
                )
            },
        )
        ensureSteamOperationNotCanceled(
            cancelSignal = cancelSignal,
            language = language,
            messageKey = "android.steam.cancel.download",
        )
        val cachedPlan = cachedPreparedManifest?.let {
            cachedRepairPlanRecord.toSteamRepairPlanOrNull(
                language = language,
                version = normalizedVersion,
                selectedSteamAccount = selectedSteamAccount,
                steamBranch = steamBranch,
                preparedManifest = it,
            )
        }
        val plan = if (cachedPlan != null) {
            onProgress(
                buildSteamDownloadRunningSnapshot(
                    version = normalizedVersion,
                    selectedSteamAccount = steamAccount,
                    steamBranch = steamBranch,
                    message = steamLocalized(language, "android.steam.0011"),
                    totalFileCount = cachedPlan.filesToDownload.size,
                    completedFileCount = 0,
                ),
            )
            cachedPlan
        } else {
            onProgress(
                buildSteamDownloadRunningSnapshot(
                    version = normalizedVersion,
                    selectedSteamAccount = steamAccount,
                    steamBranch = steamBranch,
                    message = steamLocalized(language, "android.steam.0012"),
                ),
            )
            val verifyResult = runSteamStreamingVerify(
                language = language,
                version = normalizedVersion,
                selectedSteamAccount = selectedSteamAccount,
                steamBranch = steamBranch,
                preparedManifest = preparedManifest,
                cancelSignal = cancelSignal,
                onProgress = { checkSnapshot ->
                    onProgress(
                        buildSteamDownloadRunningSnapshot(
                            version = normalizedVersion,
                            selectedSteamAccount = steamAccount,
                            steamBranch = steamBranch,
                            message = checkSnapshot.message,
                            totalFileCount = checkSnapshot.expectedFileCount,
                            completedFileCount = checkSnapshot.checkedFileCount,
                        ),
                    )
                },
            )
            ensureSteamOperationNotCanceled(
                cancelSignal = cancelSignal,
                language = language,
                messageKey = "android.steam.cancel.download",
            )
            buildSteamRepairPlan(
                language = language,
                version = normalizedVersion,
                selectedSteamAccount = selectedSteamAccount,
                steamBranch = steamBranch,
                preparedManifest = preparedManifest,
                verifyResult = verifyResult,
            )
        }
        ensureSteamOperationNotCanceled(
            cancelSignal = cancelSignal,
            language = language,
            messageKey = "android.steam.cancel.download",
        )
        if (cachedPlan == null) {
            onProgress(
                buildSteamDownloadRunningSnapshot(
                    version = normalizedVersion,
                    selectedSteamAccount = steamAccount,
                    steamBranch = steamBranch,
                    message = steamLocalized(language, "android.steam.0013"),
                    totalFileCount = plan.filesToDownload.size,
                    completedFileCount = 0,
                ),
            )
        }
        if (plan.checkSnapshot.passed) {
            return Sts2SelectiveDownloadOutcome(
                downloadSnapshot = Sts2GameFileDownloadSnapshot(
                    status = Sts2GameFileDownloadStatus.COMPLETED,
                    versionClientId = normalizedVersion.clientId,
                    gameDirectory = normalizedVersion.gameDirectory,
                    steamAccountSubjectId = steamAccount.subjectId,
                    steamBranch = steamBranch,
                    message = steamLocalized(language, "android.steam.0014"),
                    totalFileCount = 0,
                    completedFileCount = 0,
                    skippedFileCount = plan.checkSnapshot.expectedFileCount,
                    deletedExtraFileCount = 0,
                    totalBytes = 0L,
                    downloadedBytes = 0L,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
                postDownloadCheckSnapshot = plan.checkSnapshot,
            )
        }

        val gameRoot = File(normalizedVersion.gameDirectory)
        val totalFilesToDownload = plan.filesToDownload.size
        val totalBytesToDownload = plan.filesToDownload.sumOf { entry -> entry.size.coerceAtLeast(0L) }
        val deletedExtraFiles = 0
        val skippedFileCount = (plan.checkSnapshot.expectedFileCount ?: 0) - totalFilesToDownload
        var completedBytes = 0L

        plan.filesToDownload.forEachIndexed { index, entry ->
            val relativePath = manifestRelativePath(entry)
            var pauseRequestedToNative = false
            ensureSteamOperationNotCanceled(
                cancelSignal = cancelSignal,
                language = language,
                messageKey = "android.steam.cancel.download",
            )
            val targetFile = resolveRelativeFile(gameRoot, relativePath)
            targetFile.parentFile?.mkdirs()
            val taskHandle = preparedManifest.depot.startFileDownload(
                inputPath = preparedManifest.manifestPath,
                outputPath = targetFile.absolutePath,
                depotKeyHex = preparedManifest.depotKeyHex,
                filePath = relativePath,
                maxCount = STS2_STEAM_MAX_COUNT,
            )
            try {
                while (true) {
                    if (cancelSignal.get()) {
                        runCatching {
                            preparedManifest.depot.cancelTask(taskHandle)
                        }
                        throw Sts2SteamOperationCanceledException(
                            steamLocalized(language, "android.steam.0015"),
                        )
                    }
                    if (pauseSignal.get() && !pauseRequestedToNative) {
                        runCatching {
                            preparedManifest.depot.pauseTask(taskHandle)
                        }
                        pauseRequestedToNative = true
                    }
                    val taskSnapshot = preparedManifest.depot.pollTask(taskHandle)
                    onProgress(
                        Sts2GameFileDownloadSnapshot(
                            status = Sts2GameFileDownloadStatus.RUNNING,
                            versionClientId = normalizedVersion.clientId,
                            gameDirectory = normalizedVersion.gameDirectory,
                            steamAccountSubjectId = steamAccount.subjectId,
                            steamBranch = steamBranch,
                            message = buildSteamDownloadProgressMessage(
                                language = language,
                                relativePath = relativePath,
                                taskSnapshot = taskSnapshot,
                                completedIndex = index,
                                totalFilesToDownload = totalFilesToDownload,
                            ),
                            currentFilePath = relativePath,
                            totalFileCount = totalFilesToDownload,
                            completedFileCount = index,
                            skippedFileCount = skippedFileCount,
                            deletedExtraFileCount = deletedExtraFiles,
                            currentFileDownloadedBytes = taskSnapshot.completedBytes.coerceAtLeast(0L),
                            currentFileTotalBytes = taskSnapshot.totalBytes.takeIf { it > 0L } ?: entry.size.coerceAtLeast(0L),
                            totalBytes = totalBytesToDownload,
                            downloadedBytes = completedBytes + taskSnapshot.completedBytes.coerceAtLeast(0L),
                            updatedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                    if (taskSnapshot.finished) {
                        when {
                            taskSnapshot.succeeded -> {
                                completedBytes += entry.size.coerceAtLeast(0L)
                                break
                            }

                            taskSnapshot.paused -> {
                                return Sts2SelectiveDownloadOutcome(
                                    downloadSnapshot = buildSteamDownloadPausedSnapshot(
                                        language = language,
                                        version = normalizedVersion,
                                        selectedSteamAccount = steamAccount,
                                        steamBranch = steamBranch,
                                        relativePath = relativePath,
                                        taskSnapshot = taskSnapshot,
                                        totalFilesToDownload = totalFilesToDownload,
                                        completedIndex = index,
                                        skippedFileCount = skippedFileCount,
                                        deletedExtraFiles = deletedExtraFiles,
                                        totalBytesToDownload = totalBytesToDownload,
                                        completedBytes = completedBytes,
                                    ),
                                    postDownloadCheckSnapshot = null,
                                )
                            }

                            taskSnapshot.canceled -> {
                                throw Sts2SteamOperationCanceledException(
                                    steamLocalized(language, "android.steam.0016"),
                                )
                            }

                            else -> {
                                error(
                                    taskSnapshot.message.ifBlank {
                                        steamLocalized(language, "android.steam.0017")
                                    },
                                )
                            }
                        }
                    }
                    delay(STS2_STEAM_DOWNLOAD_POLL_INTERVAL_MILLIS)
                }
            } finally {
                preparedManifest.depot.disposeTask(taskHandle)
            }
        }

        val postCheck = performSteamCheckWithPreparedManifest(
            language = language,
            version = normalizedVersion,
            selectedSteamAccount = selectedSteamAccount,
            steamBranch = steamBranch,
            preparedManifest = preparedManifest,
            cancelSignal = cancelSignal,
        )
        return Sts2SelectiveDownloadOutcome(
            downloadSnapshot = Sts2GameFileDownloadSnapshot(
                status = Sts2GameFileDownloadStatus.COMPLETED,
                versionClientId = normalizedVersion.clientId,
                gameDirectory = normalizedVersion.gameDirectory,
                steamAccountSubjectId = steamAccount.subjectId,
                steamBranch = steamBranch,
                message = if (postCheck.passed) {
                    steamLocalized(language, "android.steam.0018")
                } else {
                    steamLocalized(language, "android.steam.0019")
                },
                currentFilePath = null,
                totalFileCount = totalFilesToDownload,
                completedFileCount = totalFilesToDownload,
                skippedFileCount = skippedFileCount,
                deletedExtraFileCount = deletedExtraFiles,
                currentFileDownloadedBytes = null,
                currentFileTotalBytes = null,
                totalBytes = totalBytesToDownload,
                downloadedBytes = totalBytesToDownload,
                updatedAtMillis = System.currentTimeMillis(),
            ),
            postDownloadCheckSnapshot = postCheck,
        )
    }

private suspend fun performSteamCheck(
    language: SupportedLanguage,
    version: Sts2VersionDefinition?,
    selectedSteamAccount: LauncherAccount?,
    steamBranch: String,
    manageStorageGranted: Boolean,
    hostPaths: ExtensionHostPaths,
    steamDepot: SteamDepotService?,
    cancelSignal: AtomicBoolean,
    onProgress: (Sts2GameFileCheckSnapshot) -> Unit = {},
): Sts2GameFileCheckSnapshot {
    val normalizedVersion = version?.normalizedGameFileVersion()
    require(manageStorageGranted) {
        steamLocalized(language, "android.steam.0020")
    }
    require(normalizedVersion != null) {
        steamLocalized(language, "android.steam.0021")
    }
    require(normalizedVersion.gameDirectory.isNotBlank()) {
        steamLocalized(language, "android.steam.0022")
    }
    val steamId = selectedSteamAccount.requireSteamId(language)
    val depot = steamDepot.requireSteamDepotService(language)
    ensureSteamOperationNotCanceled(
        cancelSignal = cancelSignal,
        language = language,
        messageKey = "android.steam.cancel.verify",
    )
    onProgress(
        buildSteamCheckRunningSnapshot(
            version = normalizedVersion,
            selectedSteamAccount = selectedSteamAccount,
            steamBranch = steamBranch,
            message = steamLocalized(language, "android.steam.0023"),
        ),
    )
    val preparedManifest = prepareSteamManifest(
        language = language,
        depot = depot,
        steamId = steamId,
        steamBranch = steamBranch,
        hostPaths = hostPaths,
        onStage = { stageMessage ->
            onProgress(
                buildSteamCheckRunningSnapshot(
                    version = normalizedVersion,
                    selectedSteamAccount = selectedSteamAccount,
                    steamBranch = steamBranch,
                    message = stageMessage,
                ),
            )
        },
    )
    ensureSteamOperationNotCanceled(
        cancelSignal = cancelSignal,
        language = language,
        messageKey = "android.steam.cancel.verify",
    )
    return performSteamCheckWithPreparedManifest(
        language = language,
        version = normalizedVersion,
        selectedSteamAccount = selectedSteamAccount,
        steamBranch = steamBranch,
        preparedManifest = preparedManifest,
        cancelSignal = cancelSignal,
        onProgress = onProgress,
    )
}

private suspend fun performSteamCheckWithPreparedManifest(
    language: SupportedLanguage,
    version: Sts2VersionDefinition,
    selectedSteamAccount: LauncherAccount?,
    steamBranch: String,
    preparedManifest: PreparedSteamManifest,
    cancelSignal: AtomicBoolean,
    onProgress: (Sts2GameFileCheckSnapshot) -> Unit = {},
): Sts2GameFileCheckSnapshot {
    val verifyResult = runSteamStreamingVerify(
        language = language,
        version = version,
        selectedSteamAccount = selectedSteamAccount,
        steamBranch = steamBranch,
        preparedManifest = preparedManifest,
        cancelSignal = cancelSignal,
        onProgress = onProgress,
    )
    return buildSteamRepairPlan(
        language = language,
        version = version,
        selectedSteamAccount = selectedSteamAccount,
        steamBranch = steamBranch,
        preparedManifest = preparedManifest,
        verifyResult = verifyResult,
    ).checkSnapshot
}

private fun buildSteamRepairPlan(
    language: SupportedLanguage,
    version: Sts2VersionDefinition,
    selectedSteamAccount: LauncherAccount?,
    steamBranch: String,
    preparedManifest: PreparedSteamManifest,
    verifyResult: SteamStreamingVerifyResult,
): SteamRepairPlan {
    val detailEntries = verifyResult.verifyResult.entries.toList()
    val manifestEntryByPath = preparedManifest.manifestFiles.associateBy(::manifestRelativePath)
    val filesToDownload = mutableListOf<SteamManifestFileEntry>()
    val problemFiles = mutableListOf<String>()

    detailEntries.forEach { entry ->
        val relativePath = verifyEntryRelativePath(entry) ?: return@forEach
        when (entry.statusCode) {
            1 -> {
                problemFiles += steamLocalized(language, "android.steam.0024", listOf(relativePath))
                manifestEntryByPath[relativePath]?.let(filesToDownload::add)
            }

            2 -> {
                problemFiles += steamLocalized(language, "android.steam.0025", listOf(relativePath))
                manifestEntryByPath[relativePath]?.let(filesToDownload::add)
            }
        }
    }

    if (
        (verifyResult.verifyResult.missingCount > 0L || verifyResult.verifyResult.mismatchedCount > 0L) &&
        filesToDownload.isEmpty()
    ) {
        error(
            steamLocalized(language, "android.steam.0026"),
        )
    }

    val distinctFilesToDownload = filesToDownload.distinctBy(::manifestRelativePath)
    return SteamRepairPlan(
        checkSnapshot = buildSteamCheckSnapshotFromVerifyResult(
            language = language,
            version = version,
            selectedSteamAccount = selectedSteamAccount,
            steamBranch = steamBranch,
            verifyResult = verifyResult.verifyResult,
            localFileCount = verifyResult.localFileCount,
            extraLocalFiles = verifyResult.extraLocalFiles,
            manifestFileCount = verifyResult.manifestFileCount,
            problemFilesPreview = buildProblemFilesPreview(
                language = language,
                problemFiles = problemFiles,
            ),
        ).copy(
            steamManifestId = preparedManifest.manifestId,
            steamManifestPath = preparedManifest.manifestPath,
            steamDepotKeyHex = preparedManifest.depotKeyHex,
            repairFilePaths = distinctFilesToDownload.map(::manifestRelativePath),
        ),
        filesToDownload = distinctFilesToDownload,
        extraLocalFiles = verifyResult.extraLocalFiles,
    )
}

private suspend fun Sts2SteamRepairPlanRecord.toPreparedSteamManifestOrNull(
    depot: SteamDepotService,
    latestManifest: SteamManifestIdentity,
): PreparedSteamManifest? {
    val manifestPath = steamManifestPath?.takeIf { it.isNotBlank() } ?: return null
    val depotKeyHex = steamDepotKeyHex?.takeIf { it.isNotBlank() } ?: return null
    if (steamManifestId != latestManifest.manifestId || !File(manifestPath).isFile) {
        return null
    }
    val manifestFiles = runCatching {
        depot.listManifestFiles(
            inputPath = manifestPath,
            depotKeyHex = depotKeyHex,
            filterText = "",
            limit = STS2_STEAM_MANIFEST_FILE_LIMIT,
        )
    }.getOrNull() ?: return null
    if (!manifestFiles.present || manifestFiles.printedCount < manifestFiles.totalCount) {
        return null
    }
    return PreparedSteamManifest(
        depot = depot,
        manifestPath = manifestPath,
        depotKeyHex = depotKeyHex,
        requestCode = 0L,
        manifestId = latestManifest.manifestId,
        manifestFiles = manifestFiles.files.toList(),
    )
}

private fun Sts2SteamRepairPlanRecord.toSteamRepairPlanOrNull(
    language: SupportedLanguage,
    version: Sts2VersionDefinition,
    selectedSteamAccount: LauncherAccount?,
    steamBranch: String,
    preparedManifest: PreparedSteamManifest,
): SteamRepairPlan? {
    if (!available || !matches(version, steamBranch, preparedManifest.manifestId)) {
        return null
    }
    val manifestEntryByPath = preparedManifest.manifestFiles.associateBy(::manifestRelativePath)
    val normalizedRepairPaths = repairFilePaths
        .map(::normalizeManifestRelativePath)
        .distinct()
    val filesToDownload = normalizedRepairPaths.map { relativePath ->
        manifestEntryByPath[relativePath] ?: return null
    }
    val passed = normalizedRepairPaths.isEmpty()
    val message = if (passed) {
        steamLocalized(language, "android.steam.0027")
    } else {
        steamLocalized(language, "android.steam.0028")
    }
    return SteamRepairPlan(
        checkSnapshot = Sts2GameFileCheckSnapshot(
            status = Sts2GameFileCheckStatus.COMPLETED,
            mode = Sts2GameFileCheckMode.STEAM,
            versionClientId = version.clientId,
            gameDirectory = version.gameDirectory,
            steamAccountSubjectId = selectedSteamAccount?.subjectId,
            steamBranch = steamBranch,
            passed = passed,
            message = message,
            targetPath = version.gameDirectory,
            expectedFileCount = expectedFileCount ?: preparedManifest.manifestFiles.size,
            localFileCount = localFileCount,
            checkedFileCount = expectedFileCount ?: preparedManifest.manifestFiles.size,
            missingFileCount = missingFileCount,
            mismatchedFileCount = mismatchedFileCount ?: normalizedRepairPaths.size.takeIf { it > 0 },
            problemFilesPreview = buildProblemFilesPreview(
                language = language,
                problemFiles = normalizedRepairPaths.map { relativePath ->
                    steamLocalized(language, "android.steam.0029", listOf(relativePath))
                },
            ),
            steamManifestId = preparedManifest.manifestId,
            steamManifestPath = preparedManifest.manifestPath,
            steamDepotKeyHex = preparedManifest.depotKeyHex,
            repairFilePaths = normalizedRepairPaths,
            checkedAtMillis = checkedAtMillis,
        ),
        filesToDownload = filesToDownload,
        extraLocalFiles = emptyList(),
    )
}

private suspend fun runSteamStreamingVerify(
    language: SupportedLanguage,
    version: Sts2VersionDefinition,
    selectedSteamAccount: LauncherAccount?,
    steamBranch: String,
    preparedManifest: PreparedSteamManifest,
    cancelSignal: AtomicBoolean,
    onProgress: (Sts2GameFileCheckSnapshot) -> Unit = {},
): SteamStreamingVerifyResult {
    val gameRoot = File(version.gameDirectory)
    onProgress(
        buildSteamCheckRunningSnapshot(
            version = version,
            selectedSteamAccount = selectedSteamAccount,
            steamBranch = steamBranch,
            message = steamLocalized(language, "android.steam.0030"),
        ),
    )
    val localFiles = listRelativeFiles(gameRoot)
    val manifestPaths = preparedManifest.manifestFiles
        .map(::manifestRelativePath)
        .toHashSet()
    val extraLocalFiles = localFiles.filter { relativePath -> relativePath !in manifestPaths }
    onProgress(
        buildSteamCheckRunningSnapshot(
            version = version,
            selectedSteamAccount = selectedSteamAccount,
            steamBranch = steamBranch,
            message = steamLocalized(language, "android.steam.0031"),
        ),
    )
    val taskHandle = preparedManifest.depot.startVerifyLocalFiles(
        inputPath = preparedManifest.manifestPath,
        localRoot = gameRoot.absolutePath,
        depotKeyHex = preparedManifest.depotKeyHex,
        filterText = null,
    )
    try {
        while (true) {
            if (cancelSignal.get()) {
                runCatching {
                    preparedManifest.depot.cancelTask(taskHandle)
                }
                throw Sts2SteamOperationCanceledException(
                    steamLocalized(language, "android.steam.0032"),
                )
            }
            val taskSnapshot = preparedManifest.depot.pollTask(taskHandle)
            val verifyProgress = taskSnapshot.verifyResult
            val checkedFileCount = verifyProgress?.checkedCount?.toSafeInt()
                ?: taskSnapshot.completedSteps.toSafeInt().takeIf { it > 0 }
                ?: 0
            val expectedFileCount = verifyProgress?.totalCount?.toSafeInt()
                ?: taskSnapshot.totalSteps.toSafeInt().takeIf { it > 0 }
                ?: preparedManifest.manifestFiles.size
            onProgress(
                Sts2GameFileCheckSnapshot(
                    status = Sts2GameFileCheckStatus.RUNNING,
                    mode = Sts2GameFileCheckMode.STEAM,
                    versionClientId = version.clientId,
                    gameDirectory = version.gameDirectory,
                    steamAccountSubjectId = selectedSteamAccount?.subjectId,
                    steamBranch = steamBranch,
                    passed = false,
                    message = buildSteamCheckTaskMessage(
                        language = language,
                        checkedFileCount = checkedFileCount,
                        expectedFileCount = expectedFileCount,
                        taskSnapshot = taskSnapshot,
                    ),
                    targetPath = version.gameDirectory,
                    currentFilePath = null,
                    expectedFileCount = expectedFileCount,
                    localFileCount = localFiles.size,
                    checkedFileCount = checkedFileCount,
                    okFileCount = verifyProgress?.okCount?.toSafeInt(),
                    missingFileCount = verifyProgress?.missingCount?.toSafeInt(),
                    mismatchedFileCount = verifyProgress?.mismatchedCount?.toSafeInt(),
                    sizeOnlyFileCount = verifyProgress?.sizeOnlyCount?.toSafeInt(),
                    extraFileCount = extraLocalFiles.size,
                    checkedAtMillis = System.currentTimeMillis(),
                ),
            )
            if (taskSnapshot.finished) {
                if (taskSnapshot.canceled) {
                    throw Sts2SteamOperationCanceledException(
                        steamLocalized(language, "android.steam.0033"),
                    )
                }
                val verifyResult = taskSnapshot.verifyResult
                    ?: error(
                        taskSnapshot.message.ifBlank {
                            steamLocalized(language, "android.steam.0034")
                        },
                    )
                if (!taskSnapshot.succeeded && verifyResult.moduleStatus.isBlank()) {
                    error(
                        taskSnapshot.message.ifBlank {
                            steamLocalized(language, "android.steam.0035")
                        },
                    )
                }
                return SteamStreamingVerifyResult(
                    verifyResult = verifyResult,
                    localFileCount = localFiles.size,
                    extraLocalFiles = extraLocalFiles,
                    manifestFileCount = preparedManifest.manifestFiles.size,
                )
            }
            delay(STS2_STEAM_DOWNLOAD_POLL_INTERVAL_MILLIS)
        }
    } finally {
        runCatching {
            preparedManifest.depot.disposeTask(taskHandle)
        }
    }
}

private fun buildProblemFilesPreview(
    language: SupportedLanguage,
    problemFiles: List<String>,
): List<String> {
    val previewLimit = 8
    if (problemFiles.isEmpty()) {
        return emptyList()
    }
    val preview = problemFiles.take(previewLimit).toMutableList()
    val remaining = problemFiles.size - preview.size
    if (remaining > 0) {
        preview += steamLocalized(language, "android.steam.0036", listOf(remaining))
    }
    return preview
}

private fun buildSteamCheckTaskMessage(
    language: SupportedLanguage,
    checkedFileCount: Int,
    expectedFileCount: Int,
    taskSnapshot: SteamDepotTaskSnapshot,
): String {
    val detail = buildSteamTaskDetail(
        language = language,
        phase = taskSnapshot.phase,
        progressSummary = taskSnapshot.progressSummary,
        moduleStatus = taskSnapshot.moduleStatus.ifBlank {
            taskSnapshot.verifyResult?.moduleStatus.orEmpty()
        },
        fallbackMessage = steamLocalized(
            language,
            "android.steam.task.processing_file",
            listOf(checkedFileCount, expectedFileCount),
        ),
        rawFallback = taskSnapshot.message,
    )
    return steamLocalized(language, "android.steam.0037", listOf(detail))
}

private fun ensureSteamOperationNotCanceled(
    cancelSignal: AtomicBoolean,
    language: SupportedLanguage,
    messageKey: String,
) {
    if (cancelSignal.get()) {
        throw Sts2SteamOperationCanceledException(
            steamLocalized(language, messageKey),
        )
    }
}

private fun buildSteamDownloadProgressMessage(
    language: SupportedLanguage,
    relativePath: String,
    taskSnapshot: SteamDepotTaskSnapshot,
    completedIndex: Int,
    totalFilesToDownload: Int,
): String {
    val detail = buildSteamTaskDetail(
        language = language,
        phase = taskSnapshot.phase,
        progressSummary = taskSnapshot.progressSummary,
        moduleStatus = taskSnapshot.moduleStatus,
        fallbackMessage = steamLocalized(
            language,
            "android.steam.task.downloading_file",
            listOf(completedIndex + 1, totalFilesToDownload),
        ),
        rawFallback = taskSnapshot.message,
    )
    return steamLocalized(language, "android.steam.0038", listOf(relativePath, detail))
}

private suspend fun prepareSteamManifest(
    language: SupportedLanguage,
    depot: SteamDepotService,
    steamId: Long,
    steamBranch: String,
    hostPaths: ExtensionHostPaths,
    latestManifest: SteamManifestIdentity? = null,
    onStage: (String) -> Unit = {},
): PreparedSteamManifest {
    val manifestIdentity = latestManifest ?: fetchLatestSteamManifestIdentity(
        language = language,
        depot = depot,
        steamId = steamId,
        steamBranch = steamBranch,
        onStage = onStage,
    )
    onStage(
        steamLocalized(language, "android.steam.0039"),
    )
    val depotKey = depot.fetchDepotKey(
        steamId = steamId,
        appId = STS2_STEAM_APP_ID,
        depotId = manifestIdentity.depotId,
        maxCount = STS2_STEAM_MAX_COUNT,
    )
    require(depotKey.present && depotKey.keyHex.isNotBlank()) {
        steamDepotKeyFailureMessage(language, depotKey.eresult)
    }
    onStage(
        steamLocalized(language, "android.steam.0040"),
    )
    val requestCode = depot.fetchManifestRequestCode(
        steamId = steamId,
        appId = STS2_STEAM_APP_ID,
        depotId = manifestIdentity.depotId,
        manifestGid = manifestIdentity.manifestGid,
        branch = steamBranch,
        branchPasswordHash = "",
        maxCount = STS2_STEAM_MAX_COUNT,
    )
    require(requestCode.present && requestCode.requestCode != 0L) {
        steamLocalized(language, "android.steam.0041")
    }
    val manifestCacheDirectory = resolveManifestCacheDirectory(hostPaths)
    manifestCacheDirectory.mkdirs()
    val manifestFile = manifestCacheDirectory.resolve(
        "$STS2_STEAM_APP_ID-$steamBranch-${manifestIdentity.depotId}-${manifestIdentity.manifestGid}.bin",
    )
    if (!manifestFile.isFile) {
        onStage(
            steamLocalized(language, "android.steam.0042"),
        )
        depot.downloadManifest(
            manifestIdentity.depotId,
            manifestIdentity.manifestGid,
            requestCode.requestCode,
            manifestFile.absolutePath,
            STS2_STEAM_MAX_COUNT,
        )
    }
    onStage(
        steamLocalized(language, "android.steam.0043"),
    )
    val manifestFiles = depot.listManifestFiles(
        manifestFile.absolutePath,
        depotKey.keyHex,
        "",
        STS2_STEAM_MANIFEST_FILE_LIMIT,
    )
    require(manifestFiles.present) {
        steamLocalized(language, "android.steam.0044")
    }
    require(manifestFiles.printedCount >= manifestFiles.totalCount) {
        steamLocalized(language, "android.steam.0045")
    }
    return PreparedSteamManifest(
        depot = depot,
        manifestPath = manifestFile.absolutePath,
        depotKeyHex = depotKey.keyHex,
        requestCode = requestCode.requestCode,
        manifestId = manifestIdentity.manifestId,
        manifestFiles = manifestFiles.files.toList(),
    )
}

private suspend fun fetchLatestSteamManifestIdentity(
    language: SupportedLanguage,
    depot: SteamDepotService,
    steamId: Long,
    steamBranch: String,
    onStage: (String) -> Unit = {},
): SteamManifestIdentity {
    onStage(
        steamLocalized(language, "android.steam.0046"),
    )
    val preflight = depot.fetchPreflight(
        steamId = steamId,
        appId = STS2_STEAM_APP_ID,
        branch = steamBranch,
        maxCount = STS2_STEAM_MAX_COUNT,
    )
    require(preflight.present) {
        steamLocalized(language, "android.steam.0047")
    }
    val depotEntry = preferredWindowsDepot(preflight.depots.toList())
        ?: error(
            steamLocalized(language, "android.steam.0048"),
        )
    return SteamManifestIdentity(
        depotId = depotEntry.depotId,
        manifestGid = depotEntry.manifestGid,
        manifestId = "$STS2_STEAM_APP_ID:${normalizeSteamBranch(steamBranch)}:${depotEntry.depotId}:${depotEntry.manifestGid}",
    )
}

private fun buildSteamCheckSnapshotFromVerifyResult(
    language: SupportedLanguage,
    version: Sts2VersionDefinition,
    selectedSteamAccount: LauncherAccount?,
    steamBranch: String,
    verifyResult: SteamDepotLocalVerifyResult,
    localFileCount: Int,
    extraLocalFiles: List<String>,
    manifestFileCount: Int,
    problemFilesPreview: List<String>,
): Sts2GameFileCheckSnapshot {
    val passed = verifyResult.clean && verifyResult.missingCount == 0L && verifyResult.mismatchedCount == 0L
    return Sts2GameFileCheckSnapshot(
        status = Sts2GameFileCheckStatus.COMPLETED,
        mode = Sts2GameFileCheckMode.STEAM,
        versionClientId = version.clientId,
        gameDirectory = version.gameDirectory,
        steamAccountSubjectId = selectedSteamAccount?.subjectId,
        steamBranch = steamBranch,
        passed = passed,
        message = if (passed) {
            if (extraLocalFiles.isNotEmpty()) {
                steamLocalized(language, "android.steam.0049")
            } else {
                steamLocalized(language, "android.steam.0050")
            }
        } else {
            steamLocalized(language, "android.steam.0051")
        },
        targetPath = version.gameDirectory,
        currentFilePath = null,
        expectedFileCount = manifestFileCount,
        localFileCount = localFileCount,
        checkedFileCount = verifyResult.checkedCount.toSafeInt(),
        okFileCount = verifyResult.okCount.toSafeInt(),
        missingFileCount = verifyResult.missingCount.toSafeInt(),
        mismatchedFileCount = verifyResult.mismatchedCount.toSafeInt(),
        sizeOnlyFileCount = verifyResult.sizeOnlyCount.toSafeInt(),
        extraFileCount = extraLocalFiles.size,
        problemFilesPreview = problemFilesPreview,
        checkedAtMillis = System.currentTimeMillis(),
    )
}

private fun verifyEntryRelativePath(entry: SteamDepotLocalVerifyEntry): String? {
    return entry.manifestFilename
        .takeIf { it.isNotBlank() }
        ?.let(::normalizeManifestRelativePath)
}

private fun buildSteamCheckFailureSnapshot(
    language: SupportedLanguage,
    version: Sts2VersionDefinition?,
    selectedSteamAccount: LauncherAccount?,
    steamBranch: String,
    message: String,
): Sts2GameFileCheckSnapshot {
    return Sts2GameFileCheckSnapshot(
        status = Sts2GameFileCheckStatus.FAILED,
        mode = Sts2GameFileCheckMode.STEAM,
        versionClientId = version?.clientId,
        gameDirectory = version?.gameDirectory?.trim(),
        steamAccountSubjectId = selectedSteamAccount?.subjectId,
        steamBranch = steamBranch,
        passed = false,
        message = message,
        targetPath = version?.gameDirectory?.trim(),
        checkedAtMillis = System.currentTimeMillis(),
    )
}

private fun buildSteamCheckRunningSnapshot(
    version: Sts2VersionDefinition?,
    selectedSteamAccount: LauncherAccount?,
    steamBranch: String,
    message: String,
    expectedFileCount: Int? = null,
    checkedFileCount: Int? = null,
): Sts2GameFileCheckSnapshot {
    return Sts2GameFileCheckSnapshot(
        status = Sts2GameFileCheckStatus.RUNNING,
        mode = Sts2GameFileCheckMode.STEAM,
        versionClientId = version?.clientId,
        gameDirectory = version?.gameDirectory?.trim(),
        steamAccountSubjectId = selectedSteamAccount?.subjectId,
        steamBranch = steamBranch,
        passed = false,
        message = message,
        targetPath = version?.gameDirectory?.trim(),
        expectedFileCount = expectedFileCount,
        checkedFileCount = checkedFileCount,
        checkedAtMillis = System.currentTimeMillis(),
    )
}

private fun buildSteamDownloadRunningSnapshot(
    version: Sts2VersionDefinition?,
    selectedSteamAccount: LauncherAccount?,
    steamBranch: String,
    message: String,
    totalFileCount: Int? = null,
    completedFileCount: Int? = null,
): Sts2GameFileDownloadSnapshot {
    return Sts2GameFileDownloadSnapshot(
        status = Sts2GameFileDownloadStatus.RUNNING,
        versionClientId = version?.clientId,
        gameDirectory = version?.gameDirectory?.trim(),
        steamAccountSubjectId = selectedSteamAccount?.subjectId,
        steamBranch = steamBranch,
        message = message,
        totalFileCount = totalFileCount,
        completedFileCount = completedFileCount,
        updatedAtMillis = System.currentTimeMillis(),
    )
}

private fun buildSteamDownloadPausedSnapshot(
    language: SupportedLanguage,
    version: Sts2VersionDefinition?,
    selectedSteamAccount: LauncherAccount?,
    steamBranch: String,
    relativePath: String,
    taskSnapshot: SteamDepotTaskSnapshot,
    totalFilesToDownload: Int,
    completedIndex: Int,
    skippedFileCount: Int,
    deletedExtraFiles: Int,
    totalBytesToDownload: Long,
    completedBytes: Long,
): Sts2GameFileDownloadSnapshot {
    val currentFileDownloadedBytes = taskSnapshot.completedBytes.coerceAtLeast(0L)
    val currentFileTotalBytes = taskSnapshot.totalBytes.takeIf { it > 0L }
    return Sts2GameFileDownloadSnapshot(
        status = Sts2GameFileDownloadStatus.PAUSED,
        versionClientId = version?.clientId,
        gameDirectory = version?.gameDirectory?.trim(),
        steamAccountSubjectId = selectedSteamAccount?.subjectId,
        steamBranch = steamBranch,
        message = steamLocalized(language, "android.steam.0052"),
        currentFilePath = relativePath,
        totalFileCount = totalFilesToDownload,
        completedFileCount = completedIndex,
        skippedFileCount = skippedFileCount,
        deletedExtraFileCount = deletedExtraFiles,
        currentFileDownloadedBytes = currentFileDownloadedBytes,
        currentFileTotalBytes = currentFileTotalBytes,
        totalBytes = totalBytesToDownload,
        downloadedBytes = completedBytes + currentFileDownloadedBytes,
        updatedAtMillis = System.currentTimeMillis(),
    )
}

private fun buildSteamTaskDetail(
    language: SupportedLanguage,
    phase: String,
    progressSummary: String,
    moduleStatus: String,
    fallbackMessage: String,
    rawFallback: String = "",
): String {
    val normalizedPhase = phase.trim()
    val normalizedSummary = progressSummary.trim()
    val localizedStatus = localizedDepotModuleStatus(language, moduleStatus)
    val primary = sequenceOf(
        normalizedPhase.takeIf { it.isNotBlank() },
        normalizedSummary.takeIf { it.isNotBlank() },
        localizedStatus,
        rawFallback.trim().takeIf { it.isNotBlank() },
        fallbackMessage,
    ).first { !it.isNullOrBlank() } ?: fallbackMessage
    val extras = mutableListOf<String>()
    normalizedSummary.takeIf {
        it.isNotBlank() && !it.equals(primary, ignoreCase = true)
    }?.let(extras::add)
    localizedStatus?.takeIf {
        !it.equals(primary, ignoreCase = true) &&
            extras.none { existing -> existing.equals(it, ignoreCase = true) }
    }?.let(extras::add)
    return if (extras.isEmpty()) {
        primary
    } else {
        steamLocalized(language, "android.steam.0053", listOf(primary, extras.joinToString(" / ")))
    }
}

private fun localizedDepotModuleStatus(
    language: SupportedLanguage,
    moduleStatus: String,
): String? {
    return when (moduleStatus.trim().lowercase()) {
        "" -> null
        "queued" -> steamLocalized(language, "android.steam.0054")
        "reading" -> steamLocalized(language, "android.steam.0055")
        "polling" -> steamLocalized(language, "android.steam.0056")
        "pausing" -> steamLocalized(language, "android.steam.0057")
        "paused" -> steamLocalized(language, "android.steam.0058")
        "canceling" -> steamLocalized(language, "android.steam.0059")
        "succeeded" -> steamLocalized(language, "android.steam.0060")
        "failed" -> steamLocalized(language, "android.steam.0061")
        "canceled" -> steamLocalized(language, "android.steam.0062")
        "idle" -> steamLocalized(language, "android.steam.0063")
        else -> moduleStatus.trim()
    }
}

private fun buildSteamCheckCanceledSnapshot(
    language: SupportedLanguage,
    version: Sts2VersionDefinition?,
    selectedSteamAccount: LauncherAccount?,
    steamBranch: String,
    previous: Sts2GameFileCheckSnapshot,
): Sts2GameFileCheckSnapshot {
    return previous.copy(
        status = Sts2GameFileCheckStatus.CANCELED,
        mode = Sts2GameFileCheckMode.STEAM,
        versionClientId = version?.clientId,
        gameDirectory = version?.gameDirectory?.trim(),
        steamAccountSubjectId = selectedSteamAccount?.subjectId,
        steamBranch = steamBranch,
        passed = false,
        message = steamLocalized(language, "android.steam.0064"),
        targetPath = version?.gameDirectory?.trim(),
        checkedAtMillis = System.currentTimeMillis(),
    )
}

private fun buildSteamDownloadCanceledSnapshot(
    language: SupportedLanguage,
    version: Sts2VersionDefinition?,
    selectedSteamAccount: LauncherAccount?,
    steamBranch: String,
    previous: Sts2GameFileDownloadSnapshot,
): Sts2GameFileDownloadSnapshot {
    return previous.copy(
        status = Sts2GameFileDownloadStatus.CANCELED,
        versionClientId = version?.clientId,
        gameDirectory = version?.gameDirectory?.trim(),
        steamAccountSubjectId = selectedSteamAccount?.subjectId,
        steamBranch = steamBranch,
        message = steamLocalized(language, "android.steam.0065"),
        updatedAtMillis = System.currentTimeMillis(),
    )
}

private fun steamDepotKeyFailureMessage(
    language: SupportedLanguage,
    eresult: Int,
): String {
    return when (eresult) {
        9 -> steamLocalized(language, "android.steam.0066")

        15 -> steamLocalized(language, "android.steam.0067")

        8 -> steamLocalized(language, "android.steam.0068")

        else -> steamLocalized(language, "android.steam.0069")
    }
}

private fun classifySteamFailureMessage(
    language: SupportedLanguage,
    rawMessage: String,
): String {
    val normalized = rawMessage.lowercase()
    return when {
        "eresult=9" in normalized ->
            steamLocalized(language, "android.steam.0070")

        "eresult=15" in normalized ->
            steamLocalized(language, "android.steam.0071")

        "eresult=8" in normalized ->
            steamLocalized(language, "android.steam.0072")

        "timeout" in normalized || "timed out" in normalized || "connection reset" in normalized ||
            "unable to resolve host" in normalized || "unknownhost" in normalized || "network" in normalized ->
            steamLocalized(language, "android.steam.0073")

        "not a steam account" in normalized ->
            steamLocalized(language, "android.steam.0074")

        "cm" in normalized && ("login" in normalized || "mode" in normalized || "session" in normalized) ->
            steamLocalized(language, "android.steam.0075")

        else -> rawMessage
    }
}

private fun preferredWindowsDepot(entries: List<SteamDepotPreflightEntry>): SteamDepotPreflightEntry? {
    return entries
        .filter { entry -> entry.manifestGid != 0L }
        .maxWithOrNull(
            compareBy<SteamDepotPreflightEntry>(
                { depotPlatformScore(it.platformLabel) },
                { if (it.keyAvailable) 1 else 0 },
                { it.size },
            ),
        )
}

private fun depotPlatformScore(platformLabel: String?): Int {
    val label = platformLabel.orEmpty().lowercase()
    return when {
        "windows/x64" in label -> 4
        "windows" in label -> 3
        "shared" in label -> 2
        else -> 1
    }
}

private fun listRelativeFiles(root: File): List<String> {
    if (!root.isDirectory) {
        return emptyList()
    }
    return root.walkTopDown()
        .filter { file -> file.isFile }
        .map { file -> file.relativeTo(root).invariantSeparatorsPath }
        .toList()
}

private fun resolveManifestCacheDirectory(hostPaths: ExtensionHostPaths): File {
    val root = hostPaths.appFilesDirectoryPath?.takeIf { path -> path.isNotBlank() }
        ?: hostPaths.launcherStorageDirectoryPath?.takeIf { path -> path.isNotBlank() }
        ?: error("Launcher storage path is unavailable for Steam manifest caching.")
    return File(root).resolve(STS2_STEAM_MANIFEST_CACHE_DIRECTORY)
}

private fun resolveRelativeFile(
    root: File,
    relativePath: String,
): File {
    return normalizeManifestRelativePath(relativePath)
        .split('/')
        .filter { segment -> segment.isNotBlank() }
        .fold(root) { current, segment -> File(current, segment) }
}

private fun manifestRelativePath(entry: SteamManifestFileEntry): String {
    return normalizeManifestRelativePath(entry.filename)
}

private fun normalizeManifestRelativePath(relativePath: String): String {
    val segments = relativePath
        .split('/', '\\')
        .filter { segment -> segment.isNotBlank() && segment != "." }
    if (segments.isEmpty()) {
        return relativePath.replace('\\', '/')
    }
    return segments.joinToString("/")
}

private fun LauncherAccount?.requireSteamId(language: SupportedLanguage): Long {
    require(this != null) { steamLocalized(language, "android.steam.0076") }
    require(provider == LauncherAccountProvider.STEAM) {
        steamLocalized(language, "android.steam.0077")
    }
    require(SteamAccountLoginMode.CM in loginModes) {
        steamLocalized(language, "android.steam.0078")
    }
    return subjectId.toLongOrNull()
        ?: error(steamLocalized(language, "android.steam.0079"))
}

private fun SteamDepotService?.requireSteamDepotService(language: SupportedLanguage): SteamDepotService {
    return this ?: error(steamLocalized(language, "android.steam.0080"))
}

private fun notifyUi(onStateChanged: () -> Unit) {
    sts2SteamDepotMainHandler.post(onStateChanged)
}

private fun Long.toSafeInt(): Int {
    return coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}
