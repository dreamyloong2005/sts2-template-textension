import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    kotlin("jvm") version "2.2.20"
}

group = "com.dreamyloong.tlauncher.template"
version = "1.0.0"

data class TExtensionPackageTarget(
    val name: String,
    val platformTarget: String,
    val extensionId: String,
    val entrypoint: String,
    val runtimeArtifactPath: String,
    val runtimeJarTaskName: String,
    val sourceSetName: String,
    val capabilities: List<String>,
)

val packageVersion = version.toString()
val extensionApiVersion = "1.0.0"
val minSdkApiVersion = 1
val targetSdkApiVersion = 1
val windowsTExtensionTarget = TExtensionPackageTarget(
    name = "windows",
    platformTarget = "WINDOWS",
    extensionId = "template.dreamyloong.sts2.windows",
    entrypoint = "com.dreamyloong.template.sts2.windows.Sts2TemplateWindowsEntrypoint",
    runtimeArtifactPath = "runtime/windows/template.dreamyloong.sts2.windows.jar",
    runtimeJarTaskName = "windowsRuntimeJar",
    sourceSetName = "windowsMain",
    capabilities = listOf(
        "DEFINE_TEMPLATE_METADATA",
        "DEFINE_TEMPLATE_RUNTIME_REQUIREMENTS",
        "PROVIDE_TEMPLATE_PAGE_CONTRIBUTIONS",
    ),
)
val androidTExtensionTarget = TExtensionPackageTarget(
    name = "android",
    platformTarget = "ANDROID",
    extensionId = "template.dreamyloong.sts2.android",
    entrypoint = "com.dreamyloong.template.sts2.android.Sts2TemplateAndroidEntrypoint",
    runtimeArtifactPath = "runtime/android/template.dreamyloong.sts2.android.jar",
    runtimeJarTaskName = "androidRuntimeJar",
    sourceSetName = "androidMain",
    capabilities = listOf(
        "DEFINE_TEMPLATE_METADATA",
        "DEFINE_TEMPLATE_RUNTIME_REQUIREMENTS",
        "PROVIDE_TEMPLATE_PAGE_CONTRIBUTIONS",
    ),
)

val sts2MobileProjectDir = project.projectDir.resolve("STS2Mobile")
val commonMainAssetsDir = sourceSetAssetsDir("commonMain")
val androidMainAssetsDir = sourceSetAssetsDir(androidTExtensionTarget.sourceSetName)
val windowsMainAssetsDir = sourceSetAssetsDir(windowsTExtensionTarget.sourceSetName)
val androidAssetsLibsDir = androidMainAssetsDir.resolve("libs")
val androidNativeAbi = "arm64-v8a"
val androidNativeLibDir = androidMainAssetsDir.resolve("jniLibs").resolve(androidNativeAbi)
val androidNativeResourcePath = "resources/android/jniLibs/$androidNativeAbi"
val dotnetBclDir = androidMainAssetsDir.resolve("dotnet_bcl")
val androidReleaseNativeLibraryExcludes = listOf(
    "libGodotFmod.android.template_debug.arm64.so",
    "libspine_godot.android.template_debug.arm64.so",
    "libmono-component-debugger.so",
    "libmono-component-diagnostics_tracing.so",
    "libmono-component-hot_reload.so",
)
val generatedAndroidDexLibraryDir = layout.buildDirectory.dir("generated/textension/android-dex-libs")
val generatedAndroidRuntimeJarFile =
    layout.buildDirectory.file("generated/textension/android/runtime/${androidTExtensionTarget.extensionId}.jar")
