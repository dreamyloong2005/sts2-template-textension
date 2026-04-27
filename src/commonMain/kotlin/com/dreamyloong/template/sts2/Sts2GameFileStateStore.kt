package com.dreamyloong.template.sts2

import com.dreamyloong.tlauncher.sdk.extension.ExtensionStateStore
import com.dreamyloong.tlauncher.sdk.model.GameInstanceId
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

class PersistentSts2GameFileStateStore(
    private val stateStore: ExtensionStateStore,
) {
    private val inMemoryCheckSnapshots = ConcurrentHashMap<String, Sts2GameFileCheckSnapshot>()
    private val inMemoryDownloadSnapshots = ConcurrentHashMap<String, Sts2GameFileDownloadSnapshot>()

    @Synchronized
    fun preferences(instanceId: GameInstanceId): Sts2GameFilePreferences {
        val values = readValues(settingsKey(instanceId))
        return Sts2GameFilePreferences(
            selectedSteamAccountSubjectId = values["selectedSteamAccountSubjectId"]?.ifBlank { null },
            steamVerificationEnabled = values["steamVerificationEnabled"]?.toBooleanStrictOrNull() ?: true,
            steamBranch = normalizeSteamBranch(values["steamBranch"].orEmpty()),
        )
    }

    @Synchronized
    fun updatePreferences(
        instanceId: GameInstanceId,
        transform: (Sts2GameFilePreferences) -> Sts2GameFilePreferences,
    ) {
        val updated = transform(preferences(instanceId))
        writeValues(
            settingsKey(instanceId),
            mapOf(
                "selectedSteamAccountSubjectId" to (updated.selectedSteamAccountSubjectId ?: ""),
                "steamVerificationEnabled" to updated.steamVerificationEnabled.toString(),
                "steamBranch" to normalizeSteamBranch(updated.steamBranch),
            ),
        )
    }

    @Synchronized
    fun steamVerificationRecord(
        instanceId: GameInstanceId,
        version: Sts2VersionDefinition?,
        steamBranch: String,
    ): Sts2SteamVerificationRecord {
        val normalizedVersion = version?.normalizedGameFileVersion()
        val fallback = unverifiedSteamVerificationRecord(normalizedVersion, steamBranch)
        val clientId = normalizedVersion?.clientId ?: return fallback
        val values = readValues(steamVerificationKey(instanceId, clientId))
        val record = Sts2SteamVerificationRecord(
            status = values["status"]
                ?.let { value -> runCatching { Sts2SteamVerificationStatus.valueOf(value) }.getOrNull() }
                ?: Sts2SteamVerificationStatus.UNVERIFIED,
            versionClientId = values["versionClientId"]?.toIntOrNull() ?: normalizedVersion.clientId,
            gameDirectory = values["gameDirectory"]?.ifBlank { null } ?: normalizedVersion.gameDirectory,
            steamBranch = normalizeSteamBranch(values["steamBranch"].orEmpty()),
            message = values["message"].orEmpty(),
            checkedAtMillis = values["checkedAtMillis"]?.toLongOrNull(),
        )
        return if (record.matches(normalizedVersion, steamBranch)) record else fallback
    }

    @Synchronized
    fun writeSteamVerificationRecord(
        instanceId: GameInstanceId,
        snapshot: Sts2GameFileCheckSnapshot,
    ) {
        if (snapshot.mode != Sts2GameFileCheckMode.STEAM) {
            return
        }
        val clientId = snapshot.versionClientId ?: return
        val status = when {
            snapshot.status == Sts2GameFileCheckStatus.COMPLETED && snapshot.passed -> Sts2SteamVerificationStatus.PASSED
            snapshot.status == Sts2GameFileCheckStatus.COMPLETED || snapshot.status == Sts2GameFileCheckStatus.FAILED -> Sts2SteamVerificationStatus.FAILED
            else -> return
        }
        writeValues(
            steamVerificationKey(instanceId, clientId),
            mapOf(
                "status" to status.name,
                "versionClientId" to clientId.toString(),
                "gameDirectory" to (snapshot.gameDirectory ?: ""),
                "steamBranch" to normalizeSteamBranch(snapshot.steamBranch),
                "message" to snapshot.message,
                "checkedAtMillis" to (snapshot.checkedAtMillis ?: System.currentTimeMillis()).toString(),
            ),
        )
    }

    @Synchronized
    fun steamRepairPlanRecord(
        instanceId: GameInstanceId,
        version: Sts2VersionDefinition?,
        steamBranch: String,
        steamManifestId: String,
    ): Sts2SteamRepairPlanRecord {
        val normalizedVersion = version?.normalizedGameFileVersion()
        val fallback = unverifiedSteamRepairPlanRecord(normalizedVersion, steamBranch, steamManifestId)
        val clientId = normalizedVersion?.clientId ?: return fallback
        val values = readValues(steamRepairPlanKey(instanceId, clientId))
        val record = Sts2SteamRepairPlanRecord(
            versionClientId = values["versionClientId"]?.toIntOrNull() ?: normalizedVersion.clientId,
            gameDirectory = values["gameDirectory"]?.ifBlank { null } ?: normalizedVersion.gameDirectory,
            steamBranch = normalizeSteamBranch(values["steamBranch"].orEmpty()),
            steamManifestId = values["steamManifestId"]?.ifBlank { null },
            steamManifestPath = values["steamManifestPath"]?.ifBlank { null },
            steamDepotKeyHex = values["steamDepotKeyHex"]?.ifBlank { null },
            repairFilePaths = values["repairFilePaths"]
                ?.takeIf { it.isNotBlank() }
                ?.split('\n')
                ?.filter { it.isNotBlank() }
                .orEmpty(),
            expectedFileCount = values["expectedFileCount"]?.toIntOrNull(),
            localFileCount = values["localFileCount"]?.toIntOrNull(),
            missingFileCount = values["missingFileCount"]?.toIntOrNull(),
            mismatchedFileCount = values["mismatchedFileCount"]?.toIntOrNull(),
            checkedAtMillis = values["checkedAtMillis"]?.toLongOrNull(),
        )
        return if (record.matches(normalizedVersion, steamBranch, steamManifestId)) record else fallback
    }

    @Synchronized
    fun writeSteamRepairPlanRecord(
        instanceId: GameInstanceId,
        snapshot: Sts2GameFileCheckSnapshot,
    ) {
        if (snapshot.mode != Sts2GameFileCheckMode.STEAM || snapshot.status != Sts2GameFileCheckStatus.COMPLETED) {
            return
        }
        val clientId = snapshot.versionClientId ?: return
        val steamManifestId = snapshot.steamManifestId?.takeIf { it.isNotBlank() } ?: return
        val steamManifestPath = snapshot.steamManifestPath?.takeIf { it.isNotBlank() } ?: return
        val steamDepotKeyHex = snapshot.steamDepotKeyHex?.takeIf { it.isNotBlank() } ?: return
        writeValues(
            steamRepairPlanKey(instanceId, clientId),
            mapOf(
                "versionClientId" to clientId.toString(),
                "gameDirectory" to (snapshot.gameDirectory ?: ""),
                "steamBranch" to normalizeSteamBranch(snapshot.steamBranch),
                "steamManifestId" to steamManifestId,
                "steamManifestPath" to steamManifestPath,
                "steamDepotKeyHex" to steamDepotKeyHex,
                "repairFilePaths" to snapshot.repairFilePaths.joinToString("\n"),
                "expectedFileCount" to (snapshot.expectedFileCount?.toString() ?: ""),
                "localFileCount" to (snapshot.localFileCount?.toString() ?: ""),
                "missingFileCount" to (snapshot.missingFileCount?.toString() ?: ""),
                "mismatchedFileCount" to (snapshot.mismatchedFileCount?.toString() ?: ""),
                "checkedAtMillis" to (snapshot.checkedAtMillis ?: System.currentTimeMillis()).toString(),
            ),
        )
    }

    @Synchronized
    fun checkSnapshot(instanceId: GameInstanceId): Sts2GameFileCheckSnapshot {
        inMemoryCheckSnapshots[instanceId.value]?.let { return it }
        val values = readValues(checkKey(instanceId))
        return Sts2GameFileCheckSnapshot(
            status = values["status"]?.let(Sts2GameFileCheckStatus::valueOf) ?: Sts2GameFileCheckStatus.IDLE,
            mode = values["mode"]?.let { value ->
                runCatching { Sts2GameFileCheckMode.valueOf(value) }.getOrNull()
            } ?: Sts2GameFileCheckMode.STEAM,
            versionClientId = values["versionClientId"]?.toIntOrNull(),
            gameDirectory = values["gameDirectory"]?.ifBlank { null },
            steamAccountSubjectId = values["steamAccountSubjectId"]?.ifBlank { null },
            steamBranch = normalizeSteamBranch(values["steamBranch"].orEmpty()),
            passed = values["passed"]?.toBooleanStrictOrNull() ?: false,
            message = values["message"].orEmpty(),
            targetPath = values["targetPath"]?.ifBlank { null },
            currentFilePath = values["currentFilePath"]?.ifBlank { null },
            expectedFileCount = values["expectedFileCount"]?.toIntOrNull(),
            localFileCount = values["localFileCount"]?.toIntOrNull(),
            checkedFileCount = values["checkedFileCount"]?.toIntOrNull(),
            okFileCount = values["okFileCount"]?.toIntOrNull(),
            missingFileCount = values["missingFileCount"]?.toIntOrNull(),
            mismatchedFileCount = values["mismatchedFileCount"]?.toIntOrNull(),
            sizeOnlyFileCount = values["sizeOnlyFileCount"]?.toIntOrNull(),
            extraFileCount = values["extraFileCount"]?.toIntOrNull(),
            problemFilesPreview = values["problemFilesPreview"]
                ?.takeIf { it.isNotBlank() }
                ?.split('\n')
                ?.filter { it.isNotBlank() }
                .orEmpty(),
            steamManifestId = values["steamManifestId"]?.ifBlank { null },
            steamManifestPath = values["steamManifestPath"]?.ifBlank { null },
            steamDepotKeyHex = values["steamDepotKeyHex"]?.ifBlank { null },
            repairFilePaths = values["repairFilePaths"]
                ?.takeIf { it.isNotBlank() }
                ?.split('\n')
                ?.filter { it.isNotBlank() }
                .orEmpty(),
            checkedAtMillis = values["checkedAtMillis"]?.toLongOrNull(),
        )
    }

    @Synchronized
    fun writeCheckSnapshot(
        instanceId: GameInstanceId,
        snapshot: Sts2GameFileCheckSnapshot,
    ) {
        inMemoryCheckSnapshots[instanceId.value] = snapshot
        if (snapshot.status == Sts2GameFileCheckStatus.RUNNING) {
            return
        }
        writeValues(
            checkKey(instanceId),
            mapOf(
                "status" to snapshot.status.name,
                "mode" to snapshot.mode.name,
                "versionClientId" to (snapshot.versionClientId?.toString() ?: ""),
                "gameDirectory" to (snapshot.gameDirectory ?: ""),
                "steamAccountSubjectId" to (snapshot.steamAccountSubjectId ?: ""),
                "steamBranch" to normalizeSteamBranch(snapshot.steamBranch),
                "passed" to snapshot.passed.toString(),
                "message" to snapshot.message,
                "targetPath" to (snapshot.targetPath ?: ""),
                "currentFilePath" to (snapshot.currentFilePath ?: ""),
                "expectedFileCount" to (snapshot.expectedFileCount?.toString() ?: ""),
                "localFileCount" to (snapshot.localFileCount?.toString() ?: ""),
                "checkedFileCount" to (snapshot.checkedFileCount?.toString() ?: ""),
                "okFileCount" to (snapshot.okFileCount?.toString() ?: ""),
                "missingFileCount" to (snapshot.missingFileCount?.toString() ?: ""),
                "mismatchedFileCount" to (snapshot.mismatchedFileCount?.toString() ?: ""),
                "sizeOnlyFileCount" to (snapshot.sizeOnlyFileCount?.toString() ?: ""),
                "extraFileCount" to (snapshot.extraFileCount?.toString() ?: ""),
                "problemFilesPreview" to snapshot.problemFilesPreview.joinToString("\n"),
                "steamManifestId" to (snapshot.steamManifestId ?: ""),
                "steamManifestPath" to (snapshot.steamManifestPath ?: ""),
                "steamDepotKeyHex" to (snapshot.steamDepotKeyHex ?: ""),
                "repairFilePaths" to snapshot.repairFilePaths.joinToString("\n"),
                "checkedAtMillis" to (snapshot.checkedAtMillis?.toString() ?: ""),
            ),
        )
    }

    @Synchronized
    fun clearCheckSnapshot(instanceId: GameInstanceId) {
        inMemoryCheckSnapshots.remove(instanceId.value)
        stateStore.write(checkKey(instanceId), null)
    }

    @Synchronized
    fun downloadSnapshot(instanceId: GameInstanceId): Sts2GameFileDownloadSnapshot {
        inMemoryDownloadSnapshots[instanceId.value]?.let { return it }
        val values = readValues(downloadKey(instanceId))
        return Sts2GameFileDownloadSnapshot(
            status = values["status"]?.let(Sts2GameFileDownloadStatus::valueOf) ?: Sts2GameFileDownloadStatus.IDLE,
            versionClientId = values["versionClientId"]?.toIntOrNull(),
            gameDirectory = values["gameDirectory"]?.ifBlank { null },
            steamAccountSubjectId = values["steamAccountSubjectId"]?.ifBlank { null },
            steamBranch = normalizeSteamBranch(values["steamBranch"].orEmpty()),
            message = values["message"].orEmpty(),
            currentFilePath = values["currentFilePath"]?.ifBlank { null },
            totalFileCount = values["totalFileCount"]?.toIntOrNull(),
            completedFileCount = values["completedFileCount"]?.toIntOrNull(),
            skippedFileCount = values["skippedFileCount"]?.toIntOrNull(),
            deletedExtraFileCount = values["deletedExtraFileCount"]?.toIntOrNull(),
            currentFileDownloadedBytes = values["currentFileDownloadedBytes"]?.toLongOrNull(),
            currentFileTotalBytes = values["currentFileTotalBytes"]?.toLongOrNull(),
            totalBytes = values["totalBytes"]?.toLongOrNull(),
            downloadedBytes = values["downloadedBytes"]?.toLongOrNull(),
            updatedAtMillis = values["updatedAtMillis"]?.toLongOrNull(),
        )
    }

    @Synchronized
    fun writeDownloadSnapshot(
        instanceId: GameInstanceId,
        snapshot: Sts2GameFileDownloadSnapshot,
    ) {
        inMemoryDownloadSnapshots[instanceId.value] = snapshot
        if (snapshot.status == Sts2GameFileDownloadStatus.RUNNING) {
            return
        }
        writeValues(
            downloadKey(instanceId),
            mapOf(
                "status" to snapshot.status.name,
                "versionClientId" to (snapshot.versionClientId?.toString() ?: ""),
                "gameDirectory" to (snapshot.gameDirectory ?: ""),
                "steamAccountSubjectId" to (snapshot.steamAccountSubjectId ?: ""),
                "steamBranch" to normalizeSteamBranch(snapshot.steamBranch),
                "message" to snapshot.message,
                "currentFilePath" to (snapshot.currentFilePath ?: ""),
                "totalFileCount" to (snapshot.totalFileCount?.toString() ?: ""),
                "completedFileCount" to (snapshot.completedFileCount?.toString() ?: ""),
                "skippedFileCount" to (snapshot.skippedFileCount?.toString() ?: ""),
                "deletedExtraFileCount" to (snapshot.deletedExtraFileCount?.toString() ?: ""),
                "currentFileDownloadedBytes" to (snapshot.currentFileDownloadedBytes?.toString() ?: ""),
                "currentFileTotalBytes" to (snapshot.currentFileTotalBytes?.toString() ?: ""),
                "totalBytes" to (snapshot.totalBytes?.toString() ?: ""),
                "downloadedBytes" to (snapshot.downloadedBytes?.toString() ?: ""),
                "updatedAtMillis" to (snapshot.updatedAtMillis?.toString() ?: ""),
            ),
        )
    }

    @Synchronized
    fun clearDownloadSnapshot(instanceId: GameInstanceId) {
        inMemoryDownloadSnapshots.remove(instanceId.value)
        stateStore.write(downloadKey(instanceId), null)
    }

    private fun settingsKey(instanceId: GameInstanceId): String = "sts2.game_files.settings.${instanceId.value}"

    private fun checkKey(instanceId: GameInstanceId): String = "sts2.game_files.check.${instanceId.value}"

    private fun downloadKey(instanceId: GameInstanceId): String = "sts2.game_files.download.${instanceId.value}"

    private fun steamVerificationKey(instanceId: GameInstanceId, clientId: Int): String {
        return "sts2.game_files.steam_verification.${instanceId.value}.$clientId"
    }

    private fun steamRepairPlanKey(instanceId: GameInstanceId, clientId: Int): String {
        return "sts2.game_files.steam_repair_plan.${instanceId.value}.$clientId"
    }

    private fun readValues(key: String): Map<String, String> {
        return stateStore.read(key)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val delimiterIndex = line.indexOf('\t')
                if (delimiterIndex <= 0) {
                    null
                } else {
                    val name = line.substring(0, delimiterIndex)
                    val value = decodeStateValue(line.substring(delimiterIndex + 1))
                    name to value
                }
            }
            ?.toMap()
            .orEmpty()
    }

    private fun writeValues(
        key: String,
        values: Map<String, String>,
    ) {
        val lines = values.entries
            .filter { (_, value) -> value.isNotEmpty() }
            .sortedBy { (name, _) -> name }
            .joinToString("\n") { (name, value) ->
                "$name\t${encodeStateValue(value)}"
            }
        stateStore.write(key, lines.ifBlank { null })
    }
}

private fun unverifiedSteamVerificationRecord(
    version: Sts2VersionDefinition?,
    steamBranch: String,
): Sts2SteamVerificationRecord {
    return Sts2SteamVerificationRecord(
        status = Sts2SteamVerificationStatus.UNVERIFIED,
        versionClientId = version?.clientId,
        gameDirectory = version?.gameDirectory?.trim(),
        steamBranch = normalizeSteamBranch(steamBranch),
    )
}

private fun unverifiedSteamRepairPlanRecord(
    version: Sts2VersionDefinition?,
    steamBranch: String,
    steamManifestId: String,
): Sts2SteamRepairPlanRecord {
    return Sts2SteamRepairPlanRecord(
        versionClientId = version?.clientId,
        gameDirectory = version?.gameDirectory?.trim(),
        steamBranch = normalizeSteamBranch(steamBranch),
        steamManifestId = steamManifestId,
    )
}

private fun encodeStateValue(value: String): String {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
}

private fun decodeStateValue(value: String): String {
    return String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}
