package com.dreamyloong.template.sts2

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Sts2ModScanResult(
    val modDirectoryPath: String,
    val modDirectoryConfigured: Boolean,
    val modDirectoryExists: Boolean,
    val mods: List<Sts2ScannedMod>,
    val problems: List<Sts2ModScanProblem>,
) {
    val affectedModCount: Int
        get() = mods.count { it.issues.isNotEmpty() }

    val totalProblemEntryCount: Int
        get() = affectedModCount + problems.size
}

data class Sts2ScannedMod(
    val manifest: Sts2ModManifest,
    val manifestFilePath: String,
    val modRootPath: String,
    val relativeManifestPath: String,
    val relativeModPath: String,
    val dllDetected: Int,
    val pckDetected: Int,
    val issues: List<Sts2ScannedModIssue>,
    val discoveryOrder: Int,
)

data class Sts2ModManifest(
    val id: String,
    val name: String?,
    val author: String?,
    val description: String?,
    val version: String?,
    val hasPck: Boolean,
    val hasDll: Boolean,
    val dependencies: List<String>,
    val affectsGameplay: Boolean,
)

data class Sts2ModScanProblem(
    val manifestFilePath: String,
    val relativeManifestPath: String,
    val reason: String,
)

sealed interface Sts2ScannedModIssue {
    data class DuplicateId(
        val id: String,
        val duplicateCount: Int,
    ) : Sts2ScannedModIssue

    data class MissingDependencies(
        val dependencies: List<String>,
    ) : Sts2ScannedModIssue

    data object MissingDll : Sts2ScannedModIssue

    data object MissingPck : Sts2ScannedModIssue

    data object NoRuntimeArtifactDeclared : Sts2ScannedModIssue
}

data class Sts2ModSettingsSnapshot(
    val settingsFilePath: String?,
    val globalModsEnabled: Boolean,
    val explicitStates: Map<String, Boolean>,
    val readError: String? = null,
)

data class Sts2ResolvedModEnabledState(
    val enabled: Boolean,
    val explicit: Boolean,
    val blockedByGlobalSwitch: Boolean,
    val settingsFilePath: String?,
    val readError: String? = null,
)

const val STS2_MOD_ARTIFACT_NOT_DETECTED: Int = 0
const val STS2_MOD_ARTIFACT_DETECTED: Int = 1
const val STS2_MOD_ARTIFACT_NOT_REQUIRED: Int = 2

fun scanSts2LocalMods(selectedVersion: Sts2VersionDefinition?): Sts2ModScanResult? {
    if (selectedVersion == null) {
        return null
    }
    val configuredDirectory = selectedVersion.modDirectory.trim()
    if (configuredDirectory.isBlank()) {
        return Sts2ModScanResult(
            modDirectoryPath = "",
            modDirectoryConfigured = false,
            modDirectoryExists = false,
            mods = emptyList(),
            problems = emptyList(),
        )
    }
    val modDirectory = File(configuredDirectory)
    if (!modDirectory.isDirectory) {
        return Sts2ModScanResult(
            modDirectoryPath = modDirectory.absolutePath,
            modDirectoryConfigured = true,
            modDirectoryExists = false,
            mods = emptyList(),
            problems = emptyList(),
        )
    }

    return scanSts2ModDirectory(modDirectory)
}

