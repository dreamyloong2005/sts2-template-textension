package com.dreamyloong.template.sts2.android

import com.dreamyloong.tlauncher.sdk.extension.ExtensionHostPaths
import com.dreamyloong.tlauncher.sdk.extension.ExtensionPackageResources
import java.io.File
import java.io.InputStream

private const val DOTNET_BCL_RESOURCE_DIRECTORY = "runtime/dotnet_bcl"
private const val PATCH_RESOURCE_DIRECTORY = "runtime/patch"
private const val ANDROID_RUNTIME_DIRECTORY = ".godot/mono/publish/arm64"
private const val STS2_VERSION_SOURCE_DIRECTORY = "data_sts2_windows_x86_64"

data class Sts2GodotRuntimeReport(
    val isComplete: Boolean,
    val runtimeDirectoryPath: String?,
    val selectedVersionSourceDirectoryPath: String?,
    val bundledReferenceFileCount: Int,
    val selectedVersionFileCount: Int,
    val runtimeFileCount: Int,
    val issues: List<Sts2RuntimeIssue>,
) {
    val quickFixAvailable: Boolean
        get() = runtimeDirectoryPath != null &&
            !issues.any { issue ->
                issue.kind == Sts2RuntimeIssueKind.VERSION_NOT_SELECTED ||
                    issue.kind == Sts2RuntimeIssueKind.MANAGE_STORAGE_NOT_GRANTED
            } &&
            issues.any { issue -> issue.quickFixAvailable }

    val versionSelected: Boolean
        get() = !issues.any { issue -> issue.kind == Sts2RuntimeIssueKind.VERSION_NOT_SELECTED }

    val manageStorageGranted: Boolean
        get() = !issues.any { issue -> issue.kind == Sts2RuntimeIssueKind.MANAGE_STORAGE_NOT_GRANTED }
}

data class Sts2RuntimeIssue(
    val fileName: String,
    val source: Sts2RuntimeIssueSource,
    val kind: Sts2RuntimeIssueKind,
    val sourcePath: String? = null,
    val targetPath: String? = null,
    val quickFixAvailable: Boolean = false,
)

data class Sts2RuntimeFileEntry(
    val name: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val lastModifiedEpochMillis: Long,
)

enum class Sts2RuntimeIssueSource {
    BUNDLED_RUNTIME,
    SELECTED_VERSION_RUNTIME,
    RUNTIME_DIRECTORY,
}

enum class Sts2RuntimeIssueKind {
    MANAGE_STORAGE_NOT_GRANTED,
    VERSION_NOT_SELECTED,
    MISSING_TARGET,
    TARGET_MISMATCH,
    SOURCE_DIRECTORY_MISSING,
    RUNTIME_DIRECTORY_UNAVAILABLE,
}

