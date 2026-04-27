package com.dreamyloong.template.sts2.windows

import com.dreamyloong.template.sts2.PersistentSts2GameFileStateStore
import com.dreamyloong.template.sts2.STS2_TEMPLATE_ID
import com.dreamyloong.template.sts2.STS2_STEAM_BRANCH_PUBLIC
import com.dreamyloong.template.sts2.STS2_STEAM_BRANCH_PUBLIC_BETA
import com.dreamyloong.template.sts2.Sts2GameFileCheckMode
import com.dreamyloong.template.sts2.Sts2GameFileCheckSnapshot
import com.dreamyloong.template.sts2.Sts2GameFileCheckStatus
import com.dreamyloong.template.sts2.Sts2GameFileDownloadSnapshot
import com.dreamyloong.template.sts2.Sts2GameFileDownloadStatus
import com.dreamyloong.template.sts2.Sts2GameFilePreferences
import com.dreamyloong.template.sts2.Sts2SteamRepairPlanRecord
import com.dreamyloong.template.sts2.Sts2SteamVerificationRecord
import com.dreamyloong.template.sts2.Sts2SteamVerificationStatus
import com.dreamyloong.template.sts2.Sts2VersionDefinition
import com.dreamyloong.template.sts2.normalizeSteamBranch
import com.dreamyloong.template.sts2.normalizedGameFileVersion
import com.dreamyloong.template.sts2.sts2Localized as steamLocalized
import com.dreamyloong.template.sts2.sts2Localized as windowsLocalized
import com.dreamyloong.tlauncher.sdk.account.LauncherAccount
import com.dreamyloong.tlauncher.sdk.account.LauncherAccountProvider
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
import com.dreamyloong.tlauncher.sdk.page.PageActionRegistration
import com.dreamyloong.tlauncher.sdk.page.PageActionStyle
import com.dreamyloong.tlauncher.sdk.page.PageChoiceOptionRegistration
import com.dreamyloong.tlauncher.sdk.page.PageContext
import com.dreamyloong.tlauncher.sdk.page.PageIds
import com.dreamyloong.tlauncher.sdk.page.PageProgressRegistration
import com.dreamyloong.tlauncher.sdk.page.PageTextDirect
import com.dreamyloong.tlauncher.sdk.page.PageValueItemRegistration
import com.dreamyloong.tlauncher.sdk.page.PageWidgetChoiceCard
import com.dreamyloong.tlauncher.sdk.page.PageWidgetDetailCard
import com.dreamyloong.tlauncher.sdk.page.PageWidgetProgressCard
import com.dreamyloong.tlauncher.sdk.page.PageWidgetRegistration
import com.dreamyloong.tlauncher.sdk.page.PageWidgetToggleCard
import com.dreamyloong.tlauncher.sdk.page.PageWidgetTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.awt.EventQueue
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val STS2_STEAM_APP_ID = 2868840
private const val STS2_STEAM_MAX_COUNT = 20
private const val STS2_STEAM_MANIFEST_FILE_LIMIT = 100_000
private const val STS2_STEAM_MANIFEST_CACHE_DIRECTORY = "sts2/steam/manifests"
private const val STS2_STEAM_DOWNLOAD_POLL_INTERVAL_MILLIS = 150L
private const val STS2_STEAM_UI_REFRESH_INTERVAL_MILLIS = 400L
private const val STS2_STEAM_CHECK_PROGRESS_STEP = 64

internal class Sts2WindowsSteamDepotGameFileCoordinator(
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
        store.clearDownloadSnapshot(instanceId)
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
                    message = steamLocalized(language, "windows.steam.0001"),
                    checkedAtMillis = System.currentTimeMillis(),
                ),
            )
            notifyWindowsUi(onStateChanged)
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
                    message = steamLocalized(language, "windows.steam.0002"),
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
            notifyWindowsUi(onStateChanged)
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
                    message = steamLocalized(language, "windows.steam.0003"),
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
            notifyWindowsUi(onStateChanged)
        }
    }

    fun startSteamCheck(
        instanceId: GameInstanceId,
        language: SupportedLanguage,
        version: Sts2VersionDefinition?,
        selectedSteamAccount: LauncherAccount?,
        steamBranch: String,
        onStateChanged: () -> Unit,
    ) {
        val normalizedVersion = version?.normalizedGameFileVersion()
        val normalizedBranch = normalizeSteamBranch(steamBranch)
        val cancelSignal = AtomicBoolean(false)
        val progressPublisher = ThrottledProgressPublisher<Sts2GameFileCheckSnapshot>(
            minIntervalMillis = STS2_STEAM_UI_REFRESH_INTERVAL_MILLIS,
        ) { snapshot ->
            store.writeCheckSnapshot(instanceId, snapshot)
            notifyWindowsUi(onStateChanged)
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
                message = steamLocalized(language, "windows.steam.0004"),
                checkedAtMillis = System.currentTimeMillis(),
            ),
        )
        notifyWindowsUi(onStateChanged)
        Thread(
            {
                try {
                    val result = runCatching {
                        runBlocking(Dispatchers.IO) {
                            performSteamCheck(
                                language = language,
                                version = normalizedVersion,
                                selectedSteamAccount = selectedSteamAccount,
                                steamBranch = normalizedBranch,
                                hostPaths = hostPaths,
                                steamDepot = steamDepot,
                                cancelSignal = cancelSignal,
                                onProgress = { snapshot -> progressPublisher.publish(snapshot) },
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
                                            ?: steamLocalized(language, "windows.steam.0005"),
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
                    notifyWindowsUi(onStateChanged)
                } finally {
                    checkCancelSignals.remove(instanceId.value, cancelSignal)
                }
            },
            "sts2-windows-steam-check-${instanceId.value}",
        ).start()
    }

    fun startSelectiveDownload(
        instanceId: GameInstanceId,
        language: SupportedLanguage,
        version: Sts2VersionDefinition?,
        selectedSteamAccount: LauncherAccount?,
        steamBranch: String,
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
            notifyWindowsUi(onStateChanged)
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
                        "windows.steam.download.resuming_paused_task"
                    } else {
                        "windows.steam.download.preparing_repair_files"
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
        notifyWindowsUi(onStateChanged)
        Thread(
            {
                try {
                    val result = runCatching {
                        runBlocking(Dispatchers.IO) {
                            performSelectiveDownload(
                                language = language,
                                version = normalizedVersion,
                                selectedSteamAccount = selectedSteamAccount,
                                steamBranch = normalizedBranch,
                                hostPaths = hostPaths,
                                steamDepot = steamDepot,
                                cancelSignal = cancelSignal,
                                pauseSignal = pauseSignal,
                                onProgress = { snapshot -> progressPublisher.publish(snapshot) },
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
                                            ?: steamLocalized(language, "windows.steam.0006"),
                                    ),
                                    updatedAtMillis = System.currentTimeMillis(),
                                )
                            },
                        )
                    }
                    notifyWindowsUi(onStateChanged)
                } finally {
                    downloadCancelSignals.remove(instanceId.value, cancelSignal)
                    downloadPauseSignals.remove(instanceId.value, pauseSignal)
                }
            },
            "sts2-windows-steam-download-${instanceId.value}",
        ).start()
    }
}

internal data class WindowsGameFilePanelState(
    val mode: Sts2GameFileCheckMode,
    val status: Sts2GameFileCheckStatus,
    val passed: Boolean,
    val message: String,
    val targetPath: String?,
    val currentFilePath: String? = null,
    val expectedFileCount: Int? = null,
    val localFileCount: Int? = null,
    val checkedFileCount: Int? = null,
    val okFileCount: Int? = null,
    val missingFileCount: Int? = null,
    val mismatchedFileCount: Int? = null,
    val extraFileCount: Int? = null,
    val problemFilesPreview: List<String> = emptyList(),
)

private data class PreparedWindowsSteamManifest(
    val manifestPath: String,
    val depotKeyHex: String,
    val requestCode: Long,
    val manifestId: String,
    val manifestFiles: List<SteamManifestFileEntry>,
)

private data class WindowsSteamManifestIdentity(
    val depotId: Int,
    val manifestGid: Long,
    val manifestId: String,
)

private data class WindowsSteamRepairPlan(
    val checkSnapshot: Sts2GameFileCheckSnapshot,
    val filesToDownload: List<SteamManifestFileEntry>,
)