fun scanSts2ModDirectory(modDirectory: File): Sts2ModScanResult {
    val rawMods = mutableListOf<RawScannedMod>()
    val problems = mutableListOf<Sts2ModScanProblem>()
    val discoveryCounter = intArrayOf(0)
    readModsInDirRecursive(
        rootDirectory = modDirectory,
        currentDirectory = modDirectory,
        rawMods = rawMods,
        problems = problems,
        discoveryCounter = discoveryCounter,
    )

    val duplicateCounts = rawMods.groupingBy { it.manifest.id }.eachCount()
    val loadedIds = rawMods.mapTo(linkedSetOf()) { it.manifest.id }

    val mods = rawMods.map { scanned ->
        val dllDetected = detectModArtifactState(
            modRootPath = scanned.modRootPath,
            modId = scanned.manifest.id,
            extension = ".dll",
            required = scanned.manifest.hasDll,
        )
        val pckDetected = detectModArtifactState(
            modRootPath = scanned.modRootPath,
            modId = scanned.manifest.id,
            extension = ".pck",
            required = scanned.manifest.hasPck,
        )
        val issues = buildList {
            val duplicateCount = duplicateCounts[scanned.manifest.id] ?: 0
            if (duplicateCount > 1) {
                add(
                    Sts2ScannedModIssue.DuplicateId(
                        id = scanned.manifest.id,
                        duplicateCount = duplicateCount,
                    ),
                )
            }
            val missingDependencies = scanned.manifest.dependencies.filterNot(loadedIds::contains)
            if (missingDependencies.isNotEmpty()) {
                add(Sts2ScannedModIssue.MissingDependencies(missingDependencies))
            }
            if (!scanned.manifest.hasDll && !scanned.manifest.hasPck) {
                add(Sts2ScannedModIssue.NoRuntimeArtifactDeclared)
            }
            if (dllDetected == STS2_MOD_ARTIFACT_NOT_DETECTED) {
                add(Sts2ScannedModIssue.MissingDll)
            }
            if (pckDetected == STS2_MOD_ARTIFACT_NOT_DETECTED) {
                add(Sts2ScannedModIssue.MissingPck)
            }
        }
        Sts2ScannedMod(
            manifest = scanned.manifest,
            manifestFilePath = scanned.manifestFilePath,
            modRootPath = scanned.modRootPath,
            relativeManifestPath = scanned.relativeManifestPath,
            relativeModPath = scanned.relativeModPath,
            dllDetected = dllDetected,
            pckDetected = pckDetected,
            issues = issues,
            discoveryOrder = scanned.discoveryOrder,
        )
    }.sortedBy { mod -> mod.discoveryOrder }

    return Sts2ModScanResult(
        modDirectoryPath = modDirectory.absolutePath,
        modDirectoryConfigured = true,
        modDirectoryExists = true,
        mods = mods,
        problems = problems.sortedBy { problem -> problem.relativeManifestPath },
    )
}

fun parseSts2ModManifest(jsonText: String): Sts2ModManifest {
    val json = JSONObject(normalizeJsonText(jsonText))
    val modId = json.optString("modid").trim().ifBlank {
        json.optString("id").trim()
    }.ifBlank {
        throw IllegalArgumentException("Missing required field 'modid' or 'id'.")
    }
    return Sts2ModManifest(
        id = modId,
        name = json.optString("name").trim().ifBlank { null },
        author = json.optString("author").trim().ifBlank { null },
        description = json.optString("description").trim().ifBlank { null },
        version = json.optString("version").trim().ifBlank { null },
        hasPck = json.optOptionalBoolean("hasPck", fallbackKey = "has_pck") ?: false,
        hasDll = json.optOptionalBoolean("hasDll", fallbackKey = "has_dll") ?: false,
        dependencies = json.optStringArray("dependencies"),
        affectsGameplay = json.optOptionalBoolean(
            primaryKey = "affectsGameplay",
            fallbackKey = "affects_gameplay",
        ) ?: true,
    )
}

fun readSts2ModSettingsSnapshot(selectedVersion: Sts2VersionDefinition?): Sts2ModSettingsSnapshot {
    val saveDirectoryPath = selectedVersion?.saveDirectory?.trim().orEmpty()
    if (saveDirectoryPath.isBlank()) {
        return Sts2ModSettingsSnapshot(
            settingsFilePath = null,
            globalModsEnabled = true,
            explicitStates = emptyMap(),
        )
    }
    val settingsFile = File(saveDirectoryPath, "settings.save")
    if (!settingsFile.isFile) {
        return Sts2ModSettingsSnapshot(
            settingsFilePath = settingsFile.absolutePath,
            globalModsEnabled = true,
            explicitStates = emptyMap(),
        )
    }
    return runCatching {
        val root = JSONObject(normalizeJsonText(settingsFile.readText()))
        val modSettings = root.optJSONObject("mod_settings")
        val modList = modSettings?.optJSONArray("mod_list")
        val explicitStates = buildMap {
            for (index in 0 until (modList?.length() ?: 0)) {
                val entry = modList?.optJSONObject(index) ?: continue
                val id = entry.optString("id").trim()
                if (id.isBlank()) continue
                put(id, entry.optBoolean("is_enabled", true))
            }
        }
        Sts2ModSettingsSnapshot(
            settingsFilePath = settingsFile.absolutePath,
            globalModsEnabled = modSettings?.optBoolean("mods_enabled", true) ?: true,
            explicitStates = explicitStates,
        )
    }.getOrElse { error ->
        Sts2ModSettingsSnapshot(
            settingsFilePath = settingsFile.absolutePath,
            globalModsEnabled = true,
            explicitStates = emptyMap(),
            readError = error.message ?: error::class.java.simpleName,
        )
    }
}