val sts2MobileReleaseDir = sts2MobileProjectDir.resolve("bin/Release/net9.0")
val dotnetAndroidCryptoSupportJar = resolveDotnetAndroidCryptoSupportJar()
val resolvedAndroidSdkDirectory = resolveAndroidSdkDirectory(rootProject.projectDir)
val resolvedCompileSdkVersion = providers.gradleProperty("android.compileSdk").orElse("36").get()
val resolvedMinSdkVersion = providers.gradleProperty("android.minSdk").orElse("26").get().toInt()
val resolvedAndroidJarFile = resolvedAndroidSdkDirectory.resolve("platforms/android-$resolvedCompileSdkVersion/android.jar")
val resolvedD8Executable = resolveAndroidBuildToolExecutable(resolvedAndroidSdkDirectory, "d8")
val androidFragmentClassesJarFile =
    layout.buildDirectory.file("generated/textension/android-compile-classpath/androidx-fragment-classes.jar").get().asFile
val androidActivityClassesJarFile =
    layout.buildDirectory.file("generated/textension/android-compile-classpath/androidx-activity-classes.jar").get().asFile
val androidSavedStateClassesJarFile =
    layout.buildDirectory.file("generated/textension/android-compile-classpath/androidx-savedstate-classes.jar").get().asFile
val androidLifecycleViewModelSavedStateClassesJarFile =
    layout.buildDirectory.file("generated/textension/android-compile-classpath/androidx-lifecycle-viewmodel-savedstate-classes.jar").get().asFile
val androidLifecycleRuntimeClassesJarFile =
    layout.buildDirectory.file("generated/textension/android-compile-classpath/androidx-lifecycle-runtime-classes.jar").get().asFile
val androidLifecycleViewModelClassesJarFile =
    layout.buildDirectory.file("generated/textension/android-compile-classpath/androidx-lifecycle-viewmodel-classes.jar").get().asFile

val androidFragmentAar = configurations.create("androidFragmentAar") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val androidActivityAar = configurations.create("androidActivityAar") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val androidSavedStateAar = configurations.create("androidSavedStateAar") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val androidLifecycleViewModelSavedStateAar = configurations.create("androidLifecycleViewModelSavedStateAar") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val androidLifecycleRuntimeAar = configurations.create("androidLifecycleRuntimeAar") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val androidLifecycleViewModelAar = configurations.create("androidLifecycleViewModelAar") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val commonMainSourceSet = sourceSets.create("commonMain") {
    java.srcDir("src/commonMain/kotlin")
}
val androidMainSourceSet = sourceSets.create("androidMain") {
    java.srcDir("src/androidMain/kotlin")
    compileClasspath += commonMainSourceSet.output
    runtimeClasspath += commonMainSourceSet.output
}
val windowsMainSourceSet = sourceSets.create("windowsMain") {
    java.srcDir("src/windowsMain/kotlin")
    compileClasspath += commonMainSourceSet.output
    runtimeClasspath += commonMainSourceSet.output
}