private data class WindowsSteamStreamingVerifyResult(
    val verifyResult: SteamDepotLocalVerifyResult,
    val localFileCount: Int,
    val extraLocalFiles: List<String>,
    val manifestFileCount: Int,
)

private data class Sts2WindowsSelectiveDownloadOutcome(
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
        if (now - lastPublishedAtMillis < minIntervalMillis) return
        lastPublishedAtMillis = now
        publishNow(snapshot)
    }
}

private suspend fun performSelectiveDownload(
    language: SupportedLanguage,
    version: Sts2VersionDefinition?,
    selectedSteamAccount: LauncherAccount?,
    steamBranch: String,
    hostPaths: ExtensionHostPaths,
    steamDepot: SteamDepotService?,
    cancelSignal: AtomicBoolean,
    pauseSignal: AtomicBoolean,
    onProgress: (Sts2GameFileDownloadSnapshot) -> Unit,
    loadCachedRepairPlan: (String) -> Sts2SteamRepairPlanRecord,
): Sts2WindowsSelectiveDownloadOutcome {
    val normalizedVersion = version?.normalizedGameFileVersion()
    require(normalizedVersion != null) {
        steamLocalized(language, "windows.steam.0007")
    }
    require(normalizedVersion.gameDirectory.isNotBlank()) {
        steamLocalized(language, "windows.steam.0008")
    }
    val steamId = selectedSteamAccount.requireSteamId(language)
    val steamAccount = requireNotNull(selectedSteamAccount)
    val depot = steamDepot.requireSteamDepotService(language)
        ensureSteamOperationNotCanceled(cancelSignal, language, "windows.steam.cancel.download")
        onProgress(
            buildSteamDownloadRunningSnapshot(
                version = normalizedVersion,
                selectedSteamAccount = steamAccount,
                steamBranch = steamBranch,
                message = steamLocalized(language, "windows.steam.0009"),
            ),
        )
        val latestManifest = fetchLatestSteamManifestIdentity(
            language = language,
            depot = depot,
            steamId = steamId,
            steamBranch = steamBranch,
        )
        val cachedRepairPlanRecord = loadCachedRepairPlan(latestManifest.manifestId)
        val cachedPreparedManifest = cachedRepairPlanRecord.toPreparedWindowsSteamManifestOrNull(
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
        ensureSteamOperationNotCanceled(cancelSignal, language, "windows.steam.cancel.download")
        val cachedPlan = cachedPreparedManifest?.let {
            cachedRepairPlanRecord.toWindowsSteamRepairPlanOrNull(
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
                    message = steamLocalized(language, "windows.steam.0010"),
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
                    message = steamLocalized(language, "windows.steam.0011"),
                ),
            )
            val verifyResult = runSteamStreamingVerify(
                language = language,
                version = normalizedVersion,
                selectedSteamAccount = selectedSteamAccount,
                steamBranch = steamBranch,
                depot = depot,
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
            ensureSteamOperationNotCanceled(cancelSignal, language, "windows.steam.cancel.download")
            buildSteamRepairPlan(
                language = language,
                version = normalizedVersion,
                selectedSteamAccount = selectedSteamAccount,
                steamBranch = steamBranch,
                verifyResult = verifyResult,
                preparedManifest = preparedManifest,
            )
        }
        ensureSteamOperationNotCanceled(cancelSignal, language, "windows.steam.cancel.download")
        if (cachedPlan == null) {
            onProgress(
                buildSteamDownloadRunningSnapshot(
                    version = normalizedVersion,
                    selectedSteamAccount = steamAccount,
                    steamBranch = steamBranch,
                    message = steamLocalized(language, "windows.steam.0012"),
                    totalFileCount = plan.filesToDownload.size,
                    completedFileCount = 0,
                ),
            )
        }
        if (plan.checkSnapshot.passed) {
            return Sts2WindowsSelectiveDownloadOutcome(
                downloadSnapshot = Sts2GameFileDownloadSnapshot(
                    status = Sts2GameFileDownloadStatus.COMPLETED,
                    versionClientId = normalizedVersion.clientId,
                    gameDirectory = normalizedVersion.gameDirectory,
                    steamAccountSubjectId = steamAccount.subjectId,
                    steamBranch = steamBranch,
                    message = steamLocalized(language, "windows.steam.0013"),
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
        val skippedFileCount = (plan.checkSnapshot.expectedFileCount ?: 0) - totalFilesToDownload
        var completedBytes = 0L

        plan.filesToDownload.forEachIndexed { index, entry ->
            val relativePath = manifestRelativePath(entry)
            var pauseRequestedToNative = false
            ensureSteamOperationNotCanceled(cancelSignal, language, "windows.steam.cancel.download")
            val targetFile = resolveRelativeFile(gameRoot, relativePath)
            targetFile.parentFile?.mkdirs()
            val taskHandle = depot.startFileDownload(
                inputPath = preparedManifest.manifestPath,
                outputPath = targetFile.absolutePath,
                depotKeyHex = preparedManifest.depotKeyHex,
                filePath = relativePath,
                fileIndex = null,
                maxCount = STS2_STEAM_MAX_COUNT,
            )
            try {
                while (true) {
                    if (cancelSignal.get()) {
                        runCatching { depot.cancelTask(taskHandle) }
                        throw Sts2SteamOperationCanceledException(steamLocalized(language, "windows.steam.0014"))
                    }
                    if (pauseSignal.get() && !pauseRequestedToNative) {
                        runCatching { depot.pauseTask(taskHandle) }
                        pauseRequestedToNative = true
                    }
                    val taskSnapshot = depot.pollTask(taskHandle)
                    onProgress(
                        Sts2GameFileDownloadSnapshot(
                            status = Sts2GameFileDownloadStatus.RUNNING,
                            versionClientId = normalizedVersion.clientId,
                            gameDirectory = normalizedVersion.gameDirectory,
                            steamAccountSubjectId = steamAccount.subjectId,
                            steamBranch = steamBranch,
                            message = buildSteamDownloadProgressMessage(language, relativePath, taskSnapshot, index, totalFilesToDownload),
                            currentFilePath = relativePath,
                            totalFileCount = totalFilesToDownload,
                            completedFileCount = index,
                            skippedFileCount = skippedFileCount,
                            deletedExtraFileCount = 0,
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
                                return Sts2WindowsSelectiveDownloadOutcome(
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
                                        deletedExtraFiles = 0,
                                        totalBytesToDownload = totalBytesToDownload,
                                        completedBytes = completedBytes,
                                    ),
                                    postDownloadCheckSnapshot = null,
                                )
                            }
                            taskSnapshot.canceled -> {
                                throw Sts2SteamOperationCanceledException(steamLocalized(language, "windows.steam.0015"))
                            }
                            else -> {
                                error(taskSnapshot.message.ifBlank { steamLocalized(language, "windows.steam.0016") })
                            }
                        }
                    }
                    delay(STS2_STEAM_DOWNLOAD_POLL_INTERVAL_MILLIS)
                }
            } finally {
                depot.disposeTask(taskHandle)
            }
        }

        val postCheck = performSteamCheckWithPreparedManifest(
            language = language,
            version = normalizedVersion,
            selectedSteamAccount = selectedSteamAccount,
            steamBranch = steamBranch,
            depot = depot,
            preparedManifest = preparedManifest,
            cancelSignal = cancelSignal,
        )
        return Sts2WindowsSelectiveDownloadOutcome(
            downloadSnapshot = Sts2GameFileDownloadSnapshot(
                status = Sts2GameFileDownloadStatus.COMPLETED,
                versionClientId = normalizedVersion.clientId,
                gameDirectory = normalizedVersion.gameDirectory,
                steamAccountSubjectId = steamAccount.subjectId,
                steamBranch = steamBranch,
                message = if (postCheck.passed) {
                    steamLocalized(language, "windows.steam.0017")
                } else {
                    steamLocalized(language, "windows.steam.0018")
                },
                totalFileCount = totalFilesToDownload,
                completedFileCount = totalFilesToDownload,
                skippedFileCount = skippedFileCount,
                deletedExtraFileCount = 0,
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
    hostPaths: ExtensionHostPaths,
    steamDepot: SteamDepotService?,
    cancelSignal: AtomicBoolean,
    onProgress: (Sts2GameFileCheckSnapshot) -> Unit = {},
): Sts2GameFileCheckSnapshot {
    val normalizedVersion = version?.normalizedGameFileVersion()
    require(normalizedVersion != null) {
        steamLocalized(language, "windows.steam.0019")
    }
    require(normalizedVersion.gameDirectory.isNotBlank()) {
        steamLocalized(language, "windows.steam.0020")
    }
    val steamId = selectedSteamAccount.requireSteamId(language)
    val depot = steamDepot.requireSteamDepotService(language)
        ensureSteamOperationNotCanceled(cancelSignal, language, "windows.steam.cancel.verify")
        onProgress(
            buildSteamCheckRunningSnapshot(
                version = normalizedVersion,
                selectedSteamAccount = selectedSteamAccount,
                steamBranch = steamBranch,
                message = steamLocalized(language, "windows.steam.0021"),
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
        ensureSteamOperationNotCanceled(cancelSignal, language, "windows.steam.cancel.verify")
        return performSteamCheckWithPreparedManifest(
            language = language,
            version = normalizedVersion,
            selectedSteamAccount = selectedSteamAccount,
            steamBranch = steamBranch,
            depot = depot,
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
    depot: SteamDepotService,
    preparedManifest: PreparedWindowsSteamManifest,
    cancelSignal: AtomicBoolean,
    onProgress: (Sts2GameFileCheckSnapshot) -> Unit = {},
): Sts2GameFileCheckSnapshot {
    val verifyResult = runSteamStreamingVerify(
        language = language,
        version = version,
        selectedSteamAccount = selectedSteamAccount,
        steamBranch = steamBranch,
        depot = depot,
        preparedManifest = preparedManifest,
        cancelSignal = cancelSignal,
        onProgress = onProgress,
    )
    return buildSteamRepairPlan(
        language = language,
        version = version,
        selectedSteamAccount = selectedSteamAccount,
        steamBranch = steamBranch,
        verifyResult = verifyResult,
        preparedManifest = preparedManifest,
    ).checkSnapshot
}

private fun buildSteamRepairPlan(
    language: SupportedLanguage,
    version: Sts2VersionDefinition,
    selectedSteamAccount: LauncherAccount?,
    steamBranch: String,
    verifyResult: WindowsSteamStreamingVerifyResult,
    preparedManifest: PreparedWindowsSteamManifest,
): WindowsSteamRepairPlan {
    val manifestEntryByPath = preparedManifest.manifestFiles.associateBy(::manifestRelativePath)
    val filesToDownload = mutableListOf<SteamManifestFileEntry>()
    val problemFiles = mutableListOf<String>()
    verifyResult.verifyResult.entries.forEach { entry ->
        val relativePath = verifyEntryRelativePath(entry) ?: return@forEach
        when (entry.statusCode) {
            1 -> {
                problemFiles += steamLocalized(language, "windows.steam.0022", listOf(relativePath))
                manifestEntryByPath[relativePath]?.let(filesToDownload::add)
            }
            2 -> {
                problemFiles += steamLocalized(language, "windows.steam.0023", listOf(relativePath))
                manifestEntryByPath[relativePath]?.let(filesToDownload::add)
            }
        }
    }
    if ((verifyResult.verifyResult.missingCount > 0L || verifyResult.verifyResult.mismatchedCount > 0L) && filesToDownload.isEmpty()) {
        error(
            steamLocalized(language, "windows.steam.0024"),
        )
    }
    val distinctFilesToDownload = filesToDownload.distinctBy(::manifestRelativePath)
    return WindowsSteamRepairPlan(
        checkSnapshot = buildSteamCheckSnapshotFromVerifyResult(
            language = language,
            version = version,
            selectedSteamAccount = selectedSteamAccount,
            steamBranch = steamBranch,
            verifyResult = verifyResult.verifyResult,
            localFileCount = verifyResult.localFileCount,
            extraLocalFiles = verifyResult.extraLocalFiles,
            manifestFileCount = verifyResult.manifestFileCount,
            problemFilesPreview = buildProblemFilesPreview(language, problemFiles),
        ).copy(
            steamManifestId = preparedManifest.manifestId,
            steamManifestPath = preparedManifest.manifestPath,
            steamDepotKeyHex = preparedManifest.depotKeyHex,
            repairFilePaths = distinctFilesToDownload.map(::manifestRelativePath),
        ),
        filesToDownload = distinctFilesToDownload,
    )
}

private suspend fun Sts2SteamRepairPlanRecord.toPreparedWindowsSteamManifestOrNull(
    depot: SteamDepotService,
    latestManifest: WindowsSteamManifestIdentity,
): PreparedWindowsSteamManifest? {
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
    return PreparedWindowsSteamManifest(
        manifestPath = manifestPath,
        depotKeyHex = depotKeyHex,
        requestCode = 0L,
        manifestId = latestManifest.manifestId,
        manifestFiles = manifestFiles.files,
    )
}

private fun Sts2SteamRepairPlanRecord.toWindowsSteamRepairPlanOrNull(
    language: SupportedLanguage,
    version: Sts2VersionDefinition,
    selectedSteamAccount: LauncherAccount?,
    steamBranch: String,
    preparedManifest: PreparedWindowsSteamManifest,
): WindowsSteamRepairPlan? {
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
        steamLocalized(language, "windows.steam.0025")
    } else {
        steamLocalized(language, "windows.steam.0026")
    }
    return WindowsSteamRepairPlan(
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
                    steamLocalized(language, "windows.steam.0027", listOf(relativePath))
                },
            ),
            steamManifestId = preparedManifest.manifestId,
            steamManifestPath = preparedManifest.manifestPath,
            steamDepotKeyHex = preparedManifest.depotKeyHex,
            repairFilePaths = normalizedRepairPaths,
            checkedAtMillis = checkedAtMillis,
        ),
        filesToDownload = filesToDownload,
    )
}

private suspend fun runSteamStreamingVerify(
    language: SupportedLanguage,
    version: Sts2VersionDefinition,
    selectedSteamAccount: LauncherAccount?,
    steamBranch: String,
    depot: SteamDepotService,
    preparedManifest: PreparedWindowsSteamManifest,
    cancelSignal: AtomicBoolean,
    onProgress: (Sts2GameFileCheckSnapshot) -> Unit = {},
): WindowsSteamStreamingVerifyResult {
    val gameRoot = File(version.gameDirectory)
    onProgress(
        buildSteamCheckRunningSnapshot(
            version = version,
            selectedSteamAccount = selectedSteamAccount,
            steamBranch = steamBranch,
            message = steamLocalized(language, "windows.steam.0028"),
        ),
    )
    val localFiles = listRelativeFiles(gameRoot)
    val manifestPaths = preparedManifest.manifestFiles.map(::manifestRelativePath).toHashSet()
    val extraLocalFiles = localFiles.filter { relativePath -> relativePath !in manifestPaths }
    onProgress(
        buildSteamCheckRunningSnapshot(
            version = version,
            selectedSteamAccount = selectedSteamAccount,
            steamBranch = steamBranch,
            message = steamLocalized(language, "windows.steam.0029"),
        ),
    )
    val taskHandle = depot.startVerifyLocalFiles(
        inputPath = preparedManifest.manifestPath,
        localRoot = gameRoot.absolutePath,
        depotKeyHex = preparedManifest.depotKeyHex,
        filterText = null,
    )
    try {
        while (true) {
            if (cancelSignal.get()) {
                runCatching { depot.cancelTask(taskHandle) }
                throw Sts2SteamOperationCanceledException(steamLocalized(language, "windows.steam.0030"))
            }
            val taskSnapshot = depot.pollTask(taskHandle)
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
                    message = buildSteamCheckTaskMessage(language, checkedFileCount, expectedFileCount, taskSnapshot),
                    targetPath = version.gameDirectory,
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
                    throw Sts2SteamOperationCanceledException(steamLocalized(language, "windows.steam.0031"))
                }
                val verifyResult = taskSnapshot.verifyResult
                    ?: error(taskSnapshot.message.ifBlank { steamLocalized(language, "windows.steam.0032") })
                if (!taskSnapshot.succeeded && verifyResult.moduleStatus.isBlank()) {
                    error(taskSnapshot.message.ifBlank { steamLocalized(language, "windows.steam.0033") })
                }
                return WindowsSteamStreamingVerifyResult(
                    verifyResult = verifyResult,
                    localFileCount = localFiles.size,
                    extraLocalFiles = extraLocalFiles,
                    manifestFileCount = preparedManifest.manifestFiles.size,
                )
            }
            delay(STS2_STEAM_DOWNLOAD_POLL_INTERVAL_MILLIS)
        }
    } finally {
        runCatching { depot.disposeTask(taskHandle) }
    }
}

private suspend fun prepareSteamManifest(
    language: SupportedLanguage,
    depot: SteamDepotService,
    steamId: Long,
    steamBranch: String,
    hostPaths: ExtensionHostPaths,
    latestManifest: WindowsSteamManifestIdentity? = null,
    onStage: (String) -> Unit = {},
): PreparedWindowsSteamManifest {
    val manifestIdentity = latestManifest ?: fetchLatestSteamManifestIdentity(
        language = language,
        depot = depot,
        steamId = steamId,
        steamBranch = steamBranch,
        onStage = onStage,
    )
    onStage(steamLocalized(language, "windows.steam.0034"))
    val depotKey = depot.fetchDepotKey(steamId, STS2_STEAM_APP_ID, manifestIdentity.depotId, STS2_STEAM_MAX_COUNT)
    require(depotKey.present && depotKey.keyHex.isNotBlank()) {
        steamDepotKeyFailureMessage(language, depotKey.eresult)
    }
    onStage(steamLocalized(language, "windows.steam.0035"))
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
        steamLocalized(language, "windows.steam.0036")
    }
    val manifestCacheDirectory = resolveManifestCacheDirectory(hostPaths)
    manifestCacheDirectory.mkdirs()
    val manifestFile = manifestCacheDirectory.resolve("$STS2_STEAM_APP_ID-$steamBranch-${manifestIdentity.depotId}-${manifestIdentity.manifestGid}.bin")
    if (!manifestFile.isFile) {
        onStage(steamLocalized(language, "windows.steam.0037"))
        depot.downloadManifest(manifestIdentity.depotId, manifestIdentity.manifestGid, requestCode.requestCode, manifestFile.absolutePath, STS2_STEAM_MAX_COUNT)
    }
    onStage(steamLocalized(language, "windows.steam.0038"))
    val manifestFiles = depot.listManifestFiles(manifestFile.absolutePath, depotKey.keyHex, "", STS2_STEAM_MANIFEST_FILE_LIMIT)
    require(manifestFiles.present) {
        steamLocalized(language, "windows.steam.0039")
    }
    require(manifestFiles.printedCount >= manifestFiles.totalCount) {
        steamLocalized(language, "windows.steam.0040")
    }
    return PreparedWindowsSteamManifest(
        manifestPath = manifestFile.absolutePath,
        depotKeyHex = depotKey.keyHex,
        requestCode = requestCode.requestCode,
        manifestId = manifestIdentity.manifestId,
        manifestFiles = manifestFiles.files,
    )
}

