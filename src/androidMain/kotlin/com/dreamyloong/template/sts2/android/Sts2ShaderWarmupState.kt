package com.dreamyloong.template.sts2.android

import com.dreamyloong.template.sts2.STS2_TEMPLATE_ID
import com.dreamyloong.template.sts2.Sts2VersionDefinition
import com.dreamyloong.tlauncher.sdk.extension.ExtensionStateStore
import com.dreamyloong.tlauncher.sdk.extension.ExtensionHostPaths
import com.dreamyloong.tlauncher.sdk.model.GameInstanceId
import org.json.JSONObject
import java.io.File

internal const val STS2_LAUNCH_MODE_GAME = "game"
internal const val STS2_LAUNCH_MODE_SHADER_WARMUP = "shaderWarmup"

private const val SHADER_WARMUP_MARKER_VERSION = 1
private const val SHADER_PATCH_SET_VERSION = 1
private const val SHADER_WARMUP_MARKER_DIRECTORY = ".tlauncher"
private const val SHADER_WARMUP_MARKER_FILE = "shader_warmup.json"
private const val RELEASE_INFO_FILE = "release_info.json"
private const val PACK_FILE = "SlayTheSpire2.pck"
private const val SHADER_CACHE_HOST_PREFIX = "sts2-"
private const val SHADER_CACHE_USER_DIR_PREFIX = "TLauncher_STS2_"
private const val ANDROID_EXTENSION_RUNTIME_DIRECTORY = "android-extension-runtime"
private const val PROJECT_HOST_DIRECTORY = "project-host"

internal data class Sts2ShaderCacheEntry(
    val key: String,
    val releaseVersion: String?,
    val versionDisplayName: String?,
    val hostProjectDirectoryPath: String?,
    val godotUserDirectoryPath: String?,
    val markerFilePath: String?,
    val fileCount: Int,
    val sizeBytes: Long,
    val lastModified: Long,
    val isCurrent: Boolean,
)