kotlin {
    sourceSets.named("commonMain") {
        kotlin.srcDir("src/commonMain/kotlin")
    }
    sourceSets.named("androidMain") {
        kotlin.srcDir("src/androidMain/kotlin")
    }
    sourceSets.named("windowsMain") {
        kotlin.srcDir("src/windowsMain/kotlin")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    "commonMainImplementation"("org.json:json:20240303")
    "androidFragmentAar"("androidx.fragment:fragment:1.8.9@aar")
    "androidActivityAar"("androidx.activity:activity:1.8.1@aar")
    "androidSavedStateAar"("androidx.savedstate:savedstate:1.2.1@aar")
    "androidLifecycleViewModelSavedStateAar"("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.6.1@aar")
    "androidLifecycleRuntimeAar"("androidx.lifecycle:lifecycle-runtime:2.6.1@aar")
    "androidLifecycleViewModelAar"("androidx.lifecycle:lifecycle-viewmodel:2.6.1@aar")
    "commonMainImplementation"("com.dreamyloong.tlauncher:tlauncher-extension-api:$extensionApiVersion")
    "androidMainImplementation"("com.dreamyloong.tlauncher:tlauncher-extension-api:$extensionApiVersion")
    "androidMainImplementation"(files(resolvedAndroidJarFile))
    "androidMainImplementation"(files(androidFragmentClassesJarFile).builtBy("extractAndroidFragmentClassesJar"))
    "androidMainImplementation"(files(androidActivityClassesJarFile).builtBy("extractAndroidActivityClassesJar"))
    "androidMainImplementation"(files(androidSavedStateClassesJarFile).builtBy("extractAndroidSavedStateClassesJar"))
    "androidMainImplementation"(
        files(androidLifecycleViewModelSavedStateClassesJarFile)
            .builtBy("extractAndroidLifecycleViewModelSavedStateClassesJar"),
    )
    "androidMainImplementation"(files(androidLifecycleRuntimeClassesJarFile).builtBy("extractAndroidLifecycleRuntimeClassesJar"))
    "androidMainImplementation"(files(androidLifecycleViewModelClassesJarFile).builtBy("extractAndroidLifecycleViewModelClassesJar"))
    "androidMainImplementation"("androidx.lifecycle:lifecycle-common:2.6.1@jar")
    "androidMainImplementation"(files(androidAssetsLibsDir.resolve("classes.jar")))
    "androidMainImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    "androidMainImplementation"(commonMainSourceSet.output)
    "windowsMainImplementation"("com.dreamyloong.tlauncher:tlauncher-extension-api:$extensionApiVersion")
    "windowsMainImplementation"(commonMainSourceSet.output)
    "windowsMainImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    "windowsMainImplementation"("net.java.dev.jna:jna:5.17.0")
}

val extractAndroidFragmentClassesJar = tasks.register<Copy>("extractAndroidFragmentClassesJar") {
    into(androidFragmentClassesJarFile.parentFile)
    from({ androidFragmentAar.files.map { file -> zipTree(file) } }) {
        include("classes.jar")
        rename { androidFragmentClassesJarFile.name }
    }
    outputs.file(androidFragmentClassesJarFile)
}

val extractAndroidActivityClassesJar = tasks.register<Copy>("extractAndroidActivityClassesJar") {
    into(androidActivityClassesJarFile.parentFile)
    from({ androidActivityAar.files.map { file -> zipTree(file) } }) {
        include("classes.jar")
        rename { androidActivityClassesJarFile.name }
    }
    outputs.file(androidActivityClassesJarFile)
}

val extractAndroidSavedStateClassesJar = tasks.register<Copy>("extractAndroidSavedStateClassesJar") {
    into(androidSavedStateClassesJarFile.parentFile)
    from({ androidSavedStateAar.files.map { file -> zipTree(file) } }) {
        include("classes.jar")
        rename { androidSavedStateClassesJarFile.name }
    }
    outputs.file(androidSavedStateClassesJarFile)
}

val extractAndroidLifecycleViewModelSavedStateClassesJar =
    tasks.register<Copy>("extractAndroidLifecycleViewModelSavedStateClassesJar") {
        into(androidLifecycleViewModelSavedStateClassesJarFile.parentFile)
        from({ androidLifecycleViewModelSavedStateAar.files.map { file -> zipTree(file) } }) {
            include("classes.jar")
            rename { androidLifecycleViewModelSavedStateClassesJarFile.name }
        }
        outputs.file(androidLifecycleViewModelSavedStateClassesJarFile)
    }

val extractAndroidLifecycleRuntimeClassesJar = tasks.register<Copy>("extractAndroidLifecycleRuntimeClassesJar") {
    into(androidLifecycleRuntimeClassesJarFile.parentFile)
    from({ androidLifecycleRuntimeAar.files.map { file -> zipTree(file) } }) {
        include("classes.jar")
        rename { androidLifecycleRuntimeClassesJarFile.name }
    }
    outputs.file(androidLifecycleRuntimeClassesJarFile)
}

val extractAndroidLifecycleViewModelClassesJar = tasks.register<Copy>("extractAndroidLifecycleViewModelClassesJar") {
    into(androidLifecycleViewModelClassesJarFile.parentFile)
    from({ androidLifecycleViewModelAar.files.map { file -> zipTree(file) } }) {
        include("classes.jar")
        rename { androidLifecycleViewModelClassesJarFile.name }
    }
    outputs.file(androidLifecycleViewModelClassesJarFile)
}

val windowsRuntimeJar = tasks.register<Jar>(windowsTExtensionTarget.runtimeJarTaskName) {
    dependsOn(tasks.named("commonMainClasses"))
    dependsOn(tasks.named("windowsMainClasses"))
    archiveBaseName.set(windowsTExtensionTarget.extensionId)
    archiveVersion.set(packageVersion)
    from(commonMainSourceSet.output)
    from(windowsMainSourceSet.output)
}

val androidRuntimeJar = tasks.register<Jar>(androidTExtensionTarget.runtimeJarTaskName) {
    dependsOn(tasks.named("commonMainClasses"))
    dependsOn(tasks.named("androidMainClasses"))
    archiveBaseName.set(androidTExtensionTarget.extensionId)
    archiveVersion.set(packageVersion)
    from(commonMainSourceSet.output)
    from(androidMainSourceSet.output)
}

val generateWindowsTExtensionManifest = registerGenerateTExtensionManifest(windowsTExtensionTarget)
val generateAndroidTExtensionManifest = registerGenerateTExtensionManifest(androidTExtensionTarget)

val buildSts2MobileDll = tasks.register<Exec>("buildSts2MobileDll") {
    notCompatibleWithConfigurationCache("External dotnet build output is staged into a .textension package.")
    workingDir = sts2MobileProjectDir
    commandLine("dotnet", "build", sts2MobileProjectDir.resolve("STS2Mobile.csproj").absolutePath, "-c", "Release")
}

val androidCompileClasspath = configurations.named("androidMainCompileClasspath")

val dexAndroidTExtensionRuntimeJar = tasks.register<Exec>("dexAndroidTExtensionRuntimeJar") {
    notCompatibleWithConfigurationCache("Android D8 command line is assembled from the resolved compile classpath.")
    val inputJar = androidRuntimeJar.flatMap { it.archiveFile }
    dependsOn(androidRuntimeJar)
    inputs.file(inputJar)
    inputs.files(androidCompileClasspath)
    outputs.file(generatedAndroidRuntimeJarFile)

    doFirst {
        val outputFile = generatedAndroidRuntimeJarFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.delete()
        val command = mutableListOf(
            resolvedD8Executable.absolutePath,
            "--min-api",
            resolvedMinSdkVersion.toString(),
            "--lib",
            resolvedAndroidJarFile.absolutePath,
        )
        androidCompileClasspath.get().files
            .filter { file -> file.exists() && file.absoluteFile != resolvedAndroidJarFile.absoluteFile }
            .sortedBy { file -> file.absolutePath }
            .forEach { file ->
                command += "--classpath"
                command += file.absolutePath
            }
        command += "--output"
        command += outputFile.absolutePath
        command += inputJar.get().asFile.absolutePath
        commandLine(command)
    }
}

val prepareFmodDexLibrary = tasks.register<Exec>("prepareFmodDexLibrary") {
    notCompatibleWithConfigurationCache("Android D8 command line is assembled at execution time.")
    val inputJar = androidAssetsLibsDir.resolve("fmod.jar")
    val outputJar = generatedAndroidDexLibraryDir.map { directory -> directory.file("fmod.jar") }
    inputs.file(inputJar)
    outputs.file(outputJar)

    doFirst {
        val outputFile = outputJar.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.delete()
        commandLine(
            resolvedD8Executable.absolutePath,
            "--min-api",
            resolvedMinSdkVersion.toString(),
            "--lib",
            resolvedAndroidJarFile.absolutePath,
            "--output",
            outputFile.absolutePath,
            inputJar.absolutePath,
        )
    }
}

val prepareGodotDexLibrary = tasks.register<Exec>("prepareGodotDexLibrary") {
    notCompatibleWithConfigurationCache("Android D8 command line is assembled at execution time.")
    val inputJar = androidAssetsLibsDir.resolve("classes.jar")
    val outputJar = generatedAndroidDexLibraryDir.map { directory -> directory.file("classes.jar") }
    inputs.file(inputJar)
    inputs.files(androidCompileClasspath)
    outputs.file(outputJar)

    doFirst {
        val outputFile = outputJar.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.delete()
        val command = mutableListOf(
            resolvedD8Executable.absolutePath,
            "--min-api",
            resolvedMinSdkVersion.toString(),
            "--lib",
            resolvedAndroidJarFile.absolutePath,
        )
        androidCompileClasspath.get().files
            .filter { file ->
                file.exists() &&
                    file.absoluteFile != inputJar.absoluteFile &&
                    file.absoluteFile != resolvedAndroidJarFile.absoluteFile
            }
            .sortedBy { file -> file.absolutePath }
            .forEach { file ->
                command += "--classpath"
                command += file.absolutePath
            }
        command += "--output"
        command += outputFile.absolutePath
        command += inputJar.absolutePath
        commandLine(command)
    }
}

val prepareDotnetCryptoDexLibrary = tasks.register<Exec>("prepareDotnetCryptoDexLibrary") {
    notCompatibleWithConfigurationCache("Android D8 command line is assembled at execution time.")
    val outputJar = generatedAndroidDexLibraryDir.map { directory ->
        directory.file("libSystem.Security.Cryptography.Native.Android.jar")
    }
    inputs.file(dotnetAndroidCryptoSupportJar)
    outputs.file(outputJar)

    doFirst {
        val outputFile = outputJar.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.delete()
        commandLine(
            resolvedD8Executable.absolutePath,
            "--min-api",
            resolvedMinSdkVersion.toString(),
            "--lib",
            resolvedAndroidJarFile.absolutePath,
            "--output",
            outputFile.absolutePath,
            dotnetAndroidCryptoSupportJar.absolutePath,
        )
    }
}

val stageWindowsTExtensionPackage = tasks.register<Sync>("stageWindowsTExtensionPackage") {
    notCompatibleWithConfigurationCache("TExtension packaging stages generated archives with runtime-specific paths.")
    dependsOn(generateWindowsTExtensionManifest)
    dependsOn(windowsRuntimeJar)

    includeEmptyDirs = false
    into(packageRootDir(windowsTExtensionTarget))
    from(manifestFile(windowsTExtensionTarget))
    from(commonMainAssetsDir) {
        into("resources")
    }
    from(windowsMainAssetsDir.resolve("i18n")) {
        into("resources/i18n")
    }
    from(windowsRuntimeJar.flatMap { it.archiveFile }) {
        into(windowsTExtensionTarget.runtimeArtifactPath.substringBeforeLast('/'))
        rename { windowsTExtensionTarget.runtimeArtifactPath.substringAfterLast('/') }
    }
}

val stageAndroidTExtensionPackage = tasks.register<Sync>("stageAndroidTExtensionPackage") {
    notCompatibleWithConfigurationCache("TExtension packaging stages generated Android runtime and resource archives.")
    dependsOn(generateAndroidTExtensionManifest)
    dependsOn(dexAndroidTExtensionRuntimeJar)
    dependsOn(buildSts2MobileDll)
    dependsOn(prepareFmodDexLibrary)
    dependsOn(prepareGodotDexLibrary)
    dependsOn(prepareDotnetCryptoDexLibrary)

    includeEmptyDirs = false
    into(packageRootDir(androidTExtensionTarget))
    from(manifestFile(androidTExtensionTarget))
    from(commonMainAssetsDir) {
        into("resources")
    }
    from(androidMainAssetsDir.resolve("i18n")) {
        into("resources/i18n")
    }
    from(generatedAndroidRuntimeJarFile) {
        into(androidTExtensionTarget.runtimeArtifactPath.substringBeforeLast('/'))
        rename { androidTExtensionTarget.runtimeArtifactPath.substringAfterLast('/') }
    }
    from(dotnetBclDir) {
        into("resources/runtime/dotnet_bcl")
    }
    from(sts2MobileReleaseDir) {
        include("STS2Mobile.dll", "STS2Mobile.deps.json")
        into("resources/runtime/patch")
    }
    from(androidNativeLibDir) {
        into(androidNativeResourcePath)
        exclude(androidReleaseNativeLibraryExcludes)
    }
    from(generatedAndroidDexLibraryDir) {
        include("classes.jar")
        include("fmod.jar")
        include("libSystem.Security.Cryptography.Native.Android.jar")
        into("resources/android/libs")
    }
}

val packageWindowsTExtension = tasks.register<Zip>("packageWindowsTExtension") {
    group = "TExtension"
    description = "Packages the Windows STS2 template extension as a .textension file."
    notCompatibleWithConfigurationCache("TExtension packaging is intentionally isolated from Gradle configuration cache.")

    dependsOn(stageWindowsTExtensionPackage)
    archiveFileName.set("${windowsTExtensionTarget.extensionId}-$packageVersion.textension")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
    includeEmptyDirs = false
    from(packageRootDir(windowsTExtensionTarget))
}

val packageAndroidTExtension = tasks.register<Zip>("packageAndroidTExtension") {
    group = "TExtension"
    description = "Packages the Android STS2 template extension as a .textension file."
    notCompatibleWithConfigurationCache("TExtension packaging is intentionally isolated from Gradle configuration cache.")

    dependsOn(stageAndroidTExtensionPackage)
    archiveFileName.set("${androidTExtensionTarget.extensionId}-$packageVersion.textension")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
    includeEmptyDirs = false
    from(packageRootDir(androidTExtensionTarget))
}

tasks.register("packageTExtension") {
    group = "TExtension"
    description = "Builds every supported platform .textension package from this single extension project."
    dependsOn(packageWindowsTExtension)
    dependsOn(packageAndroidTExtension)
}

fun registerGenerateTExtensionManifest(target: TExtensionPackageTarget) =
    tasks.register("generate${target.name.capitalizedTaskName()}TExtensionManifest") {
        notCompatibleWithConfigurationCache("TExtension packaging writes generated package metadata during task execution.")
        val outputFile = manifestFile(target)
        outputs.file(outputFile)

        doLast {
            val manifest = outputFile.get().asFile
            manifest.parentFile.mkdirs()
            manifest.writeText(buildManifestJson(target))
        }
    }

fun buildManifestJson(target: TExtensionPackageTarget): String {
    val capabilitiesJson = target.capabilities.joinToString(",\n") { capability ->
        "    \"$capability\""
    }
    return """
        {
          "id": "${target.extensionId}",
          "kind": "TEMPLATE",
          "displayName": "Slay the Spire 2",
          "version": "$packageVersion",
          "apiVersion": "$extensionApiVersion",
          "supportedTargets": ["${target.platformTarget}"],
          "capabilities": [
        $capabilitiesJson
          ],
          "compatibility": {
            "packageFormatVersion": 1,
            "minSdkApiVersion": $minSdkApiVersion,
            "targetSdkApiVersion": $targetSdkApiVersion
          },
          "entrypoints": {
            "${target.platformTarget}": "${target.entrypoint}"
          },
          "runtimeArtifacts": {
            "${target.platformTarget}": "${target.runtimeArtifactPath}"
          }
        }
    """.trimIndent()
}

fun manifestFile(target: TExtensionPackageTarget) =
    layout.buildDirectory.file("generated/textension/${target.name}/manifest.json")

fun packageRootDir(target: TExtensionPackageTarget) =
    layout.buildDirectory.dir("textension/${target.name}/package")

fun sourceSetAssetsDir(sourceSetName: String): File {
    return projectDir.resolve("src/$sourceSetName/assets")
}

fun String.capitalizedTaskName(): String {
    return replaceFirstChar { character -> character.uppercaseChar() }
}

fun resolveDotnetAndroidCryptoSupportJar(): File {
    val programFilesDirectory = sequenceOf(
        System.getenv("ProgramFiles"),
        "C:/Program Files",
    ).map(::File).firstOrNull(File::isDirectory)
        ?: error("Program Files directory was not found while resolving the .NET Android crypto support jar.")
    val monoAndroidRuntimePackRoot = programFilesDirectory
        .resolve("dotnet/packs/Microsoft.NETCore.App.Runtime.Mono.android-arm64")
    require(monoAndroidRuntimePackRoot.isDirectory) {
        ".NET Android runtime pack root was not found at $monoAndroidRuntimePackRoot"
    }

    val packDirectories = monoAndroidRuntimePackRoot.listFiles()
        .orEmpty()
        .filter(File::isDirectory)
        .sortedWith(
            compareByDescending<File> { parseVersion(directory = it.name, index = 0) }
                .thenByDescending { parseVersion(directory = it.name, index = 1) }
                .thenByDescending { parseVersion(directory = it.name, index = 2) }
                .thenByDescending { parseVersion(directory = it.name, index = 3) },
        )
    val preferredPackDirectory = packDirectories.firstOrNull { directory ->
        directory.name.startsWith("9.")
    } ?: packDirectories.firstOrNull()
        ?: error("No .NET Android runtime pack versions were found in $monoAndroidRuntimePackRoot")

    val cryptoSupportJar = preferredPackDirectory
        .resolve("runtimes/android-arm64/native/libSystem.Security.Cryptography.Native.Android.jar")
    require(cryptoSupportJar.isFile) {
        "The .NET Android crypto support jar was not found at $cryptoSupportJar"
    }
    return cryptoSupportJar
}

fun parseVersion(
    directory: String,
    index: Int,
): Int {
    return directory.split('.', '-', '_')
        .mapNotNull(String::toIntOrNull)
        .getOrElse(index) { 0 }
}

fun resolveAndroidSdkDirectory(projectDir: File): File {
    val localProperties = projectDir.resolve("local.properties")
    val localSdkDir = if (localProperties.isFile) {
        Properties().apply {
            localProperties.inputStream().use { input ->
                load(input)
            }
        }.getProperty("sdk.dir")
    } else {
        null
    }
    val resolvedPath = sequenceOf(
        localSdkDir,
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
    ).firstOrNull { value -> !value.isNullOrBlank() }
        ?: error("Android SDK path was not found. Set sdk.dir in local.properties or ANDROID_HOME/ANDROID_SDK_ROOT.")
    return File(resolvedPath)
}

fun resolveAndroidBuildToolExecutable(
    androidSdkDirectory: File,
    toolName: String,
): File {
    val executableName = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        "$toolName.bat"
    } else {
        toolName
    }
    val buildToolsRoot = androidSdkDirectory.resolve("build-tools")
    require(buildToolsRoot.isDirectory) {
        "Android build-tools directory was not found at $buildToolsRoot"
    }
    return buildToolsRoot.listFiles()
        .orEmpty()
        .filter(File::isDirectory)
        .sortedWith(
            compareByDescending<File> { parseVersion(directory = it.name, index = 0) }
                .thenByDescending { parseVersion(directory = it.name, index = 1) }
                .thenByDescending { parseVersion(directory = it.name, index = 2) },
        )
        .map { directory -> directory.resolve(executableName) }
        .firstOrNull(File::isFile)
        ?: error("Unable to locate $executableName under $buildToolsRoot")
}