fun resolveSts2ModEnabledState(
    snapshot: Sts2ModSettingsSnapshot,
    modId: String,
): Sts2ResolvedModEnabledState {
    val explicitValue = snapshot.explicitStates[modId]
    return Sts2ResolvedModEnabledState(
        enabled = snapshot.globalModsEnabled && (explicitValue ?: true),
        explicit = explicitValue != null,
        blockedByGlobalSwitch = !snapshot.globalModsEnabled,
        settingsFilePath = snapshot.settingsFilePath,
        readError = snapshot.readError,
    )
}

fun updateSts2ModEnabledState(
    selectedVersion: Sts2VersionDefinition?,
    modId: String,
    enabled: Boolean,
): Boolean {
    val document = loadMutableModSettingsDocument(selectedVersion) ?: return false
    document.modSettings.put("mods_enabled", true)
    var updated = false
    for (index in 0 until document.modList.length()) {
        val entry = document.modList.optJSONObject(index) ?: continue
        if (entry.optString("id").trim() != modId) continue
        entry.put("is_enabled", enabled)
        if (entry.optString("source").isBlank()) {
            entry.put("source", "mods_directory")
        }
        updated = true
    }
    if (!updated) {
        document.modList.put(
            JSONObject()
                .put("id", modId)
                .put("is_enabled", enabled)
                .put("source", "mods_directory"),
        )
    }
    return saveMutableModSettingsDocument(document)
}

fun removeSts2ModSettingsEntry(
    selectedVersion: Sts2VersionDefinition?,
    modId: String,
) {
    val document = loadMutableModSettingsDocument(selectedVersion, allowMissing = true) ?: return
    var removed = false
    val filtered = JSONArray()
    for (index in 0 until document.modList.length()) {
        val entry = document.modList.opt(index)
        val entryObject = entry as? JSONObject
        if (entryObject != null && entryObject.optString("id").trim() == modId) {
            removed = true
            continue
        }
        filtered.put(entry)
    }
    if (!removed) {
        return
    }
    document.modSettings.put("mod_list", filtered)
    saveMutableModSettingsDocument(document)
}

fun deleteSts2ScannedMod(
    selectedVersion: Sts2VersionDefinition?,
    mod: Sts2ScannedMod,
): Boolean {
    val modDirectoryRoot = selectedVersion?.modDirectory?.trim()?.takeIf { it.isNotBlank() }?.let(::File)?.canonicalFile
        ?: return false
    val manifestFile = File(mod.manifestFilePath)
    if (!manifestFile.isFile || !manifestFile.delete()) {
        return false
    }
    deleteDetectedArtifact(mod, "${mod.manifest.id}.pck", mod.pckDetected)
    deleteDetectedArtifact(mod, "${mod.manifest.id}.dll", mod.dllDetected)
    val modRootDirectory = File(mod.modRootPath)
    if (modRootDirectory.isDirectory && modRootDirectory.canonicalPath != modDirectoryRoot.canonicalPath && modRootDirectory.listFiles().orEmpty().isEmpty()) {
        modRootDirectory.delete()
    }
    return true
}

private data class RawScannedMod(
    val manifest: Sts2ModManifest,
    val manifestFilePath: String,
    val modRootPath: String,
    val relativeManifestPath: String,
    val relativeModPath: String,
    val discoveryOrder: Int,
)

private data class MutableSts2ModSettingsDocument(
    val settingsFile: File,
    val root: JSONObject,
    val modSettings: JSONObject,
    val modList: JSONArray,
)

private sealed interface ManifestFileReadResult {
    data class Success(
        val manifest: Sts2ModManifest,
        val relativeManifestPath: String,
    ) : ManifestFileReadResult

    data class Problem(
        val problem: Sts2ModScanProblem,
    ) : ManifestFileReadResult
}