private suspend fun fetchLatestSteamManifestIdentity(
    language: SupportedLanguage,
    depot: SteamDepotService,
    steamId: Long,
    steamBranch: String,
    onStage: (String) -> Unit = {},
): WindowsSteamManifestIdentity {
    onStage(steamLocalized(language, "windows.steam.0041"))
    val preflight = depot.fetchPreflight(steamId, STS2_STEAM_APP_ID, steamBranch, STS2_STEAM_MAX_COUNT)
    require(preflight.present) {
        steamLocalized(language, "windows.steam.0042")
    }
    val depotEntry = preferredWindowsDepot(preflight.depots)
        ?: error(
            steamLocalized(language, "windows.steam.0043"),
        )
    return WindowsSteamManifestIdentity(
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
                steamLocalized(language, "windows.steam.0044")
            } else {
                steamLocalized(language, "windows.steam.0045")
            }
        } else {
            steamLocalized(language, "windows.steam.0046")
        },
        targetPath = version.gameDirectory,
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
        message = steamLocalized(language, "windows.steam.0047"),
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
        message = steamLocalized(language, "windows.steam.0048"),
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
        message = steamLocalized(language, "windows.steam.0049"),
        updatedAtMillis = System.currentTimeMillis(),
    )
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
        moduleStatus = taskSnapshot.moduleStatus.ifBlank { taskSnapshot.verifyResult?.moduleStatus.orEmpty() },
        fallbackMessage = steamLocalized(
            language,
            "windows.steam.task.processing_file",
            listOf(checkedFileCount, expectedFileCount),
        ),
        rawFallback = taskSnapshot.message,
    )
    return steamLocalized(language, "windows.steam.0050", listOf(detail))
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
        moduleStatus = taskSnapshot.moduleStatus,
        fallbackMessage = steamLocalized(
            language,
            "windows.steam.task.downloading_file",
            listOf(completedIndex + 1, totalFilesToDownload),
        ),
        rawFallback = taskSnapshot.message,
    )
    return steamLocalized(language, "windows.steam.0051", listOf(relativePath, detail))
}