fun diagnoseSts2Runtime(
    resources: ExtensionPackageResources,
    hostPaths: ExtensionHostPaths,
    selectedGameDirectory: String?,
    manageStorageGranted: Boolean,
): Sts2GodotRuntimeReport {
    val runtimeDirectory = runtimeDirectory(hostPaths)
    val bundledAssets = bundledRuntimeAssets(resources)
    val bundledNames = bundledAssets.map { asset -> asset.fileName }.toSet()
    val issues = mutableListOf<Sts2RuntimeIssue>()

    if (!manageStorageGranted) {
        issues += Sts2RuntimeIssue(
            fileName = "manage_external_storage",
            source = Sts2RuntimeIssueSource.SELECTED_VERSION_RUNTIME,
            kind = Sts2RuntimeIssueKind.MANAGE_STORAGE_NOT_GRANTED,
        )
    }

    if (runtimeDirectory == null) {
        issues += Sts2RuntimeIssue(
            fileName = ANDROID_RUNTIME_DIRECTORY,
            source = Sts2RuntimeIssueSource.RUNTIME_DIRECTORY,
            kind = Sts2RuntimeIssueKind.RUNTIME_DIRECTORY_UNAVAILABLE,
        )
    } else {
        bundledAssets.forEach { asset ->
            val targetFile = runtimeDirectory.resolve(asset.fileName)
            when {
                !targetFile.isFile -> issues += Sts2RuntimeIssue(
                    fileName = asset.fileName,
                    source = Sts2RuntimeIssueSource.BUNDLED_RUNTIME,
                    kind = Sts2RuntimeIssueKind.MISSING_TARGET,
                    targetPath = targetFile.absolutePath,
                    quickFixAvailable = true,
                )

                !compareFileWithStream(targetFile) { asset.bytes.inputStream() } -> issues += Sts2RuntimeIssue(
                    fileName = asset.fileName,
                    source = Sts2RuntimeIssueSource.BUNDLED_RUNTIME,
                    kind = Sts2RuntimeIssueKind.TARGET_MISMATCH,
                    targetPath = targetFile.absolutePath,
                    quickFixAvailable = true,
                )
            }
        }
    }

    val selectedVersionSourceDirectory = selectedVersionSourceDirectory(selectedGameDirectory)
    val selectedRuntimeFiles = when {
        selectedVersionSourceDirectory == null -> {
            issues += Sts2RuntimeIssue(
                fileName = STS2_VERSION_SOURCE_DIRECTORY,
                source = Sts2RuntimeIssueSource.SELECTED_VERSION_RUNTIME,
                kind = Sts2RuntimeIssueKind.VERSION_NOT_SELECTED,
            )
            emptyList()
        }

        !selectedVersionSourceDirectory.isDirectory -> {
            issues += Sts2RuntimeIssue(
                fileName = STS2_VERSION_SOURCE_DIRECTORY,
                source = Sts2RuntimeIssueSource.SELECTED_VERSION_RUNTIME,
                kind = Sts2RuntimeIssueKind.SOURCE_DIRECTORY_MISSING,
                sourcePath = selectedVersionSourceDirectory.absolutePath,
            )
            emptyList()
        }

        else -> selectedVersionSourceDirectory.listFiles()
            .orEmpty()
            .filter { file -> file.isFile && file.name !in bundledNames }
            .sortedBy { file -> file.name.lowercase() }
    }

    if (runtimeDirectory != null) {
        selectedRuntimeFiles.forEach { sourceFile ->
            val targetFile = runtimeDirectory.resolve(sourceFile.name)
            when {
                !targetFile.isFile -> issues += Sts2RuntimeIssue(
                    fileName = sourceFile.name,
                    source = Sts2RuntimeIssueSource.SELECTED_VERSION_RUNTIME,
                    kind = Sts2RuntimeIssueKind.MISSING_TARGET,
                    sourcePath = sourceFile.absolutePath,
                    targetPath = targetFile.absolutePath,
                    quickFixAvailable = true,
                )

                !compareFiles(sourceFile, targetFile) -> issues += Sts2RuntimeIssue(
                    fileName = sourceFile.name,
                    source = Sts2RuntimeIssueSource.SELECTED_VERSION_RUNTIME,
                    kind = Sts2RuntimeIssueKind.TARGET_MISMATCH,
                    sourcePath = sourceFile.absolutePath,
                    targetPath = targetFile.absolutePath,
                    quickFixAvailable = true,
                )
            }
        }
    }

    return Sts2GodotRuntimeReport(
        isComplete = issues.isEmpty(),
        runtimeDirectoryPath = runtimeDirectory?.absolutePath,
        selectedVersionSourceDirectoryPath = selectedVersionSourceDirectory?.absolutePath,
        bundledReferenceFileCount = bundledAssets.size,
        selectedVersionFileCount = selectedRuntimeFiles.size,
        runtimeFileCount = listSts2RuntimeFiles(hostPaths).size,
        issues = issues.sortedWith(
            compareBy<Sts2RuntimeIssue>({ it.source.ordinal }, { it.fileName.lowercase() }),
        ),
    )
}

