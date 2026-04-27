package com.dreamyloong.template.sts2

import com.dreamyloong.tlauncher.sdk.extension.ExtensionHostPaths
import com.dreamyloong.tlauncher.sdk.extension.ExtensionStateStore
import com.dreamyloong.tlauncher.sdk.i18n.SupportedLanguage
import com.dreamyloong.tlauncher.sdk.model.GameInstanceId
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipInputStream

enum class Sts2ModImportStatusKind {
    SUCCESS,
    WARNING,
    ERROR,
}

enum class Sts2ModPendingReplacementKind {
    MOD_PACKAGE,
    DLL,
    PCK,
}

data class Sts2ModImportStatus(
    val kind: Sts2ModImportStatusKind,
    val sourceFileName: String,
    val modId: String?,
    val targetDirectoryPath: String?,
    val importedFileNames: List<String>,
    val missingFileNames: List<String>,
    val detail: String,
    val updatedAtMillis: Long,
)

data class Sts2ModPendingReplacement(
    val kind: Sts2ModPendingReplacementKind,
    val sourceFileName: String,
    val modId: String,
    val manifestVersion: String?,
    val existingVersion: String?,
    val targetDirectoryPath: String,
    val stagedDirectoryPath: String,
    val stagedFileNames: List<String>,
    val replacingFileNames: List<String>,
    val addingFileNames: List<String>,
    val missingFileNames: List<String>,
    val detail: String,
    val updatedAtMillis: Long,
)