private fun buildSteamTaskDetail(
    language: SupportedLanguage,
    phase: String,
    moduleStatus: String,
    fallbackMessage: String,
    rawFallback: String = "",
): String {
    val normalizedPhase = phase.trim()
    val localizedStatus = localizedDepotModuleStatus(language, moduleStatus)
    val primary = sequenceOf(
        normalizedPhase.takeIf { it.isNotBlank() },
        localizedStatus,
        rawFallback.trim().takeIf { it.isNotBlank() },
        fallbackMessage,
    ).first { !it.isNullOrBlank() } ?: fallbackMessage
    val extras = mutableListOf<String>()
    localizedStatus?.takeIf { !it.equals(primary, ignoreCase = true) }?.let(extras::add)
    return if (extras.isEmpty()) primary else steamLocalized(language, "windows.steam.0052", listOf(primary, extras.joinToString(" / ")))
}

private fun localizedDepotModuleStatus(
    language: SupportedLanguage,
    moduleStatus: String,
): String? {
    return when (moduleStatus.trim().lowercase()) {
        "" -> null
        "queued" -> steamLocalized(language, "windows.steam.0053")
        "reading" -> steamLocalized(language, "windows.steam.0054")
        "polling" -> steamLocalized(language, "windows.steam.0055")
        "pausing" -> steamLocalized(language, "windows.steam.0056")
        "paused" -> steamLocalized(language, "windows.steam.0057")
        "canceling" -> steamLocalized(language, "windows.steam.0058")
        "succeeded" -> steamLocalized(language, "windows.steam.0059")
        "failed" -> steamLocalized(language, "windows.steam.0060")
        "canceled" -> steamLocalized(language, "windows.steam.0061")
        "idle" -> steamLocalized(language, "windows.steam.0062")
        else -> moduleStatus.trim()
    }
}

private fun buildProblemFilesPreview(
    language: SupportedLanguage,
    problemFiles: List<String>,
): List<String> {
    val previewLimit = 8
    if (problemFiles.isEmpty()) return emptyList()
    val preview = problemFiles.take(previewLimit).toMutableList()
    val remaining = problemFiles.size - preview.size
    if (remaining > 0) {
        preview += steamLocalized(language, "windows.steam.0063", listOf(remaining))
    }
    return preview
}

private fun steamDepotKeyFailureMessage(
    language: SupportedLanguage,
    eresult: Int,
): String {
    return when (eresult) {
        9 -> steamLocalized(language, "windows.steam.0064")
        15 -> steamLocalized(language, "windows.steam.0065")
        8 -> steamLocalized(language, "windows.steam.0066")
        else -> steamLocalized(language, "windows.steam.0067")
    }
}

private fun classifySteamFailureMessage(
    language: SupportedLanguage,
    rawMessage: String,
): String {
    val normalized = rawMessage.lowercase()
    return when {
        "eresult=9" in normalized -> steamLocalized(language, "windows.steam.0068")
        "eresult=15" in normalized -> steamLocalized(language, "windows.steam.0069")
        "eresult=8" in normalized -> steamLocalized(language, "windows.steam.0070")
        "timeout" in normalized || "timed out" in normalized || "connection reset" in normalized || "unable to resolve host" in normalized || "unknownhost" in normalized || "network" in normalized ->
            steamLocalized(language, "windows.steam.0071")
        "not a steam account" in normalized -> steamLocalized(language, "windows.steam.0072")
        else -> rawMessage
    }
}