fun repairSts2Runtime(
    resources: ExtensionPackageResources,
    hostPaths: ExtensionHostPaths,
    selectedGameDirectory: String?,
    manageStorageGranted: Boolean,
): Boolean {
    val runtimeDirectory = runtimeDirectory(hostPaths) ?: return false
    runtimeDirectory.mkdirs()

    val bundledAssets = bundledRuntimeAssets(resources)
    bundledAssets.forEach { asset ->
        asset.bytes.inputStream().use { input ->
            runtimeDirectory.resolve(asset.fileName).outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    val bundledNames = bundledAssets.map { asset -> asset.fileName }.toSet()
    selectedVersionSourceDirectory(selectedGameDirectory)
        ?.takeIf { directory -> directory.isDirectory }
        ?.listFiles()
        .orEmpty()
        .filter { file -> file.isFile && file.name !in bundledNames }
        .forEach { sourceFile ->
            sourceFile.copyTo(
                target = runtimeDirectory.resolve(sourceFile.name),
                overwrite = true,
            )
        }

    return diagnoseSts2Runtime(
        resources = resources,
        hostPaths = hostPaths,
        selectedGameDirectory = selectedGameDirectory,
        manageStorageGranted = manageStorageGranted,
    ).isComplete
}

fun listSts2RuntimeFiles(hostPaths: ExtensionHostPaths): List<Sts2RuntimeFileEntry> {
    return runtimeDirectory(hostPaths)
        ?.listFiles()
        .orEmpty()
        .filter { file -> file.isFile }
        .sortedBy { file -> file.name.lowercase() }
        .map { file ->
            Sts2RuntimeFileEntry(
                name = file.name,
                absolutePath = file.absolutePath,
                sizeBytes = file.length(),
                lastModifiedEpochMillis = file.lastModified(),
            )
        }
}

fun importSts2RuntimeDll(
    hostPaths: ExtensionHostPaths,
    fileName: String,
    bytes: ByteArray,
): Boolean {
    val cleanName = File(fileName).name
    if (!cleanName.endsWith(".dll", ignoreCase = true)) return false
    val runtimeDirectory = runtimeDirectory(hostPaths) ?: return false
    runtimeDirectory.mkdirs()
    runtimeDirectory.resolve(cleanName).writeBytes(bytes)
    return true
}

fun deleteSts2RuntimeFile(
    hostPaths: ExtensionHostPaths,
    fileName: String,
): Boolean {
    val cleanName = File(fileName).name
    return runtimeDirectory(hostPaths)
        ?.resolve(cleanName)
        ?.takeIf { file -> file.isFile }
        ?.delete() == true
}

private fun runtimeDirectory(hostPaths: ExtensionHostPaths): File? {
    return hostPaths.appFilesDirectoryPath
        ?.let(::File)
        ?.resolve(ANDROID_RUNTIME_DIRECTORY)
}

private fun selectedVersionSourceDirectory(selectedGameDirectory: String?): File? {
    return selectedGameDirectory
        ?.trim()
        ?.takeIf { directory -> directory.isNotBlank() }
        ?.let(::File)
        ?.resolve(STS2_VERSION_SOURCE_DIRECTORY)
}

private fun bundledRuntimeAssets(resources: ExtensionPackageResources): List<BundledRuntimeAsset> {
    val dotnetAssets = resources.listRuntimeFiles(DOTNET_BCL_RESOURCE_DIRECTORY)
    val patchAssets = resources.listRuntimeFiles(PATCH_RESOURCE_DIRECTORY)
    return (dotnetAssets + patchAssets).sortedBy { asset -> asset.fileName.lowercase() }
}

private fun ExtensionPackageResources.listRuntimeFiles(directory: String): List<BundledRuntimeAsset> {
    return listFilesRecursively(directory)
        .mapNotNull { resourcePath ->
            readBytes(resourcePath)?.let { bytes ->
                BundledRuntimeAsset(
                    fileName = resourcePath.substringAfterLast('/'),
                    bytes = bytes,
                )
            }
        }
}

private fun ExtensionPackageResources.listFilesRecursively(path: String): List<String> {
    return list(path).flatMap { child ->
        val childPath = "$path/$child"
        if (readBytes(childPath) != null) {
            listOf(childPath)
        } else {
            listFilesRecursively(childPath)
        }
    }
}

private fun compareFiles(
    sourceFile: File,
    targetFile: File,
): Boolean {
    if (!sourceFile.isFile || !targetFile.isFile) return false
    if (sourceFile.length() != targetFile.length()) return false
    return sourceFile.inputStream().use { source ->
        targetFile.inputStream().use { target ->
            streamsEqual(source, target)
        }
    }
}

private fun compareFileWithStream(
    targetFile: File,
    assetStream: () -> InputStream,
): Boolean {
    if (!targetFile.isFile) return false
    return assetStream().use { source ->
        targetFile.inputStream().use { target ->
            streamsEqual(source, target)
        }
    }
}

private fun streamsEqual(
    source: InputStream,
    target: InputStream,
): Boolean {
    val sourceBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val targetBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val sourceRead = source.read(sourceBuffer)
        val targetRead = target.read(targetBuffer)
        if (sourceRead != targetRead) return false
        if (sourceRead == -1) return true
        for (index in 0 until sourceRead) {
            if (sourceBuffer[index] != targetBuffer[index]) {
                return false
            }
        }
    }
}

private data class BundledRuntimeAsset(
    val fileName: String,
    val bytes: ByteArray,
)