private fun readModsInDirRecursive(
    rootDirectory: File,
    currentDirectory: File,
    rawMods: MutableList<RawScannedMod>,
    problems: MutableList<Sts2ModScanProblem>,
    discoveryCounter: IntArray,
) {
    currentDirectory.listFiles()
        .orEmpty()
        .filter(File::isFile)
        .sortedBy { file -> file.name.lowercase() }
        .forEach { file ->
            if (!file.name.endsWith(".json", ignoreCase = true)) {
                return@forEach
            }
            when (val parseResult = readManifestFile(rootDirectory, file)) {
                is ManifestFileReadResult.Success -> {
                    val modRoot = file.parentFile ?: rootDirectory
                    rawMods += RawScannedMod(
                        manifest = parseResult.manifest,
                        manifestFilePath = file.absolutePath,
                        modRootPath = modRoot.absolutePath,
                        relativeManifestPath = parseResult.relativeManifestPath,
                        relativeModPath = relativePath(rootDirectory, modRoot),
                        discoveryOrder = discoveryCounter[0]++,
                    )
                }

                is ManifestFileReadResult.Problem -> {
                    problems += parseResult.problem
                }
            }
        }

    currentDirectory.listFiles()
        .orEmpty()
        .filter(File::isDirectory)
        .sortedBy { file -> file.name.lowercase() }
        .forEach { directory ->
            readModsInDirRecursive(
                rootDirectory = rootDirectory,
                currentDirectory = directory,
                rawMods = rawMods,
                problems = problems,
                discoveryCounter = discoveryCounter,
            )
        }
}

private fun readManifestFile(
    rootDirectory: File,
    manifestFile: File,
): ManifestFileReadResult {
    val relativeManifestPath = relativePath(rootDirectory, manifestFile)
    return runCatching {
        val manifest = parseSts2ModManifest(manifestFile.readText())
        ManifestFileReadResult.Success(
            manifest = manifest,
            relativeManifestPath = relativeManifestPath,
        )
    }.getOrElse { error ->
        ManifestFileReadResult.Problem(
            Sts2ModScanProblem(
                manifestFilePath = manifestFile.absolutePath,
                relativeManifestPath = relativeManifestPath,
                reason = error.message ?: error::class.java.simpleName,
            ),
        )
    }
}

private fun JSONObject.optStringArray(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotBlank()) {
                add(value)
            }
        }
    }
}

private fun JSONObject.optOptionalBoolean(
    primaryKey: String,
    fallbackKey: String? = null,
): Boolean? {
    if (has(primaryKey)) {
        return optBoolean(primaryKey)
    }
    if (fallbackKey != null && has(fallbackKey)) {
        return optBoolean(fallbackKey)
    }
    return null
}

private fun detectModArtifactState(
    modRootPath: String,
    modId: String,
    extension: String,
    required: Boolean,
): Int {
    if (!required) {
        return STS2_MOD_ARTIFACT_NOT_REQUIRED
    }
    return if (File(modRootPath, "$modId$extension").isFile) {
        STS2_MOD_ARTIFACT_DETECTED
    } else {
        STS2_MOD_ARTIFACT_NOT_DETECTED
    }
}

private fun deleteDetectedArtifact(
    mod: Sts2ScannedMod,
    fileName: String,
    detectionState: Int,
) {
    if (detectionState != STS2_MOD_ARTIFACT_DETECTED) {
        return
    }
    File(mod.modRootPath, fileName).takeIf(File::isFile)?.delete()
}

private fun loadMutableModSettingsDocument(
    selectedVersion: Sts2VersionDefinition?,
    allowMissing: Boolean = false,
): MutableSts2ModSettingsDocument? {
    val saveDirectoryPath = selectedVersion?.saveDirectory?.trim().orEmpty()
    if (saveDirectoryPath.isBlank()) {
        return null
    }
    val settingsFile = File(saveDirectoryPath, "settings.save")
    val root = when {
        settingsFile.isFile -> runCatching { JSONObject(normalizeJsonText(settingsFile.readText())) }.getOrNull() ?: return null
        allowMissing -> JSONObject()
        else -> JSONObject()
    }
    val modSettings = root.optJSONObject("mod_settings") ?: JSONObject().also { root.put("mod_settings", it) }
    val modList = modSettings.optJSONArray("mod_list") ?: JSONArray().also { modSettings.put("mod_list", it) }
    return MutableSts2ModSettingsDocument(
        settingsFile = settingsFile,
        root = root,
        modSettings = modSettings,
        modList = modList,
    )
}

private fun saveMutableModSettingsDocument(document: MutableSts2ModSettingsDocument): Boolean {
    return runCatching {
        document.settingsFile.parentFile?.mkdirs()
        document.settingsFile.writeText(document.root.toString(2))
    }.isSuccess
}

private fun relativePath(
    rootDirectory: File,
    target: File,
): String {
    return target.relativeToOrNull(rootDirectory)
        ?.invariantSeparatorsPath
        ?.ifBlank { "." }
        ?: target.absolutePath
}

private fun normalizeJsonText(text: String): String {
    return text.removePrefix("\uFEFF")
}