private fun preferredWindowsDepot(entries: List<SteamDepotPreflightEntry>): SteamDepotPreflightEntry? {
    return entries
        .filter { entry -> entry.manifestGid != 0L }
        .maxWithOrNull(compareBy<SteamDepotPreflightEntry>({ depotPlatformScore(it.platformLabel) }, { if (it.keyAvailable) 1 else 0 }, { it.size }))
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
    if (!root.isDirectory) return emptyList()
    return root.walkTopDown().filter { file -> file.isFile }.map { file -> file.relativeTo(root).invariantSeparatorsPath }.toList()
}

private fun resolveManifestCacheDirectory(hostPaths: ExtensionHostPaths): File {
    val root = hostPaths.appFilesDirectoryPath?.takeIf { it.isNotBlank() }
        ?: hostPaths.launcherStorageDirectoryPath?.takeIf { it.isNotBlank() }
        ?: error("Launcher storage path is unavailable for Steam manifest caching.")
    return File(root).resolve(STS2_STEAM_MANIFEST_CACHE_DIRECTORY)
}

private fun resolveRelativeFile(root: File, relativePath: String): File {
    return normalizeManifestRelativePath(relativePath)
        .split('/')
        .filter { segment -> segment.isNotBlank() }
        .fold(root) { current, segment -> File(current, segment) }
}

private fun manifestRelativePath(entry: SteamManifestFileEntry): String = normalizeManifestRelativePath(entry.filename)

private fun normalizeManifestRelativePath(relativePath: String): String {
    val segments = relativePath.split('/', '\\').filter { segment -> segment.isNotBlank() && segment != "." }
    if (segments.isEmpty()) return relativePath.replace('\\', '/')
    return segments.joinToString("/")
}

private fun verifyEntryRelativePath(entry: SteamDepotLocalVerifyEntry): String? {
    return entry.manifestFilename.takeIf { it.isNotBlank() }?.let(::normalizeManifestRelativePath)
}

private fun LauncherAccount?.requireSteamId(language: SupportedLanguage): Long {
    require(this != null) { steamLocalized(language, "windows.steam.0073") }
    require(provider == LauncherAccountProvider.STEAM) {
        steamLocalized(language, "windows.steam.0074")
    }
    return subjectId.toLongOrNull()
        ?: error(steamLocalized(language, "windows.steam.0075"))
}

private fun SteamDepotService?.requireSteamDepotService(language: SupportedLanguage): SteamDepotService {
    return this ?: error(steamLocalized(language, "windows.steam.host_service_unavailable"))
}

private fun ensureSteamOperationNotCanceled(cancelSignal: AtomicBoolean, language: SupportedLanguage, messageKey: String) {
    if (cancelSignal.get()) {
        throw Sts2SteamOperationCanceledException(steamLocalized(language, messageKey))
    }
}

private fun notifyWindowsUi(onStateChanged: () -> Unit) {
    EventQueue.invokeLater(onStateChanged)
}