internal class Sts2AndroidShaderCacheStore(
    private val stateStore: ExtensionStateStore,
) {
    fun isEnabled(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Boolean {
        return stateStore.read(key(instanceId, clientId))?.toBooleanStrictOrNull() ?: false
    }

    fun setEnabled(
        instanceId: GameInstanceId,
        clientId: Int,
        enabled: Boolean,
    ) {
        stateStore.write(key(instanceId, clientId), enabled.toString())
    }

    private fun key(
        instanceId: GameInstanceId,
        clientId: Int,
    ): String {
        return "sts2.android.shaderCache.${instanceId.value}.$clientId"
    }
}

internal fun sts2ShaderWarmupNeedsRun(version: Sts2VersionDefinition): Boolean {
    return sts2ShaderWarmupNeedsRun(
        version = version,
        requiredCacheDirectory = null,
    )
}

internal fun sts2ShaderWarmupNeedsRun(
    version: Sts2VersionDefinition,
    hostPaths: ExtensionHostPaths,
): Boolean {
    return sts2ShaderWarmupNeedsRun(
        version = version,
        requiredCacheDirectory = sts2ShaderCacheHostProjectDirectory(hostPaths, version),
    )
}

private fun sts2ShaderWarmupNeedsRun(
    version: Sts2VersionDefinition,
    requiredCacheDirectory: File?,
): Boolean {
    val gameDirectory = File(version.gameDirectory.trim())
    val marker = shaderWarmupMarkerFile(gameDirectory)
    if (requiredCacheDirectory != null && !requiredCacheDirectory.isDirectory) {
        deleteShaderWarmupMarker(marker)
        return true
    }
    if (!marker.isFile) return true

    val releaseVersion = readReleaseInfoVersion(gameDirectory)
    return runCatching {
        val markerJson = JSONObject(marker.readText())
        if (!sameMarkerValue(markerJson.optString("releaseVersion"), releaseVersion)) {
            deleteShaderWarmupMarker(marker)
            return@runCatching true
        }
        !(
            markerJson.optBoolean("success", false) &&
                markerJson.optInt("warmupVersion", 0) == SHADER_WARMUP_MARKER_VERSION &&
                markerJson.optInt("shaderPatchSetVersion", 0) == SHADER_PATCH_SET_VERSION &&
                markerJson.optString("renderer") == normalizeShaderWarmupRenderer(version.renderer) &&
                markerJson.optBoolean("mobileShadersEnabled", true) == version.mobileShadersEnabled
            )
    }.getOrElse {
        deleteShaderWarmupMarker(marker)
        true
    }
}

internal fun sts2ShaderCacheHostProjectKey(version: Sts2VersionDefinition): String {
    val gameDirectory = File(version.gameDirectory.trim())
    val releaseVersion = readReleaseInfoVersion(gameDirectory)
    return sts2ShaderCacheHostProjectKey(gameDirectory, releaseVersion)
}

internal fun listSts2ShaderCacheEntries(
    hostPaths: ExtensionHostPaths,
    versions: List<Sts2VersionDefinition>,
    selectedVersion: Sts2VersionDefinition?,
): List<Sts2ShaderCacheEntry> {
    val hostRoot = sts2ShaderCacheHostProjectRoot(hostPaths)
    val appFilesDirectory = appFilesDirectory(hostPaths)
    val selectedKey = selectedVersion
        ?.takeIf { version -> version.gameDirectory.trim().isNotBlank() }
        ?.let(::sts2ShaderCacheHostProjectKey)
    val knownVersions = versions
        .filter { version -> version.gameDirectory.trim().isNotBlank() }
        .map { version ->
            val gameDirectory = File(version.gameDirectory.trim())
            val releaseVersion = readReleaseInfoVersion(gameDirectory)
            val key = sts2ShaderCacheHostProjectKey(gameDirectory, releaseVersion)
            key to KnownShaderCacheVersion(
                version = version,
                releaseVersion = releaseVersion,
                markerFile = shaderWarmupMarkerFile(gameDirectory),
            )
        }
        .toMap()
    val keys = linkedSetOf<String>()
    hostRoot
        ?.listFiles()
        .orEmpty()
        .filter { file -> file.isDirectory }
        .forEach { directory -> keys += directory.name }
    appFilesDirectory
        ?.listFiles()
        .orEmpty()
        .filter { file -> file.isDirectory && file.name.startsWith(SHADER_CACHE_USER_DIR_PREFIX) }
        .forEach { directory -> keys += directory.name.removePrefix(SHADER_CACHE_USER_DIR_PREFIX) }
    knownVersions.forEach { (key, knownVersion) ->
        if (knownVersion.markerFile.isFile) {
            keys += key
        }
    }

    return keys
        .mapNotNull { key ->
            val knownVersion = knownVersions[key]
            val hostDirectory = hostRoot
                ?.resolve(key)
                ?.takeIf { file -> file.exists() }
            val userDirectory = appFilesDirectory
                ?.resolve("$SHADER_CACHE_USER_DIR_PREFIX$key")
                ?.takeIf { file -> file.exists() }
            val markerFile = knownVersion
                ?.markerFile
                ?.takeIf { file -> file.isFile }
            if (hostDirectory == null && userDirectory == null && markerFile == null) {
                return@mapNotNull null
            }
            val stats = collectFileStats(
                listOfNotNull(
                    hostDirectory,
                    userDirectory,
                    markerFile,
                ),
            )
            Sts2ShaderCacheEntry(
                key = key,
                releaseVersion = knownVersion?.releaseVersion,
                versionDisplayName = knownVersion?.version?.let { version ->
                    version.versionName.ifBlank { version.versionId.ifBlank { version.clientId.toString() } }
                },
                hostProjectDirectoryPath = hostDirectory?.absolutePath,
                godotUserDirectoryPath = userDirectory?.absolutePath,
                markerFilePath = markerFile?.absolutePath,
                fileCount = stats.fileCount,
                sizeBytes = stats.sizeBytes,
                lastModified = stats.lastModified,
                isCurrent = key == selectedKey,
            )
        }
        .sortedWith(
            compareByDescending<Sts2ShaderCacheEntry> { entry -> entry.isCurrent }
                .thenByDescending { entry -> entry.lastModified }
                .thenBy { entry -> entry.key },
        )
}

internal fun deleteSts2ShaderCacheEntry(
    hostPaths: ExtensionHostPaths,
    entry: Sts2ShaderCacheEntry,
    versions: List<Sts2VersionDefinition>,
): Boolean {
    var changed = false
    listOfNotNull(
        entry.hostProjectDirectoryPath,
        entry.godotUserDirectoryPath,
    )
        .map(::File)
        .forEach { file ->
            changed = deleteCachePath(file) || changed
        }
    entry.markerFilePath
        ?.let(::File)
        ?.let { file -> changed = deleteCachePath(file) || changed }

    versions
        .filter { version ->
            version.gameDirectory.trim().isNotBlank() &&
                sts2ShaderCacheHostProjectKey(version) == entry.key
        }
        .map { version -> shaderWarmupMarkerFile(File(version.gameDirectory.trim())) }
        .forEach { marker -> changed = deleteCachePath(marker) || changed }

    val hostRoot = sts2ShaderCacheHostProjectRoot(hostPaths)
    val appFilesDirectory = appFilesDirectory(hostPaths)
    listOfNotNull(
        hostRoot?.resolve(entry.key),
        appFilesDirectory?.resolve("$SHADER_CACHE_USER_DIR_PREFIX${entry.key}"),
    ).forEach { file ->
        changed = deleteCachePath(file) || changed
    }

    return changed
}

internal fun readReleaseInfoVersion(gameDirectory: File): String {
    val releaseInfo = gameDirectory.resolve(RELEASE_INFO_FILE)
    if (releaseInfo.isFile) {
        runCatching {
            val version = JSONObject(releaseInfo.readText()).optString("version").trim()
            if (version.isNotBlank()) return version
        }
    }

    val packFile = gameDirectory.resolve(PACK_FILE)
    if (packFile.isFile) {
        return "unknown-${packFile.length()}-${packFile.lastModified()}"
    }

    return "unknown"
}

private data class KnownShaderCacheVersion(
    val version: Sts2VersionDefinition,
    val releaseVersion: String,
    val markerFile: File,
)

private data class FileStats(
    val fileCount: Int,
    val sizeBytes: Long,
    val lastModified: Long,
)

private fun sts2ShaderCacheHostProjectKey(
    gameDirectory: File,
    releaseVersion: String,
): String {
    val gameDirectoryHash = Integer.toHexString(gameDirectory.absolutePath.hashCode())
    val safeReleaseVersion = releaseVersion.toSafeShaderCacheKeyPart()
    return "$SHADER_CACHE_HOST_PREFIX$gameDirectoryHash-$safeReleaseVersion"
}

private fun sts2ShaderCacheHostProjectDirectory(
    hostPaths: ExtensionHostPaths,
    version: Sts2VersionDefinition,
): File? {
    val root = sts2ShaderCacheHostProjectRoot(hostPaths) ?: return null
    return root.resolve(sts2ShaderCacheHostProjectKey(version))
}

private fun sts2ShaderCacheHostProjectRoot(hostPaths: ExtensionHostPaths): File? {
    val appRoot = appFilesDirectory(hostPaths)?.parentFile ?: return null
    return appRoot
        .resolve("code_cache")
        .resolve(ANDROID_EXTENSION_RUNTIME_DIRECTORY)
        .resolve(STS2_TEMPLATE_ID.toSafeShaderCacheKeyPart())
        .resolve(PROJECT_HOST_DIRECTORY)
}

private fun appFilesDirectory(hostPaths: ExtensionHostPaths): File? {
    return hostPaths.appFilesDirectoryPath
        ?.trim()
        ?.takeIf { path -> path.isNotBlank() }
        ?.let(::File)
}

private fun collectFileStats(paths: List<File>): FileStats {
    var fileCount = 0
    var sizeBytes = 0L
    var lastModified = 0L
    paths
        .distinctBy { file -> file.absolutePath }
        .forEach { path ->
            val stats = collectFileStats(path)
            fileCount += stats.fileCount
            sizeBytes += stats.sizeBytes
            lastModified = maxOf(lastModified, stats.lastModified)
        }
    return FileStats(
        fileCount = fileCount,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
    )
}

private fun collectFileStats(root: File): FileStats {
    if (!root.exists()) return FileStats(fileCount = 0, sizeBytes = 0L, lastModified = 0L)
    if (root.isFile) {
        return FileStats(
            fileCount = 1,
            sizeBytes = root.length(),
            lastModified = root.lastModified(),
        )
    }

    var fileCount = 0
    var sizeBytes = 0L
    var lastModified = root.lastModified()
    val pending = mutableListOf(root)
    while (pending.isNotEmpty()) {
        val file = pending.removeAt(pending.lastIndex)
        lastModified = maxOf(lastModified, file.lastModified())
        if (file.isFile) {
            fileCount += 1
            sizeBytes += file.length()
        } else {
            file.listFiles()
                .orEmpty()
                .forEach { child -> pending += child }
        }
    }
    return FileStats(
        fileCount = fileCount,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
    )
}

private fun deleteCachePath(file: File): Boolean {
    if (!file.exists()) return false
    return runCatching {
        if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }.getOrDefault(false)
}

private fun shaderWarmupMarkerFile(gameDirectory: File): File {
    return gameDirectory
        .resolve(SHADER_WARMUP_MARKER_DIRECTORY)
        .resolve(SHADER_WARMUP_MARKER_FILE)
}

private fun deleteShaderWarmupMarker(marker: File) {
    runCatching {
        if (marker.isFile) {
            marker.delete()
        }
    }
}

private fun sameMarkerValue(
    left: String,
    right: String,
): Boolean {
    return left.trim().equals(right.trim(), ignoreCase = true)
}

private fun normalizeShaderWarmupRenderer(renderer: String): String {
    return when (renderer.trim().lowercase()) {
        "opengl" -> "opengl"
        else -> "vulkan"
    }
}

private fun String.toSafeShaderCacheKeyPart(): String {
    return map { character ->
        if (character.isLetterOrDigit() || character == '.' || character == '-' || character == '_') {
            character
        } else {
            '_'
        }
    }.joinToString("").ifBlank { "unknown" }
}
