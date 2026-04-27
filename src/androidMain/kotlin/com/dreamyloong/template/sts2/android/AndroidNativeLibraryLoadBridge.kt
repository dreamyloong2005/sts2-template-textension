package com.dreamyloong.template.sts2.android

import java.io.File

private const val PROJECT_FMOD_LIBRARY_RELATIVE_PATH =
    "addons/fmod/libs/android/arm64/libGodotFmod.android.template_release.arm64.so"
private const val PROJECT_FMOD_RUNTIME_LIBRARY_RELATIVE_PATH =
    "addons/fmod/libs/android/arm64/libfmod.so"
private const val PROJECT_FMOD_STUDIO_RUNTIME_LIBRARY_RELATIVE_PATH =
    "addons/fmod/libs/android/arm64/libfmodstudio.so"
private const val PROJECT_SPINE_LIBRARY_RELATIVE_PATH =
    "addons/spine/android/libspine_godot.android.template_release.arm64.so"
private const val PROJECT_RELEASE_INFO_RELATIVE_PATH = "release_info.json"
private const val PROJECT_FMOD_LIBRARY_NAME = "libGodotFmod.android.template_release.arm64.so"
private const val PROJECT_FMOD_RUNTIME_LIBRARY_NAME = "libfmod.so"
private const val PROJECT_FMOD_STUDIO_RUNTIME_LIBRARY_NAME = "libfmodstudio.so"
private const val PROJECT_SPINE_LIBRARY_NAME = "libspine_godot.android.template_release.arm64.so"

object AndroidNativeLibraryLoadBridge {
    @JvmStatic
    fun loadLibraries(vararg absolutePaths: String) {
        absolutePaths.forEach { absolutePath ->
            System.load(absolutePath)
        }
    }

    @JvmStatic
    fun onHostActivityCreated(context: Any) {
        val contextClass = Class.forName("android.content.Context")
        Class.forName("org.fmod.FMOD")
            .getMethod("init", contextClass)
            .invoke(null, context)
    }

    @JvmStatic
    fun onHostActivityDestroyed(context: Any) {
        Class.forName("org.fmod.FMOD")
            .getMethod("close")
            .invoke(null)
    }

    @JvmStatic
    fun createHostFragment(
        projectDirectoryPath: String,
        packFilePath: String,
        launchContextJson: String,
    ): Any {
        return Sts2GodotHostFragment.newInstance(
            projectDirectoryPath = projectDirectoryPath,
            packFilePath = packFilePath,
            launchContextJson = launchContextJson,
        )
    }

    @JvmStatic
    fun prepareProjectDirectory(
        nativeLibraryDirectoryPath: String,
        projectDirectoryPath: String,
        hostProjectDirectoryPath: String,
        launchContextJson: String,
    ): String {
        val projectDirectory = prepareProjectDirectory(
            nativeLibraryDirectoryPath = nativeLibraryDirectoryPath,
            projectDirectoryPath = projectDirectoryPath,
            hostProjectDirectoryPath = hostProjectDirectoryPath,
        )
        syncShaderCacheProjectSettingsOverride(
            projectDirectory = File(projectDirectory),
            shaderCacheEnabled = parseShaderCacheEnabled(launchContextJson),
        )
        return projectDirectory
    }

    @JvmStatic
    fun prepareProjectDirectory(
        nativeLibraryDirectoryPath: String,
        projectDirectoryPath: String,
        hostProjectDirectoryPath: String,
    ): String {
        require(projectDirectoryPath.isNotBlank()) {
            "STS2 source project directory must not be blank."
        }
        val sourceProjectDirectory = File(projectDirectoryPath)
        val nativeLibraryDirectory = File(nativeLibraryDirectoryPath)
        val projectDirectory = File(hostProjectDirectoryPath)
        syncOptionalProjectFile(
            sourceFile = sourceProjectDirectory.resolve(PROJECT_RELEASE_INFO_RELATIVE_PATH),
            targetFile = projectDirectory.resolve(PROJECT_RELEASE_INFO_RELATIVE_PATH),
        )
        syncProjectLibrary(
            sourceFile = nativeLibraryDirectory.resolve(PROJECT_FMOD_LIBRARY_NAME),
            targetFile = projectDirectory.resolve(PROJECT_FMOD_LIBRARY_RELATIVE_PATH),
        )
        syncProjectLibrary(
            sourceFile = nativeLibraryDirectory.resolve(PROJECT_SPINE_LIBRARY_NAME),
            targetFile = projectDirectory.resolve(PROJECT_SPINE_LIBRARY_RELATIVE_PATH),
        )
        syncProjectLibrary(
            sourceFile = nativeLibraryDirectory.resolve(PROJECT_FMOD_RUNTIME_LIBRARY_NAME),
            targetFile = projectDirectory.resolve(PROJECT_FMOD_RUNTIME_LIBRARY_RELATIVE_PATH),
        )
        syncProjectLibrary(
            sourceFile = nativeLibraryDirectory.resolve(PROJECT_FMOD_STUDIO_RUNTIME_LIBRARY_NAME),
            targetFile = projectDirectory.resolve(PROJECT_FMOD_STUDIO_RUNTIME_LIBRARY_RELATIVE_PATH),
        )
        return projectDirectory.absolutePath
    }

    private fun syncProjectLibrary(
        sourceFile: File,
        targetFile: File,
    ) {
        require(sourceFile.isFile) {
            "Missing STS2 project library: ${sourceFile.absolutePath}"
        }
        targetFile.parentFile?.mkdirs()
        sourceFile.copyTo(targetFile, overwrite = true)
    }

    private fun syncOptionalProjectFile(
        sourceFile: File,
        targetFile: File,
    ) {
        if (!sourceFile.isFile) {
            return
        }
        targetFile.parentFile?.mkdirs()
        sourceFile.copyTo(targetFile, overwrite = true)
    }
}