private fun Long.toSafeInt(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

private fun steamAccountCardSubtitle(language: SupportedLanguage, savedSteamAccounts: List<LauncherAccount>, selectedSteamAccount: LauncherAccount?): String {
    return when {
        savedSteamAccounts.isEmpty() -> windowsLocalized(language, "windows.steam.0076")
        selectedSteamAccount == null -> windowsLocalized(language, "windows.steam.0077")
        else -> windowsLocalized(language, "windows.steam.0078", listOf(steamAccountTitle(selectedSteamAccount)))
    }
}

private fun steamAccountTitle(account: LauncherAccount): String {
    return account.displayName?.takeIf { it.isNotBlank() } ?: account.subjectId
}

private fun steamAccountOptions(
    language: SupportedLanguage,
    savedSteamAccounts: List<LauncherAccount>,
    selectedSteamAccount: LauncherAccount?,
    onSelect: (String?) -> Unit,
): List<PageChoiceOptionRegistration> {
    return buildList {
        add(PageChoiceOptionRegistration("sts2_windows_game_files_account_none", PageTextDirect(windowsLocalized(language, "windows.steam.0079")), selectedSteamAccount == null, onClick = { onSelect(null) }))
        savedSteamAccounts.forEach { account ->
            add(
                PageChoiceOptionRegistration(
                    id = "sts2_windows_game_files_account_${account.subjectId}",
                    label = PageTextDirect(steamAccountTitle(account)),
                    selected = selectedSteamAccount?.subjectId == account.subjectId,
                    onClick = { onSelect(account.subjectId) },
                ),
            )
        }
    }
}

private fun steamBranchOptions(language: SupportedLanguage, selectedSteamBranch: String, onSelect: (String) -> Unit): List<PageChoiceOptionRegistration> {
    val normalizedBranch = normalizeSteamBranch(selectedSteamBranch)
    return listOf(
        PageChoiceOptionRegistration("sts2_windows_game_files_branch_public", PageTextDirect(steamBranchLabel(language, STS2_STEAM_BRANCH_PUBLIC)), normalizedBranch == STS2_STEAM_BRANCH_PUBLIC, onClick = { onSelect(STS2_STEAM_BRANCH_PUBLIC) }),
        PageChoiceOptionRegistration("sts2_windows_game_files_branch_public_beta", PageTextDirect(steamBranchLabel(language, STS2_STEAM_BRANCH_PUBLIC_BETA)), normalizedBranch == STS2_STEAM_BRANCH_PUBLIC_BETA, onClick = { onSelect(STS2_STEAM_BRANCH_PUBLIC_BETA) }),
    )
}

private fun steamBranchLabel(language: SupportedLanguage, steamBranch: String): String {
    return when (normalizeSteamBranch(steamBranch)) {
        STS2_STEAM_BRANCH_PUBLIC_BETA -> windowsLocalized(language, "windows.steam.0080")
        else -> windowsLocalized(language, "windows.steam.0081")
    }
}

private fun gameFileCheckCardSubtitle(language: SupportedLanguage, gameFileState: WindowsGameFilePanelState): String {
    return when (gameFileState.status) {
        Sts2GameFileCheckStatus.RUNNING, Sts2GameFileCheckStatus.FAILED, Sts2GameFileCheckStatus.CANCELED, Sts2GameFileCheckStatus.IDLE -> gameFileState.message
        Sts2GameFileCheckStatus.COMPLETED -> if (gameFileState.passed) windowsLocalized(language, "windows.steam.0082") else windowsLocalized(language, "windows.steam.0083")
    }
}

private fun gameFileDownloadCardSubtitle(language: SupportedLanguage, downloadSnapshot: Sts2GameFileDownloadSnapshot): String {
    return when (downloadSnapshot.status) {
        Sts2GameFileDownloadStatus.RUNNING,
        Sts2GameFileDownloadStatus.PAUSED,
        Sts2GameFileDownloadStatus.FAILED,
        Sts2GameFileDownloadStatus.CANCELED,
        -> downloadSnapshot.message
        Sts2GameFileDownloadStatus.COMPLETED -> downloadSnapshot.message.ifBlank { windowsLocalized(language, "windows.steam.0084") }
        Sts2GameFileDownloadStatus.IDLE -> windowsLocalized(language, "windows.steam.0085")
    }
}

private fun gameFileStatusText(language: SupportedLanguage, status: Sts2GameFileCheckStatus, passed: Boolean): String {
    return when (status) {
        Sts2GameFileCheckStatus.IDLE -> windowsLocalized(language, "windows.steam.0086")
        Sts2GameFileCheckStatus.RUNNING -> windowsLocalized(language, "windows.steam.0087")
        Sts2GameFileCheckStatus.COMPLETED -> if (passed) windowsLocalized(language, "windows.steam.0088") else windowsLocalized(language, "windows.steam.0089")
        Sts2GameFileCheckStatus.CANCELED -> windowsLocalized(language, "windows.steam.0090")
        Sts2GameFileCheckStatus.FAILED -> windowsLocalized(language, "windows.steam.0091")
    }
}

private fun downloadStatusText(language: SupportedLanguage, status: Sts2GameFileDownloadStatus): String {
    return when (status) {
        Sts2GameFileDownloadStatus.IDLE -> windowsLocalized(language, "windows.steam.0092")
        Sts2GameFileDownloadStatus.RUNNING -> windowsLocalized(language, "windows.steam.0093")
        Sts2GameFileDownloadStatus.PAUSED -> windowsLocalized(language, "windows.steam.0094")
        Sts2GameFileDownloadStatus.COMPLETED -> windowsLocalized(language, "windows.steam.0095")
        Sts2GameFileDownloadStatus.CANCELED -> windowsLocalized(language, "windows.steam.0096")
        Sts2GameFileDownloadStatus.FAILED -> windowsLocalized(language, "windows.steam.0097")
    }
}

private fun steamDifferenceSummary(language: SupportedLanguage, gameFileState: WindowsGameFilePanelState): String {
    val missing = gameFileState.missingFileCount ?: 0
    val mismatched = gameFileState.mismatchedFileCount ?: 0
    val extra = gameFileState.extraFileCount ?: 0
    return windowsLocalized(language, "windows.steam.0098", listOf(missing, mismatched, extra))
}

private fun checkProgressFraction(gameFileState: WindowsGameFilePanelState): Float? {
    val total = gameFileState.expectedFileCount?.takeIf { it > 0 } ?: return null
    val current = (gameFileState.checkedFileCount ?: 0).coerceAtLeast(0)
    return (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

private fun checkProgressText(language: SupportedLanguage, gameFileState: WindowsGameFilePanelState): String? {
    val total = gameFileState.expectedFileCount?.takeIf { it > 0 } ?: return null
    val current = (gameFileState.checkedFileCount ?: 0).coerceAtLeast(0)
    return windowsLocalized(language, "windows.steam.0099", listOf(current, total))
}

private fun downloadProgressFraction(snapshot: Sts2GameFileDownloadSnapshot): Float? {
    val totalBytes = snapshot.totalBytes?.takeIf { it > 0L }
    if (totalBytes != null) {
        val downloadedBytes = snapshot.downloadedBytes?.coerceAtLeast(0L) ?: 0L
        return (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    }
    val totalFiles = snapshot.totalFileCount?.takeIf { it > 0 } ?: return null
    val completed = (snapshot.completedFileCount ?: 0).coerceAtLeast(0)
    return (completed.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f)
}

private fun downloadProgressText(language: SupportedLanguage, snapshot: Sts2GameFileDownloadSnapshot): String? {
    val totalBytes = snapshot.totalBytes?.takeIf { it > 0L }
    if (totalBytes != null) {
        val downloadedBytes = snapshot.downloadedBytes?.coerceAtLeast(0L) ?: 0L
        return windowsLocalized(language, "windows.steam.0100", listOf(formatBytes(downloadedBytes), formatBytes(totalBytes)))
    }
    val totalFiles = snapshot.totalFileCount ?: return null
    val completedFiles = snapshot.completedFileCount ?: 0
    return windowsLocalized(language, "windows.steam.0101", listOf(completedFiles, totalFiles))
}

private fun currentFileDownloadProgressText(language: SupportedLanguage, snapshot: Sts2GameFileDownloadSnapshot): String? {
    val totalBytes = snapshot.currentFileTotalBytes?.takeIf { it > 0L } ?: return null
    val downloadedBytes = snapshot.currentFileDownloadedBytes?.coerceAtLeast(0L) ?: 0L
    return windowsLocalized(language, "windows.steam.0102", listOf(formatBytes(downloadedBytes), formatBytes(totalBytes)))
}

private fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe >= 1024L * 1024L * 1024L -> "${scaledSize(safe / (1024.0 * 1024.0 * 1024.0))} GB"
        safe >= 1024L * 1024L -> "${scaledSize(safe / (1024.0 * 1024.0))} MB"
        safe >= 1024L -> "${scaledSize(safe / 1024.0)} KB"
        else -> "$safe B"
    }
}

private fun scaledSize(value: Double): String {
    val scaled = kotlin.math.round(value * 100.0) / 100.0
    return if (scaled.toLong().toDouble() == scaled) scaled.toLong().toString() else scaled.toString()
}

private fun buildProgressSupportingText(message: String?, trailing: String?): String? {
    return buildList {
        message?.takeIf { it.isNotBlank() }?.let(::add)
        trailing?.takeIf { it.isNotBlank() }?.let(::add)
    }.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

private fun gameFileRow(language: SupportedLanguage, labelKey: String, value: String): PageValueItemRegistration {
    return PageValueItemRegistration(
        label = PageTextDirect(windowsLocalized(language, labelKey)),
        value = PageTextDirect(value),
    )
}

internal fun currentWindowsGameFileCheck(
    language: SupportedLanguage,
    version: Sts2VersionDefinition?,
    steamVerificationEnabled: Boolean,
    steamBranch: String,
    selectedSteamAccount: LauncherAccount?,
    storedSnapshot: Sts2GameFileCheckSnapshot,
    steamVerificationRecord: Sts2SteamVerificationRecord,
): WindowsGameFilePanelState {
    if (!steamVerificationEnabled) {
        val passed = version?.gameDirectory?.trim()?.takeIf { it.isNotBlank() }?.let {
            File(it, "SlayTheSpire2.pck").isFile
        } ?: false
        return WindowsGameFilePanelState(
            mode = Sts2GameFileCheckMode.SIMPLE,
            status = Sts2GameFileCheckStatus.COMPLETED,
            passed = passed,
            message = if (passed) {
                windowsLocalized(language, "windows.steam.0103")
            } else {
                windowsLocalized(language, "windows.steam.0104")
            },
            targetPath = version?.gameDirectory?.trim()?.takeIf { it.isNotBlank() },
        )
    }
    if (version == null) {
        return WindowsGameFilePanelState(
            mode = Sts2GameFileCheckMode.STEAM,
            status = Sts2GameFileCheckStatus.IDLE,
            passed = false,
            message = windowsLocalized(language, "windows.steam.0105"),
            targetPath = null,
        )
    }
    val gameDirectory = version.gameDirectory.trim()
    if (gameDirectory.isBlank()) {
        return WindowsGameFilePanelState(
            mode = Sts2GameFileCheckMode.STEAM,
            status = Sts2GameFileCheckStatus.IDLE,
            passed = false,
            message = windowsLocalized(language, "windows.steam.0106"),
            targetPath = null,
        )
    }
    val matchingSnapshot = storedSnapshot.matches(version, Sts2GameFileCheckMode.STEAM, steamBranch)
    if (matchingSnapshot && storedSnapshot.status == Sts2GameFileCheckStatus.RUNNING) {
        return storedSnapshot.toWindowsPanelState()
    }
    if (steamVerificationRecord.status != Sts2SteamVerificationStatus.UNVERIFIED) {
        val snapshotPanel = if (matchingSnapshot && storedSnapshot.matchesSteamVerificationRecord(steamVerificationRecord)) {
            storedSnapshot.toWindowsPanelState()
        } else {
            null
        }
        return snapshotPanel?.copy(passed = steamVerificationRecord.passed)
            ?: steamVerificationRecord.toWindowsPanelState(language, version)
    }
    if (selectedSteamAccount == null) {
        return WindowsGameFilePanelState(
            mode = Sts2GameFileCheckMode.STEAM,
            status = Sts2GameFileCheckStatus.IDLE,
            passed = false,
            message = windowsLocalized(language, "windows.steam.0107"),
            targetPath = gameDirectory,
        )
    }
    return steamVerificationRecord.toWindowsPanelState(
        language = language,
        version = version,
        unverifiedMessage = windowsLocalized(language, "windows.steam.0108"),
    )
}

private fun Sts2GameFileCheckSnapshot.toWindowsPanelState(): WindowsGameFilePanelState {
    return WindowsGameFilePanelState(
        mode = mode,
        status = status,
        passed = passed && status == Sts2GameFileCheckStatus.COMPLETED,
        message = message,
        targetPath = targetPath?.takeIf { it.isNotBlank() } ?: gameDirectory?.takeIf { it.isNotBlank() },
        currentFilePath = currentFilePath,
        expectedFileCount = expectedFileCount,
        localFileCount = localFileCount,
        checkedFileCount = checkedFileCount,
        okFileCount = okFileCount,
        missingFileCount = missingFileCount,
        mismatchedFileCount = mismatchedFileCount,
        extraFileCount = extraFileCount,
        problemFilesPreview = problemFilesPreview,
    )
}

private fun Sts2SteamVerificationRecord.toWindowsPanelState(
    language: SupportedLanguage,
    version: Sts2VersionDefinition?,
    unverifiedMessage: String = windowsLocalized(language, "windows.steam.0109"),
): WindowsGameFilePanelState {
    val target = gameDirectory?.takeIf { it.isNotBlank() }
        ?: version?.gameDirectory?.trim()?.takeIf { it.isNotBlank() }
    val checkStatus = when (status) {
        Sts2SteamVerificationStatus.UNVERIFIED -> Sts2GameFileCheckStatus.IDLE
        Sts2SteamVerificationStatus.PASSED -> Sts2GameFileCheckStatus.COMPLETED
        Sts2SteamVerificationStatus.FAILED -> Sts2GameFileCheckStatus.FAILED
    }
    val effectiveMessage = message.ifBlank {
        when (status) {
            Sts2SteamVerificationStatus.UNVERIFIED -> unverifiedMessage
            Sts2SteamVerificationStatus.PASSED -> windowsLocalized(language, "windows.steam.0110")
            Sts2SteamVerificationStatus.FAILED -> windowsLocalized(language, "windows.steam.0111")
        }
    }
    return WindowsGameFilePanelState(
        mode = Sts2GameFileCheckMode.STEAM,
        status = checkStatus,
        passed = passed,
        message = effectiveMessage,
        targetPath = target,
    )
}

private fun Sts2GameFileCheckSnapshot.matchesSteamVerificationRecord(
    record: Sts2SteamVerificationRecord,
): Boolean {
    return mode == Sts2GameFileCheckMode.STEAM &&
        versionClientId == record.versionClientId &&
        gameDirectory == record.gameDirectory &&
        normalizeSteamBranch(steamBranch) == normalizeSteamBranch(record.steamBranch) &&
        when (record.status) {
            Sts2SteamVerificationStatus.PASSED -> status == Sts2GameFileCheckStatus.COMPLETED && passed
            Sts2SteamVerificationStatus.FAILED ->
                status == Sts2GameFileCheckStatus.FAILED ||
                    (status == Sts2GameFileCheckStatus.COMPLETED && !passed)
            Sts2SteamVerificationStatus.UNVERIFIED -> false
        }
}

internal fun currentWindowsDownloadSnapshot(
    version: Sts2VersionDefinition?,
    steamBranch: String,
    selectedSteamAccount: LauncherAccount?,
    storedSnapshot: Sts2GameFileDownloadSnapshot,
): Sts2GameFileDownloadSnapshot {
    if (!storedSnapshot.matches(version, steamBranch)) return Sts2GameFileDownloadSnapshot()
    val storedAccount = storedSnapshot.steamAccountSubjectId?.takeIf { it.isNotBlank() }
    val selectedAccount = selectedSteamAccount?.subjectId
    return if (storedAccount == null || storedAccount == selectedAccount) storedSnapshot else Sts2GameFileDownloadSnapshot()
}

internal fun buildWindowsGameFileHomeWidgets(
    context: PageContext,
    gameId: GameInstanceId,
    language: SupportedLanguage,
    selectedVersion: Sts2VersionDefinition?,
    savedSteamAccounts: List<LauncherAccount>,
    selectedSteamAccount: LauncherAccount?,
    preferences: Sts2GameFilePreferences,
    gameFileState: WindowsGameFilePanelState,
    downloadSnapshot: Sts2GameFileDownloadSnapshot,
    coordinator: Sts2WindowsSteamDepotGameFileCoordinator,
): List<PageWidgetRegistration> {
    val target = setOf(com.dreamyloong.tlauncher.sdk.model.PlatformTarget.WINDOWS)
    return buildList {
        add(PageWidgetRegistration("sts2_windows_steam_account", PageIds.HOME, "home_current_game", STS2_TEMPLATE_ID, 12, target, widget = PageWidgetChoiceCard(
            title = PageTextDirect(windowsLocalized(language, "windows.steam.0112")),
            subtitle = PageTextDirect(steamAccountCardSubtitle(language, savedSteamAccounts, selectedSteamAccount)),
            options = steamAccountOptions(language, savedSteamAccounts, selectedSteamAccount) { subjectId ->
                coordinator.updateSelectedSteamAccount(gameId, subjectId)
                context.refreshPage()
            },
        )))
        add(PageWidgetRegistration("sts2_windows_steam_verification_toggle", PageIds.HOME, "home_current_game", STS2_TEMPLATE_ID, 13, target, widget = PageWidgetToggleCard(
            title = PageTextDirect(windowsLocalized(language, "windows.steam.0113")),
            subtitle = PageTextDirect(windowsLocalized(language, "windows.steam.0114")),
            checked = preferences.steamVerificationEnabled,
            onCheckedChange = { enabled ->
                coordinator.updateSteamVerificationEnabled(gameId, enabled, language, context::refreshPage)
                context.refreshPage()
            },
        )))
        add(PageWidgetRegistration("sts2_windows_steam_branch", PageIds.HOME, "home_current_game", STS2_TEMPLATE_ID, 14, target, widget = PageWidgetChoiceCard(
            title = PageTextDirect(windowsLocalized(language, "windows.steam.0115")),
            subtitle = PageTextDirect(windowsLocalized(language, "windows.steam.0116")),
            options = steamBranchOptions(language, preferences.steamBranch) { branch ->
                coordinator.updateSteamBranch(gameId, branch, language, context::refreshPage)
                context.refreshPage()
            },
        )))
        add(PageWidgetRegistration("sts2_windows_game_file_check", PageIds.HOME, "home_current_game", STS2_TEMPLATE_ID, 15, target, widget = PageWidgetDetailCard(
            title = PageTextDirect(windowsLocalized(language, "windows.steam.0117")),
            subtitle = PageTextDirect(gameFileCheckCardSubtitle(language, gameFileState)),
            rows = buildList {
                add(gameFileRow(language, "windows.steam.row.0001", if (preferences.steamVerificationEnabled) windowsLocalized(language, "windows.steam.0118") else windowsLocalized(language, "windows.steam.0119")))
                if (preferences.steamVerificationEnabled) add(gameFileRow(language, "windows.steam.row.0002", steamBranchLabel(language, preferences.steamBranch)))
                add(gameFileRow(language, "windows.steam.row.0003", gameFileStatusText(language, gameFileState.status, gameFileState.passed)))
                add(gameFileRow(language, "windows.steam.row.0004", selectedSteamAccount?.let(::steamAccountTitle) ?: windowsLocalized(language, "windows.steam.0120")))
                add(gameFileRow(language, "windows.steam.row.0005", gameFileState.targetPath ?: windowsLocalized(language, "windows.steam.0121")))
                gameFileState.currentFilePath?.takeIf { gameFileState.status == Sts2GameFileCheckStatus.RUNNING }?.let { add(gameFileRow(language, "windows.steam.row.0006", it)) }
                if (gameFileState.mode == Sts2GameFileCheckMode.STEAM) {
                    gameFileState.expectedFileCount?.let { add(gameFileRow(language, "windows.steam.row.0007", it.toString())) }
                    gameFileState.localFileCount?.let { add(gameFileRow(language, "windows.steam.row.0008", it.toString())) }
                    gameFileState.checkedFileCount?.let { add(gameFileRow(language, "windows.steam.row.0009", it.toString())) }
                    gameFileState.okFileCount?.let { add(gameFileRow(language, "windows.steam.row.0010", it.toString())) }
                    add(gameFileRow(language, "windows.steam.row.0011", steamDifferenceSummary(language, gameFileState)))
                    gameFileState.extraFileCount?.takeIf { it > 0 }?.let { add(gameFileRow(language, "windows.steam.row.0012", it.toString())) }
                    if (gameFileState.problemFilesPreview.isNotEmpty()) add(gameFileRow(language, "windows.steam.row.0013", gameFileState.problemFilesPreview.joinToString("\n")))
                }
            },
            actions = buildList {
                if (preferences.steamVerificationEnabled && selectedSteamAccount == null) {
                    add(PageActionRegistration("sts2_windows_game_files_open_account_manager", PageTextDirect(windowsLocalized(language, "windows.steam.0122")), style = PageActionStyle.OUTLINED, onClick = { context.openPage(PageIds.ACCOUNT_MANAGER) }))
                }
                if (gameFileState.status == Sts2GameFileCheckStatus.RUNNING) {
                    add(PageActionRegistration("sts2_windows_game_files_cancel_check", PageTextDirect(windowsLocalized(language, "windows.steam.0123")), style = PageActionStyle.FILLED_TONAL, onClick = {
                        coordinator.cancelSteamCheck(gameId, language, context::refreshPage)
                    }))
                } else {
                    add(PageActionRegistration("sts2_windows_game_files_check", PageTextDirect(windowsLocalized(language, "windows.steam.0124")), style = PageActionStyle.FILLED_TONAL, enabled = canRunGameFileCheck(selectedVersion, preferences.steamVerificationEnabled, selectedSteamAccount, gameFileState, downloadSnapshot), onClick = {
                        coordinator.startSteamCheck(gameId, language, selectedVersion, selectedSteamAccount, preferences.steamBranch, context::refreshPage)
                    }))
                }
            },
            tone = when {
                gameFileState.status == Sts2GameFileCheckStatus.FAILED -> PageWidgetTone.DANGER
                gameFileState.status == Sts2GameFileCheckStatus.COMPLETED && gameFileState.passed -> PageWidgetTone.ACCENT
                else -> PageWidgetTone.DEFAULT
            },
        )))
        add(PageWidgetRegistration("sts2_windows_game_file_download", PageIds.HOME, "home_current_game", STS2_TEMPLATE_ID, 16, target, widget = PageWidgetDetailCard(
            title = PageTextDirect(windowsLocalized(language, "windows.steam.0125")),
            subtitle = PageTextDirect(gameFileDownloadCardSubtitle(language, downloadSnapshot)),
            rows = buildList {
                add(gameFileRow(language, "windows.steam.row.0014", steamBranchLabel(language, preferences.steamBranch)))
                add(gameFileRow(language, "windows.steam.row.0015", selectedSteamAccount?.let(::steamAccountTitle) ?: windowsLocalized(language, "windows.steam.0126")))
                add(gameFileRow(language, "windows.steam.row.0016", downloadStatusText(language, downloadSnapshot.status)))
                add(gameFileRow(language, "windows.steam.row.0017", selectedVersion?.gameDirectory?.trim()?.takeIf { it.isNotBlank() } ?: windowsLocalized(language, "windows.steam.0127")))
                downloadSnapshot.currentFilePath?.takeIf {
                    downloadSnapshot.status == Sts2GameFileDownloadStatus.RUNNING ||
                        downloadSnapshot.status == Sts2GameFileDownloadStatus.PAUSED
                }?.let { add(gameFileRow(language, "windows.steam.row.0018", it)) }
                downloadSnapshot.totalFileCount?.let { add(gameFileRow(language, "windows.steam.row.0019", it.toString())) }
                downloadSnapshot.completedFileCount?.let { add(gameFileRow(language, "windows.steam.row.0020", it.toString())) }
                downloadSnapshot.skippedFileCount?.let { add(gameFileRow(language, "windows.steam.row.0021", it.toString())) }
            },
            actions = buildList {
                if (selectedSteamAccount == null) {
                    add(PageActionRegistration("sts2_windows_game_files_download_open_account_manager", PageTextDirect(windowsLocalized(language, "windows.steam.0128")), style = PageActionStyle.OUTLINED, onClick = { context.openPage(PageIds.ACCOUNT_MANAGER) }))
                }
                if (downloadSnapshot.status == Sts2GameFileDownloadStatus.RUNNING) {
                    add(PageActionRegistration("sts2_windows_game_files_pause_download", PageTextDirect(windowsLocalized(language, "windows.steam.0129")), style = PageActionStyle.OUTLINED, onClick = {
                        coordinator.pauseSelectiveDownload(gameId, language, context::refreshPage)
                    }))
                    add(PageActionRegistration("sts2_windows_game_files_cancel_download", PageTextDirect(windowsLocalized(language, "windows.steam.0130")), style = PageActionStyle.FILLED_TONAL, onClick = {
                        coordinator.cancelSelectiveDownload(gameId, language, context::refreshPage)
                    }))
                } else {
                    val downloadActionLabelKey = if (downloadSnapshot.status == Sts2GameFileDownloadStatus.PAUSED) {
                        "windows.steam.resume_download"
                    } else {
                        "windows.steam.download_game_files"
                    }
                    add(PageActionRegistration("sts2_windows_game_files_download", PageTextDirect(windowsLocalized(language, downloadActionLabelKey)), style = PageActionStyle.FILLED_TONAL, enabled = canDownloadGameFiles(selectedVersion, selectedSteamAccount, gameFileState, downloadSnapshot), onClick = {
                        coordinator.startSelectiveDownload(gameId, language, selectedVersion, selectedSteamAccount, preferences.steamBranch, context::refreshPage)
                    }))
                }
            },
            tone = when (downloadSnapshot.status) {
                Sts2GameFileDownloadStatus.FAILED -> PageWidgetTone.DANGER
                Sts2GameFileDownloadStatus.PAUSED -> PageWidgetTone.ACCENT
                Sts2GameFileDownloadStatus.COMPLETED -> PageWidgetTone.ACCENT
                else -> PageWidgetTone.DEFAULT
            },
        )))
        if (gameFileState.status == Sts2GameFileCheckStatus.RUNNING) {
            add(PageWidgetRegistration("sts2_windows_game_files_check_progress", PageIds.HOME, "home_current_game", STS2_TEMPLATE_ID, 17, target, widget = PageWidgetProgressCard(
                title = PageTextDirect(windowsLocalized(language, "windows.steam.0131")),
                subtitle = PageTextDirect(gameFileState.currentFilePath ?: gameFileState.message),
                progress = PageProgressRegistration(
                    fraction = checkProgressFraction(gameFileState),
                    label = checkProgressText(language, gameFileState)?.let(::PageTextDirect),
                    supportingText = gameFileState.currentFilePath?.let { PageTextDirect(gameFileState.message) },
                ),
                tone = PageWidgetTone.ACCENT,
            )))
        }
        if (downloadSnapshot.status == Sts2GameFileDownloadStatus.RUNNING) {
            add(PageWidgetRegistration("sts2_windows_game_files_download_progress", PageIds.HOME, "home_current_game", STS2_TEMPLATE_ID, 18, target, widget = PageWidgetProgressCard(
                title = PageTextDirect(windowsLocalized(language, "windows.steam.0132")),
                subtitle = PageTextDirect(downloadSnapshot.currentFilePath ?: downloadSnapshot.message),
                progress = PageProgressRegistration(
                    fraction = downloadProgressFraction(downloadSnapshot),
                    label = downloadProgressText(language, downloadSnapshot)?.let(::PageTextDirect),
                    supportingText = buildProgressSupportingText(downloadSnapshot.currentFilePath?.let { downloadSnapshot.message }, currentFileDownloadProgressText(language, downloadSnapshot))?.let(::PageTextDirect),
                ),
                tone = PageWidgetTone.ACCENT,
            )))
        }
    }
}

private fun canRunGameFileCheck(
    selectedVersion: Sts2VersionDefinition?,
    steamVerificationEnabled: Boolean,
    selectedSteamAccount: LauncherAccount?,
    gameFileState: WindowsGameFilePanelState,
    downloadSnapshot: Sts2GameFileDownloadSnapshot,
): Boolean {
    if (selectedVersion == null || selectedVersion.gameDirectory.trim().isBlank()) return false
    if (
        gameFileState.status == Sts2GameFileCheckStatus.RUNNING ||
        downloadSnapshot.status.blocksGameFileCheck()
    ) {
        return false
    }
    return if (steamVerificationEnabled) {
        selectedSteamAccount != null
    } else {
        true
    }
}

private fun canDownloadGameFiles(
    selectedVersion: Sts2VersionDefinition?,
    selectedSteamAccount: LauncherAccount?,
    gameFileState: WindowsGameFilePanelState,
    downloadSnapshot: Sts2GameFileDownloadSnapshot,
): Boolean {
    if (selectedVersion == null || selectedVersion.gameDirectory.trim().isBlank()) return false
    if (selectedSteamAccount == null) return false
    if (
        gameFileState.status.blocksGameFileDownload() ||
        downloadSnapshot.status == Sts2GameFileDownloadStatus.RUNNING
    ) {
        return false
    }
    return true
}

private fun Sts2GameFileCheckStatus.blocksGameFileDownload(): Boolean {
    return this == Sts2GameFileCheckStatus.RUNNING
}

private fun Sts2GameFileDownloadStatus.blocksGameFileCheck(): Boolean {
    return this == Sts2GameFileDownloadStatus.RUNNING ||
        this == Sts2GameFileDownloadStatus.PAUSED
}