class Sts2ModImportCoordinator(
    stateStore: ExtensionStateStore,
    private val hostPaths: ExtensionHostPaths,
) {
    private val persistentStore = PersistentSts2ModImportStateStore(stateStore)

    fun status(instanceId: GameInstanceId): Sts2ModImportStatus? = persistentStore.status(instanceId)

    fun pendingReplacement(instanceId: GameInstanceId): Sts2ModPendingReplacement? {
        return persistentStore.pendingReplacement(instanceId)
    }

    fun importPickedFile(
        instanceId: GameInstanceId,
        language: SupportedLanguage,
        selectedVersion: Sts2VersionDefinition?,
        pickedFileName: String,
        bytes: ByteArray,
    ): Boolean {
        discardPendingReplacement(instanceId)
        val cleanName = File(pickedFileName).name.ifBlank { "imported-mod" }
        val result = runCatching {
            when {
                cleanName.endsWith(".json", ignoreCase = true) -> importJson(language, selectedVersion, cleanName, bytes)
                cleanName.endsWith(".zip", ignoreCase = true) || looksLikeZip(bytes) -> {
                    importZip(language, selectedVersion, cleanName, bytes)
                }

                else -> throw IllegalArgumentException(
                    modImportLocalized(language, "common.mod_import.0001"),
                )
            }
        }.getOrElse { error ->
            Sts2ModImportProcessResult(
                status = Sts2ModImportStatus(
                    kind = Sts2ModImportStatusKind.ERROR,
                    sourceFileName = cleanName,
                    modId = null,
                    targetDirectoryPath = selectedVersion?.modDirectory?.trim()?.ifBlank { null },
                    importedFileNames = emptyList(),
                    missingFileNames = emptyList(),
                    detail = error.message ?: error::class.java.simpleName,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
                pendingReplacement = null,
            )
        }
        persistentStore.writeStatus(instanceId, result.status)
        persistentStore.writePendingReplacement(instanceId, result.pendingReplacement)
        return result.status.kind != Sts2ModImportStatusKind.ERROR
    }

    fun importArtifact(
        instanceId: GameInstanceId,
        language: SupportedLanguage,
        mod: Sts2ScannedMod,
        artifactKind: Sts2ModPendingReplacementKind,
        pickedFileName: String,
        bytes: ByteArray,
    ): Boolean {
        discardPendingReplacement(instanceId)
        val cleanName = File(pickedFileName).name.ifBlank {
            when (artifactKind) {
                Sts2ModPendingReplacementKind.DLL -> "${mod.manifest.id}.dll"
                Sts2ModPendingReplacementKind.PCK -> "${mod.manifest.id}.pck"
                Sts2ModPendingReplacementKind.MOD_PACKAGE -> "${mod.manifest.id}.json"
            }
        }
        val result = runCatching {
            prepareArtifactImport(language, mod, artifactKind, cleanName, bytes)
        }.getOrElse { error ->
            Sts2ModImportProcessResult(
                status = Sts2ModImportStatus(
                    kind = Sts2ModImportStatusKind.ERROR,
                    sourceFileName = cleanName,
                    modId = mod.manifest.id,
                    targetDirectoryPath = mod.modRootPath,
                    importedFileNames = emptyList(),
                    missingFileNames = emptyList(),
                    detail = error.message ?: error::class.java.simpleName,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
                pendingReplacement = null,
            )
        }
        persistentStore.writeStatus(instanceId, result.status)
        persistentStore.writePendingReplacement(instanceId, result.pendingReplacement)
        return result.status.kind != Sts2ModImportStatusKind.ERROR
    }

    fun confirmPendingReplacement(
        instanceId: GameInstanceId,
        language: SupportedLanguage,
    ): Boolean {
        val pendingReplacement = persistentStore.pendingReplacement(instanceId) ?: return false
        val status = runCatching {
            applyPendingReplacement(language, pendingReplacement)
        }.getOrElse { error ->
            Sts2ModImportStatus(
                kind = Sts2ModImportStatusKind.ERROR,
                sourceFileName = pendingReplacement.sourceFileName,
                modId = pendingReplacement.modId,
                targetDirectoryPath = pendingReplacement.targetDirectoryPath,
                importedFileNames = emptyList(),
                missingFileNames = emptyList(),
                detail = error.message ?: error::class.java.simpleName,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        deleteDirectoryQuietly(File(pendingReplacement.stagedDirectoryPath))
        persistentStore.writePendingReplacement(instanceId, null)
        persistentStore.writeStatus(instanceId, status)
        return status.kind != Sts2ModImportStatusKind.ERROR
    }

    fun cancelPendingReplacement(
        instanceId: GameInstanceId,
        language: SupportedLanguage,
    ) {
        val pendingReplacement = persistentStore.pendingReplacement(instanceId) ?: return
        deleteDirectoryQuietly(File(pendingReplacement.stagedDirectoryPath))
        persistentStore.writePendingReplacement(instanceId, null)
        persistentStore.writeStatus(
            instanceId,
            Sts2ModImportStatus(
                kind = Sts2ModImportStatusKind.WARNING,
                sourceFileName = pendingReplacement.sourceFileName,
                modId = pendingReplacement.modId,
                targetDirectoryPath = pendingReplacement.targetDirectoryPath,
                importedFileNames = emptyList(),
                missingFileNames = emptyList(),
                detail = modImportLocalized(language, "common.mod_import.0002"),
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun discardPendingReplacement(instanceId: GameInstanceId) {
        persistentStore.pendingReplacement(instanceId)?.let { pendingReplacement ->
            deleteDirectoryQuietly(File(pendingReplacement.stagedDirectoryPath))
        }
        persistentStore.writePendingReplacement(instanceId, null)
    }

    private fun importJson(
        language: SupportedLanguage,
        selectedVersion: Sts2VersionDefinition?,
        cleanName: String,
        bytes: ByteArray,
    ): Sts2ModImportProcessResult {
        val manifest = runCatching {
            parseSts2ModManifest(bytes.toString(Charsets.UTF_8))
        }.getOrElse {
            throw IllegalArgumentException(corruptedModPackageMessage(language))
        }
        val modId = validateModId(language, manifest.id)
        val targetDirectory = prepareTargetDirectory(language, selectedVersion, modId)
        val existingManifestFile = findExistingManifestFile(targetDirectory, modId)
        val targetManifestFile = existingManifestFile ?: targetDirectory.resolve(cleanJsonFileName(cleanName, modId))
        val existingManifestVersion = existingManifestFile?.let(::readManifestVersion)
        val preparedImport = stagePreparedImport(
            language = language,
            sourceFileName = cleanName,
            modId = modId,
            manifestVersion = manifest.version,
            existingVersion = existingManifestVersion,
            targetDirectory = targetDirectory,
            files = listOf(PreparedImportFile(targetManifestFile.name, bytes)),
            missingFileNames = emptyList(),
        )
        return finalizePreparedModImport(
            language = language,
            preparedImport = preparedImport,
            importedMessage = modImportLocalized(language, "common.mod_import.0003"),
            pendingReplacementMessage = replacementPromptMessage(
                language = language,
                displayName = manifest.name?.trim()?.ifBlank { null } ?: modId,
                manifestVersion = manifest.version,
                existingVersion = existingManifestVersion,
                replacementTarget = modImportLocalized(language, "common.mod_import.0004"),
            ),
        )
    }

    private fun importZip(
        language: SupportedLanguage,
        selectedVersion: Sts2VersionDefinition?,
        cleanName: String,
        bytes: ByteArray,
    ): Sts2ModImportProcessResult {
        val extractionDirectory = createTempDirectory(language, "sts2/mod-import")
        try {
            runCatching { unzipToDirectory(bytes, extractionDirectory) }.getOrElse {
                throw IllegalArgumentException(corruptedModPackageMessage(language))
            }
            val scanResult = scanSts2ModDirectory(extractionDirectory)
            require(scanResult.mods.isNotEmpty()) { corruptedModPackageMessage(language) }
            require(scanResult.mods.size == 1) {
                modImportLocalized(language, "common.mod_import.0005")
            }
            val scannedMod = scanResult.mods.single()
            val manifest = scannedMod.manifest
            val modId = validateModId(language, manifest.id)
            val targetDirectory = prepareTargetDirectory(language, selectedVersion, modId)
            val existingManifestFile = findExistingManifestFile(targetDirectory, modId)
            val existingManifestVersion = existingManifestFile?.let(::readManifestVersion)
            val manifestTargetFile = existingManifestFile ?: targetDirectory.resolve(
                cleanJsonFileName(File(scannedMod.manifestFilePath).name, modId),
            )
            val preparedFiles = mutableListOf(
                PreparedImportFile(
                    targetFileName = manifestTargetFile.name,
                    bytes = File(scannedMod.manifestFilePath).readBytes(),
                ),
            )
            val missingFileNames = mutableListOf<String>()
            collectOptionalArtifact(extractionDirectory, "$modId.pck", manifest.hasPck, preparedFiles, missingFileNames)
            collectOptionalArtifact(extractionDirectory, "$modId.dll", manifest.hasDll, preparedFiles, missingFileNames)
            val preparedImport = stagePreparedImport(
                language = language,
                sourceFileName = cleanName,
                modId = modId,
                manifestVersion = manifest.version,
                existingVersion = existingManifestVersion,
                targetDirectory = targetDirectory,
                files = preparedFiles,
                missingFileNames = missingFileNames,
            )
            return finalizePreparedModImport(
                language = language,
                preparedImport = preparedImport,
                importedMessage = if (missingFileNames.isEmpty()) {
                    modImportLocalized(language, "common.mod_import.0006")
                } else {
                    modImportLocalized(language, "common.mod_import.0007")
                },
                pendingReplacementMessage = replacementPromptMessage(
                    language = language,
                    displayName = manifest.name?.trim()?.ifBlank { null } ?: modId,
                    manifestVersion = manifest.version,
                    existingVersion = existingManifestVersion,
                    replacementTarget = modImportLocalized(language, "common.mod_import.0008"),
                ),
            )
        } finally {
            deleteDirectoryQuietly(extractionDirectory)
        }
    }

    private fun prepareArtifactImport(
        language: SupportedLanguage,
        mod: Sts2ScannedMod,
        artifactKind: Sts2ModPendingReplacementKind,
        sourceFileName: String,
        bytes: ByteArray,
    ): Sts2ModImportProcessResult {
        val expectedExtension = when (artifactKind) {
            Sts2ModPendingReplacementKind.DLL -> ".dll"
            Sts2ModPendingReplacementKind.PCK -> ".pck"
            Sts2ModPendingReplacementKind.MOD_PACKAGE -> ".json"
        }
        require(sourceFileName.endsWith(expectedExtension, ignoreCase = true)) {
            modImportLocalized(
                language,
                when (artifactKind) {
                    Sts2ModPendingReplacementKind.DLL -> "common.mod_import.choose_dll"
                    Sts2ModPendingReplacementKind.PCK -> "common.mod_import.choose_pck"
                    Sts2ModPendingReplacementKind.MOD_PACKAGE -> "common.mod_import.choose_json"
                },
            )
        }
        val preparedImport = stagePreparedImport(
            language = language,
            sourceFileName = sourceFileName,
            modId = mod.manifest.id,
            manifestVersion = mod.manifest.version,
            existingVersion = mod.manifest.version,
            targetDirectory = File(mod.modRootPath),
            files = listOf(PreparedImportFile("${mod.manifest.id}$expectedExtension", bytes)),
            missingFileNames = emptyList(),
        )
        if (preparedImport.replacingFileNames.isNotEmpty()) {
            val pendingReplacement = preparedImport.toPendingReplacement(
                kind = artifactKind,
                detail = modImportLocalized(
                    language,
                    if (artifactKind == Sts2ModPendingReplacementKind.DLL) {
                        "common.mod_import.replace_existing_dll"
                    } else {
                        "common.mod_import.replace_existing_pck"
                    },
                ),
            )
            return Sts2ModImportProcessResult(
                status = Sts2ModImportStatus(
                    kind = Sts2ModImportStatusKind.WARNING,
                    sourceFileName = sourceFileName,
                    modId = mod.manifest.id,
                    targetDirectoryPath = mod.modRootPath,
                    importedFileNames = emptyList(),
                    missingFileNames = emptyList(),
                    detail = pendingReplacement.detail,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
                pendingReplacement = pendingReplacement,
            )
        }
        val status = applyPreparedImport(
            preparedImport = preparedImport,
            overwriteExisting = false,
            language = language,
            detail = if (artifactKind == Sts2ModPendingReplacementKind.DLL) {
                modImportLocalized(language, "common.mod_import.0009")
            } else {
                modImportLocalized(language, "common.mod_import.0010")
            },
        )
        return Sts2ModImportProcessResult(status = status, pendingReplacement = null)
    }

    private fun finalizePreparedModImport(
        language: SupportedLanguage,
        preparedImport: PreparedModImport,
        importedMessage: String,
        pendingReplacementMessage: String,
    ): Sts2ModImportProcessResult {
        return when {
            preparedImport.replacingFileNames.isEmpty() -> {
                val status = applyPreparedImport(preparedImport, false, language, importedMessage)
                Sts2ModImportProcessResult(status = status, pendingReplacement = null)
            }

            else -> {
                val pendingReplacement = preparedImport.toPendingReplacement(
                    kind = Sts2ModPendingReplacementKind.MOD_PACKAGE,
                    detail = pendingReplacementMessage,
                )
                Sts2ModImportProcessResult(
                    status = Sts2ModImportStatus(
                        kind = Sts2ModImportStatusKind.WARNING,
                        sourceFileName = preparedImport.sourceFileName,
                        modId = preparedImport.modId,
                        targetDirectoryPath = preparedImport.targetDirectory.absolutePath,
                        importedFileNames = emptyList(),
                        missingFileNames = preparedImport.missingFileNames,
                        detail = pendingReplacement.detail,
                        updatedAtMillis = System.currentTimeMillis(),
                    ),
                    pendingReplacement = pendingReplacement,
                )
            }
        }
    }

    private fun applyPreparedImport(
        preparedImport: PreparedModImport,
        overwriteExisting: Boolean,
        language: SupportedLanguage,
        detail: String,
    ): Sts2ModImportStatus {
        preparedImport.stagedFileNames.forEach { stagedFileName ->
            val stagedFile = preparedImport.stagedDirectory.resolve(stagedFileName)
            check(stagedFile.isFile) {
                modImportLocalized(language, "common.mod_import.0011")
            }
            val targetFile = preparedImport.targetDirectory.resolve(stagedFileName)
            if (!overwriteExisting && targetFile.exists()) {
                throw IllegalStateException(
                    modImportLocalized(language, "common.mod_import.0012"),
                )
            }
            stagedFile.copyTo(targetFile, overwrite = true)
        }
        deleteDirectoryQuietly(preparedImport.stagedDirectory)
        return Sts2ModImportStatus(
            kind = if (preparedImport.missingFileNames.isEmpty()) {
                Sts2ModImportStatusKind.SUCCESS
            } else {
                Sts2ModImportStatusKind.WARNING
            },
            sourceFileName = preparedImport.sourceFileName,
            modId = preparedImport.modId,
            targetDirectoryPath = preparedImport.targetDirectory.absolutePath,
            importedFileNames = preparedImport.stagedFileNames,
            missingFileNames = preparedImport.missingFileNames,
            detail = detail,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun applyPendingReplacement(
        language: SupportedLanguage,
        pendingReplacement: Sts2ModPendingReplacement,
    ): Sts2ModImportStatus {
        val stagedDirectory = File(pendingReplacement.stagedDirectoryPath)
        val targetDirectory = File(pendingReplacement.targetDirectoryPath)
        pendingReplacement.stagedFileNames.forEach { stagedFileName ->
            val stagedFile = stagedDirectory.resolve(stagedFileName)
            check(stagedFile.isFile) {
                modImportLocalized(language, "common.mod_import.0013")
            }
            stagedFile.copyTo(targetDirectory.resolve(stagedFileName), overwrite = true)
        }
        return Sts2ModImportStatus(
            kind = if (pendingReplacement.missingFileNames.isEmpty()) {
                Sts2ModImportStatusKind.SUCCESS
            } else {
                Sts2ModImportStatusKind.WARNING
            },
            sourceFileName = pendingReplacement.sourceFileName,
            modId = pendingReplacement.modId,
            targetDirectoryPath = pendingReplacement.targetDirectoryPath,
            importedFileNames = pendingReplacement.stagedFileNames,
            missingFileNames = pendingReplacement.missingFileNames,
            detail = when (pendingReplacement.kind) {
                Sts2ModPendingReplacementKind.MOD_PACKAGE -> {
                    val importedVersion = pendingReplacement.manifestVersion
                    val existingVersion = pendingReplacement.existingVersion
                    if (isSts2ModVersionUpdate(importedVersion, existingVersion)) {
                        modImportLocalized(language, "common.mod_import.0014", listOf(pendingReplacement.modId, existingVersion, importedVersion))
                    } else {
                        modImportLocalized(language, "common.mod_import.0015")
                    }
                }

                Sts2ModPendingReplacementKind.DLL -> modImportLocalized(language, "common.mod_import.0016")

                Sts2ModPendingReplacementKind.PCK -> modImportLocalized(language, "common.mod_import.0017")
            },
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun prepareTargetDirectory(
        language: SupportedLanguage,
        selectedVersion: Sts2VersionDefinition?,
        modId: String,
    ): File {
        require(selectedVersion != null) {
            modImportLocalized(language, "common.mod_import.0018")
        }
        val modDirectoryPath = selectedVersion.modDirectory.trim()
        require(modDirectoryPath.isNotBlank()) {
            modImportLocalized(language, "common.mod_import.0019")
        }
        val modDirectory = File(modDirectoryPath)
        if (!modDirectory.isDirectory) {
            modDirectory.mkdirs()
        }
        val targetDirectory = File(modDirectory, modId)
        targetDirectory.mkdirs()
        check(targetDirectory.isDirectory) {
            modImportLocalized(language, "common.mod_import.0020")
        }
        return targetDirectory
    }

    private fun stagePreparedImport(
        language: SupportedLanguage,
        sourceFileName: String,
        modId: String,
        manifestVersion: String?,
        existingVersion: String?,
        targetDirectory: File,
        files: List<PreparedImportFile>,
        missingFileNames: List<String>,
    ): PreparedModImport {
        val stagingDirectory = createTempDirectory(language, "sts2/mod-import-staging")
        files.forEach { file ->
            stagingDirectory.resolve(file.targetFileName).writeBytes(file.bytes)
        }
        return PreparedModImport(
            sourceFileName = sourceFileName,
            modId = modId,
            manifestVersion = manifestVersion?.trim()?.ifBlank { null },
            existingVersion = existingVersion?.trim()?.ifBlank { null },
            targetDirectory = targetDirectory,
            stagedDirectory = stagingDirectory,
            stagedFileNames = files.map { it.targetFileName },
            replacingFileNames = files.map { it.targetFileName }.filter { name -> targetDirectory.resolve(name).isFile },
            addingFileNames = files.map { it.targetFileName }.filterNot { name -> targetDirectory.resolve(name).isFile },
            missingFileNames = missingFileNames,
        )
    }

    private fun createTempDirectory(
        language: SupportedLanguage,
        relativeRootPath: String,
    ): File {
        val appFilesDirectory = hostPaths.appFilesDirectoryPath?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                modImportLocalized(language, "common.mod_import.0021"),
            )
        val tempRoot = File(appFilesDirectory, relativeRootPath)
        tempRoot.mkdirs()
        check(tempRoot.isDirectory) {
            modImportLocalized(language, "common.mod_import.0022")
        }
        val tempDirectory = File(tempRoot, UUID.randomUUID().toString())
        tempDirectory.mkdirs()
        check(tempDirectory.isDirectory) {
            modImportLocalized(language, "common.mod_import.0023")
        }
        return tempDirectory
    }
}

private class PersistentSts2ModImportStateStore(
    private val stateStore: ExtensionStateStore,
) {
    @Synchronized
    fun status(instanceId: GameInstanceId): Sts2ModImportStatus? {
        val values = readValues(statusKey(instanceId))
        if (values.isEmpty()) return null
        return Sts2ModImportStatus(
            kind = values["kind"]?.let(Sts2ModImportStatusKind::valueOf) ?: Sts2ModImportStatusKind.ERROR,
            sourceFileName = values["sourceFileName"].orEmpty(),
            modId = values["modId"]?.ifBlank { null },
            targetDirectoryPath = values["targetDirectoryPath"]?.ifBlank { null },
            importedFileNames = values["importedFileNames"]?.takeIf { it.isNotBlank() }?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            missingFileNames = values["missingFileNames"]?.takeIf { it.isNotBlank() }?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            detail = values["detail"].orEmpty(),
            updatedAtMillis = values["updatedAtMillis"]?.toLongOrNull() ?: 0L,
        )
    }

    @Synchronized
    fun writeStatus(
        instanceId: GameInstanceId,
        status: Sts2ModImportStatus?,
    ) {
        if (status == null) {
            stateStore.write(statusKey(instanceId), null)
            return
        }
        writeValues(
            statusKey(instanceId),
            mapOf(
                "kind" to status.kind.name,
                "sourceFileName" to status.sourceFileName,
                "modId" to (status.modId ?: ""),
                "targetDirectoryPath" to (status.targetDirectoryPath ?: ""),
                "importedFileNames" to status.importedFileNames.joinToString("\n"),
                "missingFileNames" to status.missingFileNames.joinToString("\n"),
                "detail" to status.detail,
                "updatedAtMillis" to status.updatedAtMillis.toString(),
            ),
        )
    }

    @Synchronized
    fun pendingReplacement(instanceId: GameInstanceId): Sts2ModPendingReplacement? {
        val values = readValues(pendingKey(instanceId))
        if (values.isEmpty()) return null
        return Sts2ModPendingReplacement(
            kind = values["kind"]?.let(Sts2ModPendingReplacementKind::valueOf) ?: Sts2ModPendingReplacementKind.MOD_PACKAGE,
            sourceFileName = values["sourceFileName"].orEmpty(),
            modId = values["modId"].orEmpty(),
            manifestVersion = values["manifestVersion"]?.ifBlank { null },
            existingVersion = values["existingVersion"]?.ifBlank { null },
            targetDirectoryPath = values["targetDirectoryPath"].orEmpty(),
            stagedDirectoryPath = values["stagedDirectoryPath"].orEmpty(),
            stagedFileNames = values["stagedFileNames"]?.takeIf { it.isNotBlank() }?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            replacingFileNames = values["replacingFileNames"]?.takeIf { it.isNotBlank() }?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            addingFileNames = values["addingFileNames"]?.takeIf { it.isNotBlank() }?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            missingFileNames = values["missingFileNames"]?.takeIf { it.isNotBlank() }?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            detail = values["detail"].orEmpty(),
            updatedAtMillis = values["updatedAtMillis"]?.toLongOrNull() ?: 0L,
        )
    }

    @Synchronized
    fun writePendingReplacement(
        instanceId: GameInstanceId,
        pendingReplacement: Sts2ModPendingReplacement?,
    ) {
        if (pendingReplacement == null) {
            stateStore.write(pendingKey(instanceId), null)
            return
        }
        writeValues(
            pendingKey(instanceId),
            mapOf(
                "kind" to pendingReplacement.kind.name,
                "sourceFileName" to pendingReplacement.sourceFileName,
                "modId" to pendingReplacement.modId,
                "manifestVersion" to (pendingReplacement.manifestVersion ?: ""),
                "existingVersion" to (pendingReplacement.existingVersion ?: ""),
                "targetDirectoryPath" to pendingReplacement.targetDirectoryPath,
                "stagedDirectoryPath" to pendingReplacement.stagedDirectoryPath,
                "stagedFileNames" to pendingReplacement.stagedFileNames.joinToString("\n"),
                "replacingFileNames" to pendingReplacement.replacingFileNames.joinToString("\n"),
                "addingFileNames" to pendingReplacement.addingFileNames.joinToString("\n"),
                "missingFileNames" to pendingReplacement.missingFileNames.joinToString("\n"),
                "detail" to pendingReplacement.detail,
                "updatedAtMillis" to pendingReplacement.updatedAtMillis.toString(),
            ),
        )
    }

    private fun statusKey(instanceId: GameInstanceId): String = "sts2.mod_import.status.${instanceId.value}"

    private fun pendingKey(instanceId: GameInstanceId): String = "sts2.mod_import.pending.${instanceId.value}"

    private fun readValues(key: String): Map<String, String> {
        return stateStore.read(key)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val delimiterIndex = line.indexOf('\t')
                if (delimiterIndex <= 0) null else line.substring(0, delimiterIndex) to decodeModImportStateValue(line.substring(delimiterIndex + 1))
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
            .joinToString("\n") { (name, value) -> "$name\t${encodeModImportStateValue(value)}" }
        stateStore.write(key, lines.ifBlank { null })
    }
}

private data class Sts2ModImportProcessResult(
    val status: Sts2ModImportStatus,
    val pendingReplacement: Sts2ModPendingReplacement?,
)

private data class PreparedImportFile(
    val targetFileName: String,
    val bytes: ByteArray,
)

private data class PreparedModImport(
    val sourceFileName: String,
    val modId: String,
    val manifestVersion: String?,
    val existingVersion: String?,
    val targetDirectory: File,
    val stagedDirectory: File,
    val stagedFileNames: List<String>,
    val replacingFileNames: List<String>,
    val addingFileNames: List<String>,
    val missingFileNames: List<String>,
) {
    fun toPendingReplacement(
        kind: Sts2ModPendingReplacementKind,
        detail: String,
    ): Sts2ModPendingReplacement {
        return Sts2ModPendingReplacement(
            kind = kind,
            sourceFileName = sourceFileName,
            modId = modId,
            manifestVersion = manifestVersion,
            existingVersion = existingVersion,
            targetDirectoryPath = targetDirectory.absolutePath,
            stagedDirectoryPath = stagedDirectory.absolutePath,
            stagedFileNames = stagedFileNames,
            replacingFileNames = replacingFileNames,
            addingFileNames = addingFileNames,
            missingFileNames = missingFileNames,
            detail = detail,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }
}

private fun unzipToDirectory(
    bytes: ByteArray,
    targetDirectory: File,
) {
    val rootPath = targetDirectory.canonicalFile.toPath()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zipInput ->
        while (true) {
            val entry = zipInput.nextEntry ?: break
            val normalizedName = entry.name.replace('\\', '/')
            if (normalizedName.isBlank()) {
                zipInput.closeEntry()
                continue
            }
            val targetFile = File(targetDirectory, normalizedName)
            val canonicalTarget = targetFile.canonicalFile
            if (!canonicalTarget.toPath().startsWith(rootPath)) {
                throw IOException("Archive entry escapes the temporary directory.")
            }
            if (entry.isDirectory) {
                canonicalTarget.mkdirs()
            } else {
                canonicalTarget.parentFile?.mkdirs()
                canonicalTarget.outputStream().use { output -> zipInput.copyTo(output) }
            }
            zipInput.closeEntry()
        }
    }
}

private fun collectOptionalArtifact(
    extractionRoot: File,
    expectedFileName: String,
    required: Boolean,
    preparedFiles: MutableList<PreparedImportFile>,
    missingFileNames: MutableList<String>,
) {
    if (!required) return
    val sourceFile = extractionRoot.walkTopDown()
        .filter { file -> file.isFile && file.name.equals(expectedFileName, ignoreCase = true) }
        .minByOrNull { file -> file.absolutePath.length }
    if (sourceFile == null) {
        missingFileNames += expectedFileName
        return
    }
    preparedFiles += PreparedImportFile(expectedFileName, sourceFile.readBytes())
}

private fun findExistingManifestFile(
    targetDirectory: File,
    modId: String,
): File? {
    val jsonFiles = targetDirectory.listFiles()
        .orEmpty()
        .filter { file -> file.isFile && file.name.endsWith(".json", ignoreCase = true) }
        .sortedBy { file -> file.name.lowercase() }
    return jsonFiles.firstOrNull { file ->
        runCatching { parseSts2ModManifest(file.readText()).id == modId }.getOrDefault(false)
    } ?: jsonFiles.firstOrNull { file ->
        file.name.equals("$modId.json", ignoreCase = true)
    }
}

private fun readManifestVersion(manifestFile: File): String? {
    return runCatching {
        parseSts2ModManifest(manifestFile.readText()).version?.trim()?.ifBlank { null }
    }.getOrNull()
}

fun isSts2ModVersionUpdate(
    incomingVersion: String?,
    existingVersion: String?,
): Boolean {
    return incomingVersion != null &&
        existingVersion != null &&
        compareModVersions(incomingVersion, existingVersion) > 0
}

private fun compareModVersions(
    left: String,
    right: String,
): Int {
    return ComparableModVersion.parse(left).compareTo(ComparableModVersion.parse(right))
}

private data class ComparableModVersion(
    val releaseParts: List<VersionIdentifier>,
    val preReleaseParts: List<VersionIdentifier>,
) : Comparable<ComparableModVersion> {
    override fun compareTo(other: ComparableModVersion): Int {
        val releaseSize = maxOf(releaseParts.size, other.releaseParts.size)
        repeat(releaseSize) { index ->
            val left = releaseParts.getOrNull(index) ?: VersionIdentifier.Zero
            val right = other.releaseParts.getOrNull(index) ?: VersionIdentifier.Zero
            val comparison = left.compareTo(right)
            if (comparison != 0) return comparison
        }
        if (preReleaseParts.isEmpty() && other.preReleaseParts.isEmpty()) return 0
        if (preReleaseParts.isEmpty()) return 1
        if (other.preReleaseParts.isEmpty()) return -1
        val prereleaseSize = maxOf(preReleaseParts.size, other.preReleaseParts.size)
        repeat(prereleaseSize) { index ->
            val left = preReleaseParts.getOrNull(index) ?: return -1
            val right = other.preReleaseParts.getOrNull(index) ?: return 1
            val comparison = left.compareTo(right)
            if (comparison != 0) return comparison
        }
        return 0
    }

    companion object {
        fun parse(raw: String): ComparableModVersion {
            val normalized = raw.trim().removePrefix("v").removePrefix("V").substringBefore("+")
            val releaseText = normalized.substringBefore("-")
            val preReleaseText = normalized.substringAfter("-", "")
            return ComparableModVersion(
                releaseParts = releaseText.split('.', '_').filter { it.isNotBlank() }.map(VersionIdentifier::parse),
                preReleaseParts = preReleaseText.split('.', '-', '_').filter { it.isNotBlank() }.map(VersionIdentifier::parse),
            )
        }
    }
}

private data class VersionIdentifier(
    val numericValue: Long?,
    val textValue: String,
) : Comparable<VersionIdentifier> {
    override fun compareTo(other: VersionIdentifier): Int {
        return when {
            numericValue != null && other.numericValue != null -> numericValue.compareTo(other.numericValue)
            numericValue != null -> 1
            other.numericValue != null -> -1
            else -> textValue.compareTo(other.textValue)
        }
    }

    companion object {
        val Zero = VersionIdentifier(0, "0")

        fun parse(raw: String): VersionIdentifier {
            return VersionIdentifier(raw.toLongOrNull(), raw.lowercase())
        }
    }
}

private fun replacementPromptMessage(
    language: SupportedLanguage,
    displayName: String,
    manifestVersion: String?,
    existingVersion: String?,
    replacementTarget: String,
): String {
    return when {
        isSts2ModVersionUpdate(manifestVersion, existingVersion) -> modImportLocalized(language, "common.mod_import.0024", listOf(displayName, existingVersion, manifestVersion))

        manifestVersion != null && existingVersion != null -> modImportLocalized(language, "common.mod_import.0025", listOf(existingVersion, manifestVersion, replacementTarget))

        else -> modImportLocalized(
            language,
            "common.mod_import.replace_existing_named_target",
            listOf(replacementTarget),
        )
    }
}

private fun looksLikeZip(bytes: ByteArray): Boolean {
    return bytes.size >= 4 &&
        bytes[0] == 0x50.toByte() &&
        bytes[1] == 0x4B.toByte() &&
        bytes[2] == 0x03.toByte() &&
        bytes[3] == 0x04.toByte()
}

private fun cleanJsonFileName(
    sourceFileName: String,
    modId: String,
): String {
    val cleanName = File(sourceFileName).name
    return if (cleanName.endsWith(".json", ignoreCase = true)) cleanName else "$modId.json"
}

private fun validateModId(
    language: SupportedLanguage,
    modId: String,
): String {
    val cleanId = modId.trim()
    require(cleanId.isNotBlank()) { corruptedModPackageMessage(language) }
    require(!cleanId.contains('/') && !cleanId.contains('\\')) { corruptedModPackageMessage(language) }
    require(cleanId != "." && cleanId != "..") { corruptedModPackageMessage(language) }
    return cleanId
}

private fun deleteDetectedArtifact(
    mod: Sts2ScannedMod,
    fileName: String,
    detectedState: Int,
) {
    if (detectedState != STS2_MOD_ARTIFACT_DETECTED) return
    val targetFile = File(mod.modRootPath, fileName)
    if (targetFile.isFile) {
        targetFile.delete()
    }
}

private fun deleteDirectoryQuietly(directory: File) {
    if (!directory.exists()) return
    directory.walkBottomUp().forEach { file -> runCatching { file.delete() } }
}

private fun encodeModImportStateValue(value: String): String {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
}

private fun decodeModImportStateValue(value: String): String {
    return String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}

private fun modImportLocalized(
    language: SupportedLanguage,
    key: String,
): String = modImportLocalized(language, key, emptyList())

private fun modImportLocalized(
    language: SupportedLanguage,
    key: String,
    args: List<Any?>,
): String = sts2Localized(language, key, args)

private fun corruptedModPackageMessage(language: SupportedLanguage): String {
    return modImportLocalized(language, "common.mod_import.0026")
}
