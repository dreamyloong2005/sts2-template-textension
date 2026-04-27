package com.dreamyloong.template.sts2.android

import com.dreamyloong.tlauncher.sdk.account.LauncherAccount
import com.dreamyloong.tlauncher.sdk.account.LauncherAccountProvider
import com.dreamyloong.tlauncher.sdk.account.SteamAccountLoginMode
import com.dreamyloong.template.sts2.STS2_ANDROID_TEMPLATE_ID
import com.dreamyloong.template.sts2.STS2_DISPLAY_NAME
import com.dreamyloong.template.sts2.STS2_MOD_ARTIFACT_DETECTED
import com.dreamyloong.template.sts2.STS2_MOD_ARTIFACT_NOT_DETECTED
import com.dreamyloong.template.sts2.STS2_MOD_ARTIFACT_NOT_REQUIRED
import com.dreamyloong.template.sts2.STS2_STEAM_BRANCH_PUBLIC
import com.dreamyloong.template.sts2.STS2_STEAM_BRANCH_PUBLIC_BETA
import com.dreamyloong.template.sts2.STS2_TEMPLATE_ID
import com.dreamyloong.template.sts2.Sts2GameFileCheckMode
import com.dreamyloong.template.sts2.Sts2GameFileCheckSnapshot
import com.dreamyloong.template.sts2.Sts2GameFileCheckStatus
import com.dreamyloong.template.sts2.Sts2GameFileDownloadSnapshot
import com.dreamyloong.template.sts2.Sts2GameFileDownloadStatus
import com.dreamyloong.template.sts2.Sts2GameFilePreferences
import com.dreamyloong.template.sts2.Sts2SteamVerificationRecord
import com.dreamyloong.template.sts2.Sts2SteamVerificationStatus
import com.dreamyloong.template.sts2.Sts2ModImportCoordinator
import com.dreamyloong.template.sts2.Sts2ModManifest
import com.dreamyloong.template.sts2.Sts2ModImportStatus
import com.dreamyloong.template.sts2.Sts2ModImportStatusKind
import com.dreamyloong.template.sts2.Sts2ModPendingReplacement
import com.dreamyloong.template.sts2.Sts2ModPendingReplacementKind
import com.dreamyloong.template.sts2.PersistentSts2VersionStore
import com.dreamyloong.template.sts2.Sts2LaunchSettingsDraft
import com.dreamyloong.template.sts2.Sts2LaunchSettingsValidationError
import com.dreamyloong.template.sts2.Sts2ModSettingsSnapshot
import com.dreamyloong.template.sts2.Sts2ResolvedModEnabledState
import com.dreamyloong.template.sts2.Sts2ScannedMod
import com.dreamyloong.template.sts2.Sts2ScannedModIssue
import com.dreamyloong.template.sts2.Sts2ModScanResult
import com.dreamyloong.template.sts2.Sts2VersionDefinition
import com.dreamyloong.template.sts2.Sts2VersionDraft
import com.dreamyloong.template.sts2.Sts2VersionStore
import com.dreamyloong.template.sts2.Sts2VersionValidationError
import com.dreamyloong.template.sts2.Sts2TemplateLocalizationTarget
import com.dreamyloong.template.sts2.Sts2TemplateStrings
import com.dreamyloong.template.sts2.deleteSts2ScannedMod
import com.dreamyloong.template.sts2.isSts2ModVersionUpdate
import com.dreamyloong.template.sts2.loadSts2TemplateStrings
import com.dreamyloong.template.sts2.normalizeSteamBranch
import com.dreamyloong.template.sts2.readSts2ModSettingsSnapshot
import com.dreamyloong.template.sts2.removeSts2ModSettingsEntry
import com.dreamyloong.template.sts2.resolveSts2ModEnabledState
import com.dreamyloong.template.sts2.scanSts2LocalMods
import com.dreamyloong.template.sts2.sts2Localized as localized
import com.dreamyloong.template.sts2.updateSts2ModEnabledState
import com.dreamyloong.tlauncher.sdk.extension.ExtensionCapability
import com.dreamyloong.tlauncher.sdk.extension.ExtensionCompatibility
import com.dreamyloong.tlauncher.sdk.extension.ExtensionContext
import com.dreamyloong.tlauncher.sdk.extension.ExtensionEntrypoint
import com.dreamyloong.tlauncher.sdk.extension.ExtensionFeature
import com.dreamyloong.tlauncher.sdk.extension.ExtensionHostPaths
import com.dreamyloong.tlauncher.sdk.extension.ExtensionPackageResources
import com.dreamyloong.tlauncher.sdk.extension.LauncherExtension
import com.dreamyloong.tlauncher.sdk.i18n.SupportedLanguage
import com.dreamyloong.tlauncher.sdk.model.ExtensionIdentityId
import com.dreamyloong.tlauncher.sdk.model.ExtensionKind
import com.dreamyloong.tlauncher.sdk.model.ExtensionManifest
import com.dreamyloong.tlauncher.sdk.model.GameInstance
import com.dreamyloong.tlauncher.sdk.model.LaunchSupportLevel
import com.dreamyloong.tlauncher.sdk.model.PlatformTarget
import com.dreamyloong.tlauncher.sdk.model.RuntimeRequirement
import com.dreamyloong.tlauncher.sdk.model.TemplateReleaseState
import com.dreamyloong.tlauncher.sdk.model.TemplateSourceType
import com.dreamyloong.tlauncher.sdk.page.PageActionRegistration
import com.dreamyloong.tlauncher.sdk.page.PageActionStyle
import com.dreamyloong.tlauncher.sdk.page.PageChoiceOptionRegistration
import com.dreamyloong.tlauncher.sdk.page.PageContext
import com.dreamyloong.tlauncher.sdk.page.PageContributionBundle
import com.dreamyloong.tlauncher.sdk.page.PageFooterLayoutRegistration
import com.dreamyloong.tlauncher.sdk.page.PageIds
import com.dreamyloong.tlauncher.sdk.page.PageNodePlacement
import com.dreamyloong.tlauncher.sdk.page.PageProgressRegistration
import com.dreamyloong.tlauncher.sdk.page.PageRegistration
import com.dreamyloong.tlauncher.sdk.page.PageSectionRegistration
import com.dreamyloong.tlauncher.sdk.page.PageTextDirect
import com.dreamyloong.tlauncher.sdk.page.PageValueItemRegistration
import com.dreamyloong.tlauncher.sdk.page.PageWidgetButtonStack
import com.dreamyloong.tlauncher.sdk.page.PageWidgetChoiceCard
import com.dreamyloong.tlauncher.sdk.page.PageWidgetDirectoryInputCard
import com.dreamyloong.tlauncher.sdk.page.PageWidgetDetailCard
import com.dreamyloong.tlauncher.sdk.page.PageWidgetLaunchBar
import com.dreamyloong.tlauncher.sdk.page.PageWidgetProgressCard
import com.dreamyloong.tlauncher.sdk.page.PageWidgetRegistration
import com.dreamyloong.tlauncher.sdk.page.PageWidgetTextInputCard
import com.dreamyloong.tlauncher.sdk.page.PageWidgetTone
import com.dreamyloong.tlauncher.sdk.platform.GameLaunchRequest
import com.dreamyloong.tlauncher.sdk.page.PageWidgetToggleCard
import com.dreamyloong.tlauncher.sdk.plugin.PageContributionProviderExtension
import com.dreamyloong.tlauncher.sdk.template.TemplateFileCheckResult
import com.dreamyloong.tlauncher.sdk.template.TemplatePackage
import com.dreamyloong.tlauncher.sdk.template.TemplatePlatformFacet
import com.dreamyloong.tlauncher.sdk.template.TemplateProviderExtension

private const val LAUNCH_PAGE_ID = "page.template.sts2.launch"
private const val MOD_MANAGER_PAGE_ID = "page.template.sts2.mod_manager"
private const val SHADER_CACHE_MANAGER_PAGE_ID = "page.template.sts2.shader_cache_manager"
private const val RUNTIME_MANAGER_PAGE_ID = "page.template.sts2.runtime_manager"
private const val VERSION_MANAGER_PAGE_ID = "page.template.sts2.version_manager"
private const val VERSION_CREATE_PAGE_ID = "page.template.sts2.version_create"
private const val STS2_PACK_FILE_NAME = "SlayTheSpire2.pck"
private const val STS2_LAUNCH_CONTEXT_FILE_NAME = "sts2-launch-context.json"
private const val STS2_NATIVE_LIBRARY_DIRECTORY = "android/jniLibs/arm64-v8a"
private const val STS2_GODOT_CLASSES_JAR_PATH = "android/libs/classes.jar"
private const val STS2_FMOD_JAR_PATH = "android/libs/fmod.jar"
private const val STS2_ANDROID_CRYPTO_SUPPORT_JAR_PATH = "android/libs/libSystem.Security.Cryptography.Native.Android.jar"
private const val STS2_ANDROID_RUNTIME_BRIDGE_CLASS =
    "com.dreamyloong.template.sts2.android.AndroidNativeLibraryLoadBridge"
private val sts2TemplatePackageId = ExtensionIdentityId(STS2_TEMPLATE_ID)

object Sts2TemplateAndroidEntrypoint : ExtensionEntrypoint {
    override fun createExtension(): LauncherExtension = Sts2TemplateAndroidExtension()
}

private class Sts2TemplateAndroidExtension : LauncherExtension {
    override val extension: ExtensionManifest = ExtensionManifest(
        id = STS2_ANDROID_TEMPLATE_ID,
        kind = ExtensionKind.TEMPLATE,
        supportedTargets = setOf(PlatformTarget.ANDROID),
        capabilities = setOf(
            ExtensionCapability.DEFINE_TEMPLATE_METADATA,
            ExtensionCapability.DEFINE_TEMPLATE_RUNTIME_REQUIREMENTS,
            ExtensionCapability.PROVIDE_TEMPLATE_PAGE_CONTRIBUTIONS,
        ),
        compatibility = ExtensionCompatibility(),
    )
    override val displayName: String = STS2_DISPLAY_NAME
    override val version: String = "1.0.0"
    override val apiVersion: String = "1.0.0"
    override val entrypoint: String = Sts2TemplateAndroidEntrypoint::class.qualifiedName.orEmpty()

    override fun createFeatures(context: ExtensionContext): List<ExtensionFeature> {
        val templateStrings = loadSts2TemplateStrings(
            resources = context.packageResources,
            target = Sts2TemplateLocalizationTarget.ANDROID,
        )
        return listOf(
            Sts2TemplateAndroidProvider(templateStrings),
            Sts2TemplateAndroidPageContributionProvider(
                resources = context.packageResources,
                versionStore = PersistentSts2VersionStore(context.stateStore),
                gameFileCoordinator = Sts2SteamDepotGameFileCoordinator(
                    stateStore = context.stateStore,
                    hostPaths = context.hostPaths,
                    steamDepot = context.hostServices.steamDepot,
                ),
                modImportCoordinator = Sts2ModImportCoordinator(
                    stateStore = context.stateStore,
                    hostPaths = context.hostPaths,
                ),
                shaderCacheStore = Sts2AndroidShaderCacheStore(context.stateStore),
                hostPaths = context.hostPaths,
            ),
        )
    }
}

private class Sts2TemplateAndroidProvider(
    private val strings: Sts2TemplateStrings,
) : TemplateProviderExtension {
    override fun provideTemplatePackages(): List<TemplatePackage> {
        return listOf(
            TemplatePackage(
                extension = ExtensionManifest(
                    id = STS2_ANDROID_TEMPLATE_ID,
                    kind = ExtensionKind.TEMPLATE,
                    supportedTargets = setOf(PlatformTarget.ANDROID),
                    capabilities = setOf(
                        ExtensionCapability.DEFINE_TEMPLATE_METADATA,
                        ExtensionCapability.DEFINE_TEMPLATE_RUNTIME_REQUIREMENTS,
                        ExtensionCapability.PROVIDE_TEMPLATE_PAGE_CONTRIBUTIONS,
                    ),
                    compatibility = ExtensionCompatibility(),
                ),
                schemaVersion = 1,
                name = strings.name,
                description = strings.targetDescription,
                defaultInstanceDescription = strings.defaultInstanceDescription,
                sourceType = TemplateSourceType.OFFICIAL,
                releaseState = TemplateReleaseState.EXPERIMENTAL,
                notes = strings.targetNotes,
                platforms = listOf(
                    TemplatePlatformFacet(
                        target = PlatformTarget.ANDROID,
                        supportLevel = LaunchSupportLevel.LAUNCHABLE,
                        runtimeRequirements = listOf(
                            RuntimeRequirement(
                                engine = "Godot",
                                language = "CSharp",
                                version = "STS2",
                            ),
                        ),
                        capabilityKeys = setOf("download", "patch", "launch", "save"),
                        capabilityLabels = strings.capabilityLabels,
                    ),
                ),
            ),
        )
    }
}

private class Sts2TemplateAndroidPageContributionProvider(
    private val resources: ExtensionPackageResources,
    private val versionStore: Sts2VersionStore,
    private val gameFileCoordinator: Sts2SteamDepotGameFileCoordinator,
    private val modImportCoordinator: Sts2ModImportCoordinator,
    private val shaderCacheStore: Sts2AndroidShaderCacheStore,
    private val hostPaths: ExtensionHostPaths,
) : PageContributionProviderExtension {
    private var cachedHomeComputedState: Sts2HomeComputedState? = null

    override fun providePageContributions(context: PageContext): List<PageContributionBundle> {
        val template = context.currentTemplate ?: return emptyList()
        val currentGame = context.currentGame ?: return emptyList()
        if (template.packageId != sts2TemplatePackageId || context.target != PlatformTarget.ANDROID) {
            return emptyList()
        }
        val language = context.strings.language
        val versions = versionStore.versions(currentGame.id)
        val selectedClientId = versionStore.selectedClientId(currentGame.id)
        val selectedVersion = selectedClientId?.let { clientId ->
            versions.firstOrNull { version -> version.clientId == clientId }
        }
        val shaderCacheEnabled = selectedVersion?.let { version ->
            shaderCacheStore.isEnabled(currentGame.id, version.clientId)
        } ?: false
        val shaderWarmupRequired = selectedVersion?.let { version ->
            shaderCacheEnabled &&
                version.gameDirectory.isNotBlank() &&
                sts2ShaderWarmupNeedsRun(version, hostPaths)
        } == true
        val shaderCacheEntries = listSts2ShaderCacheEntries(
            hostPaths = hostPaths,
            versions = versions,
            selectedVersion = selectedVersion,
        )
        val gameFilePreferences = gameFileCoordinator.preferences(currentGame.id)
        val selectedSteamBranch = normalizeSteamBranch(gameFilePreferences.steamBranch)
        val savedSteamAccounts = context.accounts.filter { account ->
            account.provider == LauncherAccountProvider.STEAM
        }
        val steamAccounts = savedSteamAccounts.filter { account -> account.supportsSts2SteamDepot() }
        val selectedSteamAccount = steamAccounts.firstOrNull { account ->
            account.subjectId == gameFilePreferences.selectedSteamAccountSubjectId
        }
        val manageStorageGranted = context.manageStorageAccessState?.isGranted == true
        val gameFileState = currentGameFileCheck(
            language = language,
            version = selectedVersion,
            manageStorageGranted = manageStorageGranted,
            steamVerificationEnabled = gameFilePreferences.steamVerificationEnabled,
            steamBranch = selectedSteamBranch,
            selectedSteamAccount = selectedSteamAccount,
            storedSnapshot = gameFileCoordinator.checkSnapshot(currentGame.id),
            steamVerificationRecord = gameFileCoordinator.steamVerificationRecord(
                instanceId = currentGame.id,
                version = selectedVersion,
                steamBranch = selectedSteamBranch,
            ),
        )
        val downloadSnapshot = currentDownloadSnapshot(
            version = selectedVersion,
            steamBranch = selectedSteamBranch,
            selectedSteamAccount = selectedSteamAccount,
            storedSnapshot = gameFileCoordinator.downloadSnapshot(currentGame.id),
        )
        val gameFileTaskRunning =
            gameFileState.status == Sts2GameFileCheckStatus.RUNNING ||
                downloadSnapshot.status == Sts2GameFileDownloadStatus.RUNNING
        val homeComputedState = resolveHomeComputedState(
            currentGame = currentGame,
            selectedVersion = selectedVersion,
            manageStorageGranted = manageStorageGranted,
            reuseCached = gameFileTaskRunning,
        )
        val runtimeReport = homeComputedState.runtimeReport
        val runtimeFiles = homeComputedState.runtimeFiles
        val modScanResult = homeComputedState.modScanResult
        val modSettingsSnapshot = homeComputedState.modSettingsSnapshot
        val modImportStatus = modImportCoordinator.status(currentGame.id)
        val pendingModReplacement = modImportCoordinator.pendingReplacement(currentGame.id)
        val quickFixRuntime = {
            runCatching {
                repairSts2Runtime(
                    resources = resources,
                    hostPaths = hostPaths,
                    selectedGameDirectory = selectedVersion?.gameDirectory,
                    manageStorageGranted = manageStorageGranted,
                )
            }.getOrDefault(false)
        }
        val runGameFileCheck = {
            if (gameFilePreferences.steamVerificationEnabled) {
                gameFileCoordinator.startSteamCheck(
                    instanceId = currentGame.id,
                    language = language,
                    version = selectedVersion,
                    selectedSteamAccount = selectedSteamAccount,
                    steamBranch = selectedSteamBranch,
                    manageStorageGranted = manageStorageGranted,
                    onStateChanged = { context.refreshPage() },
                )
            } else {
                context.refreshPage()
            }
        }
        val downloadGameFiles = {
            gameFileCoordinator.startSelectiveDownload(
                instanceId = currentGame.id,
                language = language,
                version = selectedVersion,
                selectedSteamAccount = selectedSteamAccount,
                steamBranch = selectedSteamBranch,
                manageStorageGranted = manageStorageGranted,
                onStateChanged = { context.refreshPage() },
            )
        }
        val cancelGameFileCheck = {
            gameFileCoordinator.cancelSteamCheck(
                instanceId = currentGame.id,
                language = language,
                onStateChanged = { context.refreshPage() },
            )
        }
        val cancelGameFileDownload = {
            gameFileCoordinator.cancelSelectiveDownload(
                instanceId = currentGame.id,
                language = language,
                onStateChanged = { context.refreshPage() },
            )
        }
        val pauseGameFileDownload = {
            gameFileCoordinator.pauseSelectiveDownload(
                instanceId = currentGame.id,
                language = language,
                onStateChanged = { context.refreshPage() },
            )
        }
        val importModPackage = context.filePickerState
            ?.takeIf { filePicker -> filePicker.isSupported }
            ?.let { filePicker ->
                {
                    filePicker.pickFile(
                        listOf(
                            "application/json",
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream",
                            "*/*",
                        ),
                    ) { pickedFile ->
                        if (pickedFile != null) {
                            modImportCoordinator.importPickedFile(
                                instanceId = currentGame.id,
                                language = language,
                                selectedVersion = selectedVersion,
                                pickedFileName = pickedFile.name,
                                bytes = pickedFile.bytes,
                            )
                            context.refreshPage()
                        }
                    }
                }
            }
        val confirmPendingModReplacement = {
            modImportCoordinator.confirmPendingReplacement(currentGame.id, language)
            context.refreshPage()
        }
        val cancelPendingModReplacement = {
            modImportCoordinator.cancelPendingReplacement(currentGame.id, language)
            context.refreshPage()
        }
        val launchRequest = context.applyPreparedLaunchRequestInterceptors(
            request = buildSts2LaunchRequest(
                game = currentGame,
                version = selectedVersion,
                gameFileCheck = gameFileState.launchCheck,
                runtimeReady = runtimeReport?.isComplete == true,
                packageName = hostPaths.packageName,
                launchMode = if (shaderWarmupRequired) {
                    STS2_LAUNCH_MODE_SHADER_WARMUP
                } else {
                    STS2_LAUNCH_MODE_GAME
                },
                shaderCacheEnabled = shaderCacheEnabled,
            ),
            templatePackageId = sts2TemplatePackageId,
            selectedGameDirectory = selectedVersion?.gameDirectory,
        ) as? GameLaunchRequest.AndroidRuntime
        val createDraft = versionStore.createDraft(currentGame.id)
        val createError = versionStore.createValidationError(currentGame.id)
        val suggestedCreateClientId = versionStore.suggestedCreateClientId(currentGame.id)

        return buildList {
            add(
                buildHomeContribution(
                    context = context,
                    selectedVersion = selectedVersion,
                    savedSteamAccounts = savedSteamAccounts,
                    steamAccounts = steamAccounts,
                    selectedSteamAccount = selectedSteamAccount,
                    gameFilePreferences = gameFilePreferences,
                    selectedSteamBranch = selectedSteamBranch,
                    gameFileState = gameFileState,
                    downloadSnapshot = downloadSnapshot,
                    modScanResult = modScanResult,
                    runtimeReport = runtimeReport,
                    launchRequest = launchRequest,
                    shaderWarmupRequired = shaderWarmupRequired,
                    onQuickFixRuntime = quickFixRuntime,
                    onRunGameFileCheck = runGameFileCheck,
                    onDownloadGameFiles = downloadGameFiles,
                    onCancelGameFileCheck = cancelGameFileCheck,
                    onCancelGameFileDownload = cancelGameFileDownload,
                    onPauseGameFileDownload = pauseGameFileDownload,
                    onToggleSteamVerification = { enabled ->
                        gameFileCoordinator.updateSteamVerificationEnabled(
                            instanceId = currentGame.id,
                            enabled = enabled,
                            language = language,
                            onStateChanged = { context.refreshPage() },
                        )
                        context.refreshPage()
                    },
                    onSelectSteamAccount = { subjectId ->
                        gameFileCoordinator.updateSelectedSteamAccount(currentGame.id, subjectId)
                        context.refreshPage()
                    },
                    onSelectSteamBranch = { branch ->
                        gameFileCoordinator.updateSteamBranch(
                            instanceId = currentGame.id,
                            steamBranch = branch,
                            language = language,
                            onStateChanged = { context.refreshPage() },
                        )
                        context.refreshPage()
                    },
                ),
            )
            add(
                buildLaunchContribution(
                    context = context,
                    game = currentGame,
                    selectedVersion = selectedVersion,
                    versionStore = versionStore,
                    shaderCacheEnabled = shaderCacheEnabled,
                    shaderCacheEntries = shaderCacheEntries,
                    onShaderCacheEnabledChange = { enabled ->
                        selectedVersion?.let { version ->
                            shaderCacheStore.setEnabled(currentGame.id, version.clientId, enabled)
                        }
                        context.refreshPage()
                    },
                ),
            )
            add(
                buildShaderCacheManagerContribution(
                    context = context,
                    selectedVersion = selectedVersion,
                    shaderCacheEntries = shaderCacheEntries,
                    onDeleteCacheEntry = { entry ->
                        deleteSts2ShaderCacheEntry(hostPaths, entry, versions)
                        context.refreshPage()
                    },
                ),
            )
            add(
                buildRuntimeManagerContribution(
                    context = context,
                    runtimeReport = runtimeReport,
                    runtimeFiles = runtimeFiles,
                    onQuickFixRuntime = quickFixRuntime,
                    onImportDll = context.filePickerState?.let { filePicker ->
                        {
                            filePicker.pickFile(
                                listOf("application/x-msdownload", "application/octet-stream", "*/*"),
                            ) { pickedFile ->
                                if (
                                    pickedFile != null &&
                                    importSts2RuntimeDll(hostPaths, pickedFile.name, pickedFile.bytes)
                                ) {
                                    context.refreshPage()
                                }
                            }
                        }
                    },
                    onDeleteRuntimeFile = { fileName ->
                        if (deleteSts2RuntimeFile(hostPaths, fileName)) {
                            context.refreshPage()
                        }
                    },
                ),
            )
            add(
                buildModManagerContribution(
                    context = context,
                    selectedVersion = selectedVersion,
                    modScanResult = modScanResult,
                    modSettingsSnapshot = modSettingsSnapshot,
                    modImportStatus = modImportStatus,
                    pendingReplacement = pendingModReplacement,
                    onImportModPackage = importModPackage,
                    onConfirmPendingReplacement = confirmPendingModReplacement,
                    onCancelPendingReplacement = cancelPendingModReplacement,
                ),
            )
            modScanResult?.mods?.forEach { mod ->
                add(
                    buildModDetailContribution(
                        context = context,
                        selectedVersion = selectedVersion,
                        mod = mod,
                        modEnabledState = resolveSts2ModEnabledState(modSettingsSnapshot, mod.manifest.id),
                        pendingReplacement = pendingModReplacement,
                        onSetModEnabled = { enabled ->
                            updateSts2ModEnabledState(
                                selectedVersion = selectedVersion,
                                modId = mod.manifest.id,
                                enabled = enabled,
                            )
                            context.refreshPage()
                        },
                        onImportPck = if (mod.manifest.hasPck) {
                            context.filePickerState?.takeIf { filePicker -> filePicker.isSupported }?.let { filePicker ->
                                {
                                    filePicker.pickFile(
                                        listOf("application/octet-stream", "*/*"),
                                    ) { pickedFile ->
                                        if (pickedFile != null) {
                                            modImportCoordinator.importArtifact(
                                                instanceId = currentGame.id,
                                                language = language,
                                                mod = mod,
                                                artifactKind = Sts2ModPendingReplacementKind.PCK,
                                                pickedFileName = pickedFile.name,
                                                bytes = pickedFile.bytes,
                                            )
                                            context.refreshPage()
                                        }
                                    }
                                }
                            }
                        } else {
                            null
                        },
                        onImportDll = if (mod.manifest.hasDll) {
                            context.filePickerState?.takeIf { filePicker -> filePicker.isSupported }?.let { filePicker ->
                                {
                                    filePicker.pickFile(
                                        listOf("application/x-msdownload", "application/octet-stream", "*/*"),
                                    ) { pickedFile ->
                                        if (pickedFile != null) {
                                            modImportCoordinator.importArtifact(
                                                instanceId = currentGame.id,
                                                language = language,
                                                mod = mod,
                                                artifactKind = Sts2ModPendingReplacementKind.DLL,
                                                pickedFileName = pickedFile.name,
                                                bytes = pickedFile.bytes,
                                            )
                                            context.refreshPage()
                                        }
                                    }
                                }
                            }
                        } else {
                            null
                        },
                        onConfirmPendingReplacement = confirmPendingModReplacement,
                        onCancelPendingReplacement = cancelPendingModReplacement,
                        onDeleteMod = {
                            if (deleteSts2ScannedMod(selectedVersion, mod)) {
                                removeSts2ModSettingsEntry(selectedVersion, mod.manifest.id)
                                context.replaceCurrentPage(MOD_MANAGER_PAGE_ID)
                            } else {
                                context.refreshPage()
                            }
                        },
                    ),
                )
            }
            add(
                buildVersionManagerContribution(
                    context = context,
                    game = currentGame,
                    versions = versions,
                    selectedClientId = selectedClientId,
                    versionStore = versionStore,
                ),
            )
            add(
                buildCreateVersionContribution(
                    context = context,
                    game = currentGame,
                    draft = createDraft,
                    validationError = createError,
                    suggestedClientId = suggestedCreateClientId,
                    versionStore = versionStore,
                ),
            )
            versions.forEach { version ->
                add(
                    buildVersionDetailContribution(
                        context = context,
                        game = currentGame,
                        version = version,
                        selected = version.clientId == selectedClientId,
                        draft = versionStore.editDraft(currentGame.id, version.clientId) ?: version.toDraft(),
                        validationError = versionStore.editValidationError(currentGame.id, version.clientId),
                        versionStore = versionStore,
                    ),
                )
            }
        }
    }

    private fun resolveHomeComputedState(
        currentGame: GameInstance,
        selectedVersion: Sts2VersionDefinition?,
        manageStorageGranted: Boolean,
        reuseCached: Boolean,
    ): Sts2HomeComputedState {
        val key = Sts2HomeComputedStateKey(
            gameId = currentGame.id.value,
            selectedClientId = selectedVersion?.clientId,
            selectedGameDirectory = selectedVersion?.gameDirectory?.trim(),
            selectedSaveDirectory = selectedVersion?.saveDirectory?.trim(),
            selectedModDirectory = selectedVersion?.modDirectory?.trim(),
            manageStorageGranted = manageStorageGranted,
        )
        cachedHomeComputedState
            ?.takeIf { cached -> reuseCached && cached.key == key }
            ?.let { cached -> return cached }
        return Sts2HomeComputedState(
            key = key,
            runtimeReport = runCatching {
                diagnoseSts2Runtime(
                    resources = resources,
                    hostPaths = hostPaths,
                    selectedGameDirectory = selectedVersion?.gameDirectory,
                    manageStorageGranted = manageStorageGranted,
                )
            }.getOrNull(),
            runtimeFiles = runCatching {
                listSts2RuntimeFiles(hostPaths)
            }.getOrDefault(emptyList()),
            modScanResult = scanSts2LocalMods(selectedVersion),
            modSettingsSnapshot = readSts2ModSettingsSnapshot(selectedVersion),
        ).also { computed ->
            cachedHomeComputedState = computed
        }
    }
}

private fun buildHomeContribution(
    context: PageContext,
    selectedVersion: Sts2VersionDefinition?,
    savedSteamAccounts: List<LauncherAccount>,
    steamAccounts: List<LauncherAccount>,
    selectedSteamAccount: LauncherAccount?,
    gameFilePreferences: Sts2GameFilePreferences,
    selectedSteamBranch: String,
    gameFileState: Sts2GameFilePanelState,
    downloadSnapshot: Sts2GameFileDownloadSnapshot,
    modScanResult: Sts2ModScanResult?,
    runtimeReport: Sts2GodotRuntimeReport?,
    launchRequest: GameLaunchRequest.AndroidRuntime?,
    shaderWarmupRequired: Boolean,
    onQuickFixRuntime: () -> Boolean,
    onRunGameFileCheck: () -> Unit,
    onDownloadGameFiles: () -> Unit,
    onCancelGameFileCheck: () -> Unit,
    onCancelGameFileDownload: () -> Unit,
    onPauseGameFileDownload: () -> Unit,
    onToggleSteamVerification: (Boolean) -> Unit,
    onSelectSteamAccount: (String?) -> Unit,
    onSelectSteamBranch: (String) -> Unit,
): PageContributionBundle {
    val language = context.strings.language
    val canLaunch = launchRequest != null && context.gameLaunchState?.isSupported == true
    val primaryLaunchLabel = if (shaderWarmupRequired) {
        localized(language, "android.entrypoint.0001")
    } else {
        localized(language, "android.entrypoint.0002")
    }
    val gameFileTaskRunning =
        gameFileState.status == Sts2GameFileCheckStatus.RUNNING ||
            downloadSnapshot.status == Sts2GameFileDownloadStatus.RUNNING
    return PageContributionBundle(
        sourceId = STS2_TEMPLATE_ID,
        nodes = listOf(
            PageWidgetRegistration(
                nodeId = "sts2.manage_versions",
                pageId = PageIds.HOME,
                parentNodeId = "home_current_game",
                sourceId = STS2_TEMPLATE_ID,
                orderHint = 10,
                widget = PageWidgetDetailCard(
                    title = PageTextDirect(localized(language, "android.entrypoint.0003")),
                    subtitle = PageTextDirect(
                        selectedVersion?.let { version -> versionDisplayName(language, version) }
                            ?: localized(language, "android.entrypoint.0004"),
                    ),
                    rows = selectedVersion?.let { version ->
                        listOf(row(language, "android.entrypoint.row.0001", version.versionId))
                    }.orEmpty(),
                    tone = if (selectedVersion != null) PageWidgetTone.ACCENT else PageWidgetTone.DANGER,
                    onClick = { context.openPage(VERSION_MANAGER_PAGE_ID) },
                ),
            ),
            PageWidgetRegistration(
                nodeId = "sts2.steam_account",
                pageId = PageIds.HOME,
                parentNodeId = "home_current_game",
                sourceId = STS2_TEMPLATE_ID,
                orderHint = 15,
                widget = if (steamAccounts.isEmpty()) {
                    PageWidgetDetailCard(
                        title = PageTextDirect(localized(language, "android.entrypoint.0005")),
                        subtitle = PageTextDirect(
                            steamAccountCardSubtitle(
                                language = language,
                                savedSteamAccounts = savedSteamAccounts,
                                compatibleSteamAccounts = steamAccounts,
                                selectedSteamAccount = selectedSteamAccount,
                            ),
                        ),
                        rows = steamAccountRows(language, savedSteamAccounts, steamAccounts, selectedSteamAccount),
                        actions = listOf(
                            PageActionRegistration(
                                id = "sts2.steam_account.open_account_manager",
                                label = PageTextDirect(localized(language, "android.entrypoint.0006")),
                                style = PageActionStyle.OUTLINED,
                                onClick = { context.openPage(PageIds.ACCOUNT_MANAGER) },
                            ),
                        ),
                        tone = when {
                            selectedSteamAccount != null -> PageWidgetTone.ACCENT
                            savedSteamAccounts.isNotEmpty() && steamAccounts.isEmpty() -> PageWidgetTone.DANGER
                            else -> PageWidgetTone.DEFAULT
                        },
                    )
                } else {
                    PageWidgetChoiceCard(
                        title = PageTextDirect(localized(language, "android.entrypoint.0007")),
                        subtitle = PageTextDirect(
                            localized(language, "android.entrypoint.0008"),
                        ),
                        options = listOf(
                            PageChoiceOptionRegistration(
                                id = "sts2_home_steam_account_none",
                                label = PageTextDirect(localized(language, "android.entrypoint.0009")),
                                selected = selectedSteamAccount == null,
                                enabled = !gameFileTaskRunning,
                                onClick = { if (!gameFileTaskRunning) onSelectSteamAccount(null) },
                            ),
                        ) + steamAccounts.map { account ->
                            PageChoiceOptionRegistration(
                                id = "sts2_home_steam_account_${account.subjectId}",
                                label = PageTextDirect(steamAccountOptionLabel(language, account)),
                                selected = account.subjectId == selectedSteamAccount?.subjectId,
                                enabled = !gameFileTaskRunning,
                                onClick = { if (!gameFileTaskRunning) onSelectSteamAccount(account.subjectId) },
                            )
                        },
                        actions = listOf(
                            PageActionRegistration(
                                id = "sts2.steam_account.open_account_manager",
                                label = PageTextDirect(localized(language, "android.entrypoint.0010")),
                                style = PageActionStyle.OUTLINED,
                                onClick = { context.openPage(PageIds.ACCOUNT_MANAGER) },
                            ),
                        ),
                    )
                },
            ),
            PageSectionRegistration(
                nodeId = "sts2.game_files.section",
                pageId = PageIds.HOME,
                parentNodeId = "home_current_game",
                sourceId = STS2_TEMPLATE_ID,
                orderHint = 20,
                title = PageTextDirect(localized(language, "android.entrypoint.0011")),
                subtitle = PageTextDirect(
                    localized(language, "android.entrypoint.0012"),
                ),
            ),
            PageWidgetRegistration(
                nodeId = "sts2.game_files.mode",
                pageId = PageIds.HOME,
                parentNodeId = "sts2.game_files.section",
                sourceId = STS2_TEMPLATE_ID,
                orderHint = 21,
                widget = PageWidgetChoiceCard(
                    title = PageTextDirect(localized(language, "android.entrypoint.0013")),
                    subtitle = PageTextDirect(
                        localized(
                            language,
                            if (gameFileTaskRunning) {
                                "android.entrypoint.game_files.mode.locked"
                            } else {
                                "android.entrypoint.game_files.mode.description"
                            },
                        ),
                    ),
                    options = listOf(
                        PageChoiceOptionRegistration(
                            id = "sts2_game_files_mode_steam",
                            label = PageTextDirect(localized(language, "android.entrypoint.0014")),
                            selected = gameFilePreferences.steamVerificationEnabled,
                            enabled = !gameFileTaskRunning,
                            onClick = { if (!gameFileTaskRunning) onToggleSteamVerification(true) },
                        ),
                        PageChoiceOptionRegistration(
                            id = "sts2_game_files_mode_simple",
                            label = PageTextDirect(localized(language, "android.entrypoint.0015")),
                            selected = !gameFilePreferences.steamVerificationEnabled,
                            enabled = !gameFileTaskRunning,
                            onClick = { if (!gameFileTaskRunning) onToggleSteamVerification(false) },
                        ),
                    ),
                ),
            ),
            PageWidgetRegistration(
                nodeId = "sts2.game_files.branch",
                pageId = PageIds.HOME,
                parentNodeId = "sts2.game_files.section",
                sourceId = STS2_TEMPLATE_ID,
                orderHint = 22,
                widget = PageWidgetChoiceCard(
                    title = PageTextDirect(localized(language, "android.entrypoint.0016")),
                    subtitle = PageTextDirect(
                        localized(
                            language,
                            if (gameFileTaskRunning) {
                                "android.entrypoint.game_files.branch.locked"
                            } else {
                                "android.entrypoint.game_files.branch.description"
                            },
                        ),
                    ),
                    options = steamBranchOptions(
                        language = language,
                        selectedSteamBranch = selectedSteamBranch,
                        enabled = !gameFileTaskRunning,
                        onSelectSteamBranch = { branch ->
                            if (!gameFileTaskRunning) {
                                onSelectSteamBranch(branch)
                            }
                        },
                    ),
                ),
            ),
            PageWidgetRegistration(
                nodeId = "sts2.runtime",
                pageId = PageIds.HOME,
                parentNodeId = "home_current_game",
                sourceId = STS2_TEMPLATE_ID,
                orderHint = 18,
                widget = PageWidgetDetailCard(
                    title = PageTextDirect(localized(language, "android.entrypoint.0017")),
                    subtitle = PageTextDirect(runtimeCardSubtitle(language, runtimeReport)),
                    rows = runtimeRows(language, runtimeReport),
                    actions = runtimeActions(
                        language = language,
                        context = context,
                        runtimeReport = runtimeReport,
                        onQuickFixRuntime = onQuickFixRuntime,
                    ),
                    tone = if (runtimeReport?.isComplete == true) PageWidgetTone.ACCENT else PageWidgetTone.DANGER,
                    onClick = { context.openPage(RUNTIME_MANAGER_PAGE_ID) },
                ),
            ),
            PageWidgetRegistration(
                nodeId = "sts2.mod_manager",
                pageId = PageIds.HOME,
                parentNodeId = "home_current_game",
                sourceId = STS2_TEMPLATE_ID,
                orderHint = 19,
                widget = PageWidgetDetailCard(
                    title = PageTextDirect(localized(language, "android.entrypoint.0018")),
                    subtitle = PageTextDirect(
                        modManagerCardSubtitle(
                            language = language,
                            selectedVersion = selectedVersion,
                            modScanResult = modScanResult,
                        ),
                    ),
                    rows = modManagerCardRows(
                        language = language,
                        selectedVersion = selectedVersion,
                        modScanResult = modScanResult,
                    ),
                    tone = modManagerCardTone(
                        selectedVersion = selectedVersion,
                        modScanResult = modScanResult,
                    ),
                    onClick = { context.openPage(MOD_MANAGER_PAGE_ID) },
                ),
            ),
            PageWidgetRegistration(
                nodeId = "sts2.game_files.check",
                pageId = PageIds.HOME,
                parentNodeId = "sts2.game_files.section",
                sourceId = STS2_TEMPLATE_ID,
                orderHint = 25,
                widget = PageWidgetDetailCard(
                    title = PageTextDirect(localized(language, "android.entrypoint.0019")),
                    subtitle = PageTextDirect(
                        gameFileCheckCardSubtitle(
                            language = language,
                            gameFileState = gameFileState,
                            steamVerificationEnabled = gameFilePreferences.steamVerificationEnabled,
                            selectedSteamBranch = selectedSteamBranch,
                        ),
                    ),
                    rows = gameFileCheckRows(
                        language = language,
                        gameFileState = gameFileState,
                        steamVerificationEnabled = gameFilePreferences.steamVerificationEnabled,
                        selectedSteamBranch = selectedSteamBranch,
                        selectedSteamAccount = selectedSteamAccount,
                    ),
                    actions = gameFileCheckActions(
                        language = language,
                        context = context,
                        selectedVersion = selectedVersion,
                        steamVerificationEnabled = gameFilePreferences.steamVerificationEnabled,
                        selectedSteamAccount = selectedSteamAccount,
                        gameFileState = gameFileState,
                        downloadSnapshot = downloadSnapshot,
                        onRunGameFileCheck = onRunGameFileCheck,
                        onCancelGameFileCheck = onCancelGameFileCheck,
                    ),
                    tone = gameFileCheckCardTone(gameFileState),
                ),
            ),
            PageWidgetRegistration(
                nodeId = "sts2.game_files.download",
                pageId = PageIds.HOME,
                parentNodeId = "sts2.game_files.section",
                sourceId = STS2_TEMPLATE_ID,
                orderHint = 27,
                widget = PageWidgetDetailCard(
                    title = PageTextDirect(localized(language, "android.entrypoint.0020")),
                    subtitle = PageTextDirect(
                        gameFileDownloadCardSubtitle(
                            language = language,
                            downloadSnapshot = downloadSnapshot,
                            selectedSteamBranch = selectedSteamBranch,
                        ),
                    ),
                    rows = gameFileDownloadRows(
                        language = language,
                        selectedVersion = selectedVersion,
                        selectedSteamBranch = selectedSteamBranch,
                        selectedSteamAccount = selectedSteamAccount,
                        downloadSnapshot = downloadSnapshot,
                    ),
                    actions = gameFileDownloadActions(
                        language = language,
                        context = context,
                        selectedVersion = selectedVersion,
                        selectedSteamAccount = selectedSteamAccount,
                        gameFileState = gameFileState,
                        downloadSnapshot = downloadSnapshot,
                        onDownloadGameFiles = onDownloadGameFiles,
                        onCancelGameFileDownload = onCancelGameFileDownload,
                        onPauseGameFileDownload = onPauseGameFileDownload,
                    ),
                    tone = gameFileDownloadCardTone(
                        selectedVersion = selectedVersion,
                        selectedSteamAccount = selectedSteamAccount,
                        manageStorageGranted = context.manageStorageAccessState?.isGranted == true,
                        downloadSnapshot = downloadSnapshot,
                    ),
                ),
            ),
            PageWidgetRegistration(
                nodeId = "sts2.launch_bar",
                pageId = PageIds.HOME,
                sourceId = STS2_TEMPLATE_ID,
                orderHint = 100,
                placement = PageNodePlacement.FOOTER,
                footerLayout = PageFooterLayoutRegistration(
                    horizontalPaddingDp = 18,
                    topPaddingDp = 2,
                    bottomPaddingDp = 6,
                ),
                widget = PageWidgetLaunchBar(
                    primaryAction = PageActionRegistration(
                        id = if (shaderWarmupRequired) {
                            "sts2.launch_bar.shader_warmup"
                        } else {
                            "sts2.launch_bar.launch"
                        },
                        label = PageTextDirect(primaryLaunchLabel),
                        style = PageActionStyle.FILLED_TONAL,
                        enabled = canLaunch,
                        onClick = {
                            launchRequest?.let { request -> context.launchGame(request) }
                        },
                    ),
                    secondaryActions = listOf(
                        PageActionRegistration(
                            id = "sts2.launch_bar.options",
                            label = PageTextDirect(localized(language, "android.entrypoint.0021")),
                            compactLabel = PageTextDirect("\u2699"),
                            style = PageActionStyle.OUTLINED,
                            onClick = { context.openPage(LAUNCH_PAGE_ID) },
                        ),
                    ),
                    tone = if (canLaunch) PageWidgetTone.ACCENT else PageWidgetTone.DEFAULT,
                ),
            ),
        ) + listOfNotNull(
            gameFileCheckProgressCard(
                language = language,
                gameFileState = gameFileState,
            ),
            gameFileDownloadProgressCard(
                language = language,
                downloadSnapshot = downloadSnapshot,
            ),
        ),
    )
}

private fun buildModManagerContribution(
    context: PageContext,
    selectedVersion: Sts2VersionDefinition?,
    modScanResult: Sts2ModScanResult?,
    modSettingsSnapshot: Sts2ModSettingsSnapshot,
    modImportStatus: Sts2ModImportStatus?,
    pendingReplacement: Sts2ModPendingReplacement?,
    onImportModPackage: (() -> Unit)?,
    onConfirmPendingReplacement: () -> Unit,
    onCancelPendingReplacement: () -> Unit,
): PageContributionBundle {
    val language = context.strings.language
    val canImportModPackage = onImportModPackage != null && selectedVersion?.modDirectory?.trim()?.isNotBlank() == true
    return PageContributionBundle(
        sourceId = STS2_TEMPLATE_ID,
        page = PageRegistration(
            id = MOD_MANAGER_PAGE_ID,
            sourceId = STS2_TEMPLATE_ID,
            title = PageTextDirect(localized(language, "android.entrypoint.0022")),
            subtitle = PageTextDirect(
                localized(language, "android.entrypoint.0023"),
            ),
            actionLabel = PageTextDirect(context.strings.commonBack),
            action = { context.goBack() },
            supportedTargets = setOf(PlatformTarget.ANDROID),
        ),
        nodes = buildList {
            add(
                PageSectionRegistration(
                    nodeId = "sts2_mod_manager_overview",
                    pageId = MOD_MANAGER_PAGE_ID,
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 0,
                    title = PageTextDirect(localized(language, "android.entrypoint.0024")),
                    subtitle = PageTextDirect(
                        localized(language, "android.entrypoint.0025"),
                    ),
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_mod_manager_overview_card",
                    pageId = MOD_MANAGER_PAGE_ID,
                    parentNodeId = "sts2_mod_manager_overview",
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 0,
                    widget = PageWidgetDetailCard(
                        title = PageTextDirect(localized(language, "android.entrypoint.0026")),
                        subtitle = PageTextDirect(
                            modManagerCardSubtitle(
                                language = language,
                                selectedVersion = selectedVersion,
                                modScanResult = modScanResult,
                            ),
                        ),
                        rows = modManagerCardRows(
                            language = language,
                            selectedVersion = selectedVersion,
                            modScanResult = modScanResult,
                        ),
                        tone = modManagerCardTone(
                            selectedVersion = selectedVersion,
                            modScanResult = modScanResult,
                        ),
                    ),
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_mod_manager_actions",
                    pageId = MOD_MANAGER_PAGE_ID,
                    parentNodeId = "sts2_mod_manager_overview",
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 5,
                    widget = PageWidgetButtonStack(
                        actions = listOf(
                            PageActionRegistration(
                                id = "sts2_mod_manager_import",
                                label = PageTextDirect(localized(language, "android.entrypoint.0027")),
                                style = PageActionStyle.FILLED_TONAL,
                                enabled = canImportModPackage,
                                onClick = { onImportModPackage?.invoke() },
                            ),
                            PageActionRegistration(
                                id = "sts2_mod_manager_refresh",
                                label = PageTextDirect(localized(language, "android.entrypoint.0028")),
                                style = PageActionStyle.OUTLINED,
                                onClick = context::refreshPage,
                            ),
                        ),
                    ),
                ),
            )
            if (modImportStatus != null) {
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_mod_manager_last_import",
                        pageId = MOD_MANAGER_PAGE_ID,
                        parentNodeId = "sts2_mod_manager_overview",
                        sourceId = STS2_TEMPLATE_ID,
                        orderHint = 8,
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(localized(language, "android.entrypoint.0029")),
                            subtitle = PageTextDirect(modImportStatus.detail),
                            rows = modImportStatusRows(language, modImportStatus),
                            tone = modImportStatusTone(modImportStatus),
                        ),
                    ),
                )
            }
            if (pendingReplacement?.kind == Sts2ModPendingReplacementKind.MOD_PACKAGE) {
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_mod_manager_pending_replace",
                        pageId = MOD_MANAGER_PAGE_ID,
                        parentNodeId = "sts2_mod_manager_overview",
                        sourceId = STS2_TEMPLATE_ID,
                        orderHint = 9,
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(modPendingReplacementTitle(language, pendingReplacement)),
                            subtitle = PageTextDirect(pendingReplacement.detail),
                            rows = modPendingReplacementRows(language, pendingReplacement),
                            actions = listOf(
                                PageActionRegistration(
                                    id = "sts2_mod_manager_pending_replace_confirm",
                                    label = PageTextDirect(modPendingReplacementConfirmLabel(language, pendingReplacement)),
                                    style = PageActionStyle.FILLED_TONAL,
                                    onClick = onConfirmPendingReplacement,
                                ),
                                PageActionRegistration(
                                    id = "sts2_mod_manager_pending_replace_cancel",
                                    label = PageTextDirect(localized(language, "android.entrypoint.0030")),
                                    style = PageActionStyle.OUTLINED,
                                    onClick = onCancelPendingReplacement,
                                ),
                            ),
                            tone = PageWidgetTone.DEFAULT,
                        ),
                    ),
                )
            }
            when {
                selectedVersion == null -> {
                    add(
                        PageWidgetRegistration(
                            nodeId = "sts2_mod_manager_missing_version",
                            pageId = MOD_MANAGER_PAGE_ID,
                            parentNodeId = "sts2_mod_manager_overview",
                            sourceId = STS2_TEMPLATE_ID,
                            orderHint = 10,
                            widget = PageWidgetDetailCard(
                                title = PageTextDirect(localized(language, "android.entrypoint.0031")),
                                subtitle = PageTextDirect(
                                    localized(language, "android.entrypoint.0032"),
                                ),
                                tone = PageWidgetTone.DANGER,
                            ),
                        ),
                    )
                }

                modScanResult == null || !modScanResult.modDirectoryConfigured -> {
                    add(
                        PageWidgetRegistration(
                            nodeId = "sts2_mod_manager_missing_directory",
                            pageId = MOD_MANAGER_PAGE_ID,
                            parentNodeId = "sts2_mod_manager_overview",
                            sourceId = STS2_TEMPLATE_ID,
                            orderHint = 10,
                            widget = PageWidgetDetailCard(
                                title = PageTextDirect(localized(language, "android.entrypoint.0033")),
                                subtitle = PageTextDirect(
                                    localized(language, "android.entrypoint.0034"),
                                ),
                                tone = PageWidgetTone.DANGER,
                            ),
                        ),
                    )
                }

                !modScanResult.modDirectoryExists -> {
                    add(
                        PageWidgetRegistration(
                            nodeId = "sts2_mod_manager_directory_missing",
                            pageId = MOD_MANAGER_PAGE_ID,
                            parentNodeId = "sts2_mod_manager_overview",
                            sourceId = STS2_TEMPLATE_ID,
                            orderHint = 10,
                            widget = PageWidgetDetailCard(
                                title = PageTextDirect(localized(language, "android.entrypoint.0035")),
                                subtitle = PageTextDirect(
                                    localized(language, "android.entrypoint.0036"),
                                ),
                                rows = listOf(
                                    row(language, "android.entrypoint.row.0002", modScanResult.modDirectoryPath),
                                ),
                                tone = PageWidgetTone.DANGER,
                            ),
                        ),
                    )
                }

                else -> {
                    if (modScanResult.problems.isNotEmpty()) {
                        add(
                            PageSectionRegistration(
                                nodeId = "sts2_mod_manager_problems",
                                pageId = MOD_MANAGER_PAGE_ID,
                                sourceId = STS2_TEMPLATE_ID,
                                orderHint = 20,
                                title = PageTextDirect(localized(language, "android.entrypoint.0037")),
                                subtitle = PageTextDirect(
                                    localized(language, "android.entrypoint.0038"),
                                ),
                            ),
                        )
                        modScanResult.problems.forEachIndexed { index, problem ->
                            add(
                                PageWidgetRegistration(
                                    nodeId = "sts2_mod_manager_problem_$index",
                                    pageId = MOD_MANAGER_PAGE_ID,
                                    parentNodeId = "sts2_mod_manager_problems",
                                    sourceId = STS2_TEMPLATE_ID,
                                    orderHint = index,
                                    widget = PageWidgetDetailCard(
                                        title = PageTextDirect(problem.relativeManifestPath),
                                        subtitle = PageTextDirect(
                                            localized(language, "android.entrypoint.0039"),
                                        ),
                                        rows = listOf(
                                            row(language, "android.entrypoint.row.0003", problem.reason),
                                            row(language, "android.entrypoint.row.0004", problem.manifestFilePath),
                                        ),
                                        tone = PageWidgetTone.DANGER,
                                    ),
                                ),
                            )
                        }
                    }

                    add(
                        PageSectionRegistration(
                            nodeId = "sts2_mod_manager_mods",
                            pageId = MOD_MANAGER_PAGE_ID,
                            sourceId = STS2_TEMPLATE_ID,
                            orderHint = 30,
                            title = PageTextDirect(localized(language, "android.entrypoint.0040")),
                            subtitle = PageTextDirect(
                                localized(language, "android.entrypoint.0041"),
                            ),
                        ),
                    )
                    if (modScanResult.mods.isEmpty()) {
                        add(
                            PageWidgetRegistration(
                                nodeId = "sts2_mod_manager_empty",
                                pageId = MOD_MANAGER_PAGE_ID,
                                parentNodeId = "sts2_mod_manager_mods",
                                sourceId = STS2_TEMPLATE_ID,
                                orderHint = 0,
                                widget = PageWidgetDetailCard(
                                    title = PageTextDirect(localized(language, "android.entrypoint.0042")),
                                    subtitle = PageTextDirect(
                                        localized(language, "android.entrypoint.0043"),
                                    ),
                                ),
                            ),
                        )
                    } else {
                        modScanResult.mods.forEachIndexed { index, mod ->
                            add(
                                PageWidgetRegistration(
                                    nodeId = "sts2_mod_manager_mod_$index",
                                    pageId = MOD_MANAGER_PAGE_ID,
                                    parentNodeId = "sts2_mod_manager_mods",
                                    sourceId = STS2_TEMPLATE_ID,
                                    orderHint = index,
                                widget = PageWidgetDetailCard(
                                        title = PageTextDirect(modManagerDisplayName(mod)),
                                        subtitle = PageTextDirect(modManagerSubtitle(language, mod)),
                                        rows = modManagerRows(
                                            language = language,
                                            mod = mod,
                                            modEnabledState = resolveSts2ModEnabledState(
                                                snapshot = modSettingsSnapshot,
                                                modId = mod.manifest.id,
                                            ),
                                        ),
                                        tone = if (mod.issues.isEmpty()) PageWidgetTone.ACCENT else PageWidgetTone.DANGER,
                                        onClick = { context.openPage(modDetailPageId(mod)) },
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        },
    )
}

private fun buildModDetailContribution(
    context: PageContext,
    selectedVersion: Sts2VersionDefinition?,
    mod: Sts2ScannedMod,
    modEnabledState: Sts2ResolvedModEnabledState,
    pendingReplacement: Sts2ModPendingReplacement?,
    onSetModEnabled: (Boolean) -> Unit,
    onImportPck: (() -> Unit)?,
    onImportDll: (() -> Unit)?,
    onConfirmPendingReplacement: () -> Unit,
    onCancelPendingReplacement: () -> Unit,
    onDeleteMod: () -> Unit,
): PageContributionBundle {
    val language = context.strings.language
    val matchingPendingReplacement = pendingReplacement?.takeIf { replacement ->
        replacement.modId == mod.manifest.id
    }
    return PageContributionBundle(
        sourceId = STS2_TEMPLATE_ID,
        page = PageRegistration(
            id = modDetailPageId(mod),
            sourceId = STS2_TEMPLATE_ID,
            title = PageTextDirect(modManagerDisplayName(mod)),
            subtitle = PageTextDirect(
                if (mod.issues.isEmpty()) {
                    localized(language, "android.entrypoint.0044")
                } else {
                    modManagerIssueSummary(language, mod.issues)
                }
            ),
            actionLabel = PageTextDirect(context.strings.commonBack),
            action = { context.goBack() },
            supportedTargets = setOf(PlatformTarget.ANDROID),
        ),
        nodes = buildList {
            add(
                PageSectionRegistration(
                    nodeId = "sts2_mod_detail_summary_${mod.discoveryOrder}",
                    pageId = modDetailPageId(mod),
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 0,
                    title = PageTextDirect(localized(language, "android.entrypoint.0045")),
                    subtitle = PageTextDirect(
                        localized(language, "android.entrypoint.0046"),
                    ),
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_mod_detail_card_${mod.discoveryOrder}",
                    pageId = modDetailPageId(mod),
                    parentNodeId = "sts2_mod_detail_summary_${mod.discoveryOrder}",
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 0,
                    widget = PageWidgetDetailCard(
                        title = PageTextDirect(modManagerDisplayName(mod)),
                        subtitle = PageTextDirect(modManagerSubtitle(language, mod)),
                        rows = modDetailRows(language, selectedVersion, mod, modEnabledState),
                        tone = if (mod.issues.isEmpty()) PageWidgetTone.ACCENT else PageWidgetTone.DANGER,
                    ),
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_mod_detail_toggle_${mod.discoveryOrder}",
                    pageId = modDetailPageId(mod),
                    parentNodeId = "sts2_mod_detail_summary_${mod.discoveryOrder}",
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 4,
                    widget = PageWidgetToggleCard(
                        title = PageTextDirect(localized(language, "android.entrypoint.0047")),
                        subtitle = PageTextDirect(modEnabledToggleSubtitle(language, modEnabledState)),
                        checked = modEnabledState.enabled,
                        enabled = modEnabledState.settingsFilePath != null && modEnabledState.readError == null,
                        onCheckedChange = onSetModEnabled,
                    ),
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_mod_detail_actions_${mod.discoveryOrder}",
                    pageId = modDetailPageId(mod),
                    parentNodeId = "sts2_mod_detail_summary_${mod.discoveryOrder}",
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 5,
                    widget = PageWidgetButtonStack(
                        actions = buildList {
                            if (mod.manifest.hasPck && onImportPck != null) {
                                add(
                                    PageActionRegistration(
                                        id = "sts2_mod_detail_import_pck_${mod.discoveryOrder}",
                                        label = PageTextDirect(localized(language, "android.entrypoint.0048")),
                                        style = PageActionStyle.FILLED_TONAL,
                                        onClick = onImportPck,
                                    ),
                                )
                            }
                            if (mod.manifest.hasDll && onImportDll != null) {
                                add(
                                    PageActionRegistration(
                                        id = "sts2_mod_detail_import_dll_${mod.discoveryOrder}",
                                        label = PageTextDirect(localized(language, "android.entrypoint.0049")),
                                        style = PageActionStyle.FILLED_TONAL,
                                        onClick = onImportDll,
                                    ),
                                )
                            }
                            add(
                                PageActionRegistration(
                                    id = "sts2_mod_detail_delete_${mod.discoveryOrder}",
                                    label = PageTextDirect(localized(language, "android.entrypoint.0050")),
                                    style = PageActionStyle.FILLED_TONAL,
                                    onClick = onDeleteMod,
                                ),
                            )
                            add(
                                PageActionRegistration(
                                    id = "sts2_mod_detail_back_${mod.discoveryOrder}",
                                    label = PageTextDirect(localized(language, "android.entrypoint.0051")),
                                    style = PageActionStyle.OUTLINED,
                                    onClick = { context.replaceCurrentPage(MOD_MANAGER_PAGE_ID) },
                                ),
                            )
                        },
                    ),
                ),
            )
            if (matchingPendingReplacement != null) {
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_mod_detail_pending_replace_${mod.discoveryOrder}",
                        pageId = modDetailPageId(mod),
                        parentNodeId = "sts2_mod_detail_summary_${mod.discoveryOrder}",
                        sourceId = STS2_TEMPLATE_ID,
                        orderHint = 6,
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(modPendingReplacementTitle(language, matchingPendingReplacement)),
                            subtitle = PageTextDirect(matchingPendingReplacement.detail),
                            rows = modPendingReplacementRows(language, matchingPendingReplacement),
                            actions = listOf(
                                PageActionRegistration(
                                    id = "sts2_mod_detail_pending_confirm_${mod.discoveryOrder}",
                                    label = PageTextDirect(modPendingReplacementConfirmLabel(language, matchingPendingReplacement)),
                                    style = PageActionStyle.FILLED_TONAL,
                                    onClick = onConfirmPendingReplacement,
                                ),
                                PageActionRegistration(
                                    id = "sts2_mod_detail_pending_cancel_${mod.discoveryOrder}",
                                    label = PageTextDirect(localized(language, "android.entrypoint.0052")),
                                    style = PageActionStyle.OUTLINED,
                                    onClick = onCancelPendingReplacement,
                                ),
                            ),
                            tone = PageWidgetTone.DEFAULT,
                        ),
                    ),
                )
            }
        },
    )
}

private fun buildLaunchContribution(
    context: PageContext,
    game: GameInstance,
    selectedVersion: Sts2VersionDefinition?,
    versionStore: Sts2VersionStore,
    shaderCacheEnabled: Boolean,
    shaderCacheEntries: List<Sts2ShaderCacheEntry>,
    onShaderCacheEnabledChange: (Boolean) -> Unit,
): PageContributionBundle {
    val language = context.strings.language
    val launchSettingsDraft = selectedVersion?.let { version ->
        versionStore.launchSettingsDraft(game.id, version.clientId) ?: version.toLaunchSettingsDraft()
    }
    val launchSettingsValidationError = selectedVersion?.let { version ->
        versionStore.launchSettingsValidationError(game.id, version.clientId)
    }
    return PageContributionBundle(
        sourceId = STS2_TEMPLATE_ID,
        page = PageRegistration(
            id = LAUNCH_PAGE_ID,
            sourceId = STS2_TEMPLATE_ID,
            title = PageTextDirect(localized(language, "android.entrypoint.0053")),
            subtitle = PageTextDirect(
                localized(language, "android.entrypoint.0054"),
            ),
            actionLabel = PageTextDirect(context.strings.commonBack),
            action = { context.goBack() },
            supportedTargets = setOf(PlatformTarget.ANDROID),
        ),
        nodes = buildList {
            add(
                PageSectionRegistration(
                    nodeId = "sts2_launch_overview",
                    pageId = LAUNCH_PAGE_ID,
                    sourceId = STS2_TEMPLATE_ID,
                    title = PageTextDirect(localized(language, "android.entrypoint.0055")),
                    subtitle = PageTextDirect(
                        localized(language, "android.entrypoint.0056"),
                    ),
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_launch_selected_version",
                    pageId = LAUNCH_PAGE_ID,
                    parentNodeId = "sts2_launch_overview",
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 0,
                    widget = PageWidgetDetailCard(
                        title = PageTextDirect(localized(language, "android.entrypoint.0057")),
                        subtitle = PageTextDirect(
                            selectedVersion?.let { version ->
                                localized(language, "android.entrypoint.0058", listOf(game.displayName, versionDisplayName(language, version)))
                            } ?: localized(language, "android.entrypoint.0059"),
                        ),
                        rows = selectedVersion?.let { versionRows(language, it, selected = true) }.orEmpty(),
                        actions = listOf(
                            PageActionRegistration(
                                id = "sts2_launch_manage_versions",
                                label = PageTextDirect(localized(language, "android.entrypoint.0060")),
                                style = PageActionStyle.OUTLINED,
                                onClick = { context.openPage(VERSION_MANAGER_PAGE_ID) },
                            ),
                        ),
                        tone = if (selectedVersion != null) PageWidgetTone.ACCENT else PageWidgetTone.DANGER,
                    ),
                ),
            )
            add(
                PageSectionRegistration(
                    nodeId = "sts2_launch_settings",
                    pageId = LAUNCH_PAGE_ID,
                    parentNodeId = "sts2_launch_overview",
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 5,
                    title = PageTextDirect(localized(language, "android.entrypoint.0061")),
                    subtitle = PageTextDirect(
                        localized(language, "android.entrypoint.0062"),
                    ),
                ),
            )
            if (selectedVersion == null) {
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_launch_settings_missing_version",
                        pageId = LAUNCH_PAGE_ID,
                        parentNodeId = "sts2_launch_settings",
                        sourceId = STS2_TEMPLATE_ID,
                        orderHint = 0,
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(localized(language, "android.entrypoint.0063")),
                            subtitle = PageTextDirect(
                                localized(language, "android.entrypoint.0064"),
                            ),
                            actions = listOf(
                                PageActionRegistration(
                                    id = "sts2_launch_settings_open_versions",
                                    label = PageTextDirect(localized(language, "android.entrypoint.0065")),
                                    style = PageActionStyle.OUTLINED,
                                    onClick = { context.openPage(VERSION_MANAGER_PAGE_ID) },
                                ),
                            ),
                            tone = PageWidgetTone.DANGER,
                        ),
                    ),
                )
            } else if (launchSettingsDraft != null) {
                fun updateLaunchSettings(
                    refreshAfterUpdate: Boolean = false,
                    transform: (Sts2LaunchSettingsDraft) -> Sts2LaunchSettingsDraft,
                ) {
                    versionStore.updateLaunchSettingsDraft(game.id, selectedVersion.clientId, transform)
                    val saved = versionStore.saveLaunchSettingsDraft(game.id, selectedVersion.clientId)
                    if (refreshAfterUpdate || !saved) {
                        context.refreshPage()
                    }
                }
                addAll(
                    launchSettingsWidgets(
                        pageId = LAUNCH_PAGE_ID,
                        parentNodeId = "sts2_launch_settings",
                        language = language,
                        draft = launchSettingsDraft,
                        shaderCacheEnabled = shaderCacheEnabled,
                        shaderCacheEntries = shaderCacheEntries,
                        validationError = launchSettingsValidationError,
                        onOpenShaderCacheManager = { context.openPage(SHADER_CACHE_MANAGER_PAGE_ID) },
                        onSpineUpdateDivisorChange = { value ->
                            updateLaunchSettings { current ->
                                current.copy(spineUpdateDivisorText = value)
                            }
                        },
                        onPreloadTrimEnabledChange = { checked ->
                            updateLaunchSettings(refreshAfterUpdate = true) { current ->
                                current.copy(preloadTrimEnabled = checked)
                            }
                        },
                        onAssetLoadingBatchSizeChange = { value ->
                            updateLaunchSettings { current ->
                                current.copy(assetLoadingBatchSizeText = value)
                            }
                        },
                        onMobileShadersEnabledChange = { checked ->
                            updateLaunchSettings(refreshAfterUpdate = true) { current ->
                                current.copy(mobileShadersEnabled = checked)
                            }
                        },
                        onShaderCacheEnabledChange = onShaderCacheEnabledChange,
                        onRendererChange = { value ->
                            updateLaunchSettings(refreshAfterUpdate = true) { current ->
                                current.copy(renderer = value)
                            }
                        },
                        onParticleScalePercentChange = { value ->
                            updateLaunchSettings { current ->
                                current.copy(particleScalePercentText = value)
                            }
                        },
                        onGlowModeChange = { value ->
                            updateLaunchSettings(refreshAfterUpdate = true) { current ->
                                current.copy(glowMode = value)
                            }
                        },
                        onVfxLimitEnabledChange = { checked ->
                            updateLaunchSettings(refreshAfterUpdate = true) { current ->
                                current.copy(vfxLimitEnabled = checked)
                            }
                        },
                    ),
                )
            }
        },
    )
}

private fun buildShaderCacheManagerContribution(
    context: PageContext,
    selectedVersion: Sts2VersionDefinition?,
    shaderCacheEntries: List<Sts2ShaderCacheEntry>,
    onDeleteCacheEntry: (Sts2ShaderCacheEntry) -> Unit,
): PageContributionBundle {
    val language = context.strings.language
    val currentReleaseVersion = selectedVersion
        ?.gameDirectory
        ?.trim()
        ?.takeIf { path -> path.isNotBlank() }
        ?.let { path -> readReleaseInfoVersion(java.io.File(path)) }
    val currentCache = shaderCacheEntries.firstOrNull { entry -> entry.isCurrent }
    return PageContributionBundle(
        sourceId = STS2_TEMPLATE_ID,
        page = PageRegistration(
            id = SHADER_CACHE_MANAGER_PAGE_ID,
            sourceId = STS2_TEMPLATE_ID,
            title = PageTextDirect(localized(language, "android.entrypoint.0066")),
            subtitle = PageTextDirect(
                localized(language, "android.entrypoint.0067"),
            ),
            actionLabel = PageTextDirect(context.strings.commonBack),
            action = { context.goBack() },
            supportedTargets = setOf(PlatformTarget.ANDROID),
        ),
        nodes = buildList {
            add(
                PageSectionRegistration(
                    nodeId = "sts2_shader_cache_overview",
                    pageId = SHADER_CACHE_MANAGER_PAGE_ID,
                    sourceId = STS2_TEMPLATE_ID,
                    title = PageTextDirect(localized(language, "android.entrypoint.0068")),
                    subtitle = PageTextDirect(
                        localized(language, "android.entrypoint.0069"),
                    ),
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_shader_cache_current",
                    pageId = SHADER_CACHE_MANAGER_PAGE_ID,
                    parentNodeId = "sts2_shader_cache_overview",
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 0,
                    widget = PageWidgetDetailCard(
                        title = PageTextDirect(localized(language, "android.entrypoint.0070")),
                        subtitle = PageTextDirect(
                            if (currentReleaseVersion != null) {
                                if (currentCache != null) {
                                    localized(language, "android.entrypoint.0071")
                                } else {
                                    localized(language, "android.entrypoint.0072")
                                }
                            } else {
                                localized(language, "android.entrypoint.0073")
                            },
                        ),
                        rows = buildList {
                            add(
                                row(language, "android.entrypoint.row.0005", currentReleaseVersion ?: localized(language, "android.entrypoint.0074"), ),
                            )
                            add(row(language, "android.entrypoint.row.0006", shaderCacheEntries.size.toString()))
                            currentCache?.let { entry ->
                                add(row(language, "android.entrypoint.row.0007", entry.fileCount.toString()))
                                add(row(language, "android.entrypoint.row.0008", formatFileSize(entry.sizeBytes)))
                            }
                        },
                        tone = if (currentCache != null) PageWidgetTone.ACCENT else PageWidgetTone.DEFAULT,
                    ),
                ),
            )
            add(
                PageSectionRegistration(
                    nodeId = "sts2_shader_cache_files",
                    pageId = SHADER_CACHE_MANAGER_PAGE_ID,
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 10,
                    title = PageTextDirect(localized(language, "android.entrypoint.0075")),
                ),
            )
            if (shaderCacheEntries.isEmpty()) {
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_shader_cache_empty",
                        pageId = SHADER_CACHE_MANAGER_PAGE_ID,
                        parentNodeId = "sts2_shader_cache_files",
                        sourceId = STS2_TEMPLATE_ID,
                        orderHint = 0,
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(localized(language, "android.entrypoint.0076")),
                            subtitle = PageTextDirect(
                                localized(language, "android.entrypoint.0077"),
                            ),
                        ),
                    ),
                )
            } else {
                shaderCacheEntries.forEachIndexed { index, entry ->
                    add(
                        PageWidgetRegistration(
                            nodeId = "sts2_shader_cache_${entry.key.toPageNodeIdPart()}",
                            pageId = SHADER_CACHE_MANAGER_PAGE_ID,
                            parentNodeId = "sts2_shader_cache_files",
                            sourceId = STS2_TEMPLATE_ID,
                            orderHint = index,
                            widget = PageWidgetDetailCard(
                                title = PageTextDirect(shaderCacheEntryTitle(language, entry)),
                                subtitle = PageTextDirect(
                                    localized(language, "android.entrypoint.0078", listOf(entry.fileCount, formatFileSize(entry.sizeBytes))),
                                ),
                                rows = shaderCacheEntryRows(language, entry),
                                actions = listOf(
                                    PageActionRegistration(
                                        id = "sts2_shader_cache_delete_${entry.key.toPageNodeIdPart()}",
                                        label = PageTextDirect(localized(language, "android.entrypoint.0079")),
                                        style = PageActionStyle.OUTLINED,
                                        onClick = { onDeleteCacheEntry(entry) },
                                    ),
                                ),
                                tone = if (entry.isCurrent) PageWidgetTone.ACCENT else PageWidgetTone.DEFAULT,
                            ),
                        ),
                    )
                }
            }
        },
    )
}

private fun buildRuntimeManagerContribution(
    context: PageContext,
    runtimeReport: Sts2GodotRuntimeReport?,
    runtimeFiles: List<Sts2RuntimeFileEntry>,
    onQuickFixRuntime: () -> Boolean,
    onImportDll: (() -> Unit)?,
    onDeleteRuntimeFile: (String) -> Unit,
): PageContributionBundle {
    val language = context.strings.language
    return PageContributionBundle(
        sourceId = STS2_TEMPLATE_ID,
        page = PageRegistration(
            id = RUNTIME_MANAGER_PAGE_ID,
            sourceId = STS2_TEMPLATE_ID,
            title = PageTextDirect(localized(language, "android.entrypoint.0080")),
            subtitle = PageTextDirect(
                localized(language, "android.entrypoint.0081"),
            ),
            actionLabel = PageTextDirect(context.strings.commonBack),
            action = { context.goBack() },
            supportedTargets = setOf(PlatformTarget.ANDROID),
        ),
        nodes = buildList {
            add(
                PageSectionRegistration(
                    nodeId = "sts2_runtime_manager_overview",
                    pageId = RUNTIME_MANAGER_PAGE_ID,
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 0,
                    title = PageTextDirect(localized(language, "android.entrypoint.0082")),
                    subtitle = PageTextDirect(
                        localized(language, "android.entrypoint.0083"),
                    ),
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_runtime_manager_status",
                    pageId = RUNTIME_MANAGER_PAGE_ID,
                    parentNodeId = "sts2_runtime_manager_overview",
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 0,
                    widget = PageWidgetDetailCard(
                        title = PageTextDirect(localized(language, "android.entrypoint.0084")),
                        subtitle = PageTextDirect(runtimeCardSubtitle(language, runtimeReport)),
                        rows = runtimeRows(language, runtimeReport),
                        actions = runtimeActions(
                            language = language,
                            context = context,
                            runtimeReport = runtimeReport,
                            onQuickFixRuntime = onQuickFixRuntime,
                        ),
                        tone = if (runtimeReport?.isComplete == true) PageWidgetTone.ACCENT else PageWidgetTone.DANGER,
                    ),
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_runtime_manager_import_dll",
                    pageId = RUNTIME_MANAGER_PAGE_ID,
                    parentNodeId = "sts2_runtime_manager_overview",
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 1,
                    widget = PageWidgetDetailCard(
                        title = PageTextDirect(localized(language, "android.entrypoint.0085")),
                        subtitle = PageTextDirect(
                            localized(language, "android.entrypoint.0086"),
                        ),
                        enabled = onImportDll != null,
                        actions = listOf(
                            PageActionRegistration(
                                id = "sts2_runtime_import_dll",
                                label = PageTextDirect(localized(language, "android.entrypoint.0087")),
                                style = PageActionStyle.FILLED_TONAL,
                                enabled = onImportDll != null,
                                onClick = { onImportDll?.invoke() },
                            ),
                        ),
                        tone = PageWidgetTone.ACCENT,
                        onClick = onImportDll,
                    ),
                ),
            )
            runtimeReport
                ?.issues
                ?.takeIf { issues -> issues.isNotEmpty() }
                ?.let { issues ->
                    add(
                        PageSectionRegistration(
                            nodeId = "sts2_runtime_manager_issues",
                            pageId = RUNTIME_MANAGER_PAGE_ID,
                            sourceId = STS2_TEMPLATE_ID,
                            orderHint = 10,
                            title = PageTextDirect(localized(language, "android.entrypoint.0088")),
                        ),
                    )
                    issues.forEachIndexed { index, issue ->
                        add(
                            PageWidgetRegistration(
                                nodeId = "sts2_runtime_manager_issue_$index",
                                pageId = RUNTIME_MANAGER_PAGE_ID,
                                parentNodeId = "sts2_runtime_manager_issues",
                                sourceId = STS2_TEMPLATE_ID,
                                orderHint = index,
                                widget = PageWidgetDetailCard(
                                    title = PageTextDirect(runtimeIssueTitle(language, issue)),
                                    subtitle = PageTextDirect(runtimeIssueSubtitle(language, issue)),
                                    rows = runtimeIssueRows(language, issue),
                                    tone = PageWidgetTone.DANGER,
                                ),
                            ),
                        )
                    }
                }
            add(
                PageSectionRegistration(
                    nodeId = "sts2_runtime_manager_files",
                    pageId = RUNTIME_MANAGER_PAGE_ID,
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 20,
                    title = PageTextDirect(localized(language, "android.entrypoint.0089")),
                ),
            )
            if (runtimeFiles.isEmpty()) {
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_runtime_manager_files_empty",
                        pageId = RUNTIME_MANAGER_PAGE_ID,
                        parentNodeId = "sts2_runtime_manager_files",
                        sourceId = STS2_TEMPLATE_ID,
                        orderHint = 0,
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(localized(language, "android.entrypoint.0090")),
                            subtitle = PageTextDirect(
                                localized(language, "android.entrypoint.0091"),
                            ),
                        ),
                    ),
                )
            } else {
                runtimeFiles.forEachIndexed { index, file ->
                    add(
                        PageWidgetRegistration(
                            nodeId = "sts2_runtime_file_${file.name}",
                            pageId = RUNTIME_MANAGER_PAGE_ID,
                            parentNodeId = "sts2_runtime_manager_files",
                            sourceId = STS2_TEMPLATE_ID,
                            orderHint = index,
                            widget = PageWidgetDetailCard(
                                title = PageTextDirect(file.name),
                                subtitle = PageTextDirect(formatFileSize(file.sizeBytes)),
                                rows = listOf(row(language, "android.entrypoint.row.0009", file.absolutePath)),
                                actions = listOf(
                                    PageActionRegistration(
                                        id = "sts2_runtime_delete_${file.name}",
                                        label = PageTextDirect(localized(language, "android.entrypoint.0092")),
                                        style = PageActionStyle.OUTLINED,
                                        onClick = { onDeleteRuntimeFile(file.name) },
                                    ),
                                ),
                            ),
                        ),
                    )
                }
            }
        },
    )
}

private fun buildVersionManagerContribution(
    context: PageContext,
    game: GameInstance,
    versions: List<Sts2VersionDefinition>,
    selectedClientId: Int?,
    versionStore: Sts2VersionStore,
): PageContributionBundle {
    val language = context.strings.language
    return PageContributionBundle(
        sourceId = STS2_TEMPLATE_ID,
        page = PageRegistration(
            id = VERSION_MANAGER_PAGE_ID,
            sourceId = STS2_TEMPLATE_ID,
            title = PageTextDirect(localized(language, "android.entrypoint.0093")),
            subtitle = PageTextDirect(
                localized(language, "android.entrypoint.0094"),
            ),
            actionLabel = PageTextDirect(context.strings.commonBack),
            action = { context.goBack() },
            supportedTargets = setOf(PlatformTarget.ANDROID),
        ),
        nodes = buildList {
            add(
                PageSectionRegistration(
                    nodeId = "sts2_version_manager_tools",
                    pageId = VERSION_MANAGER_PAGE_ID,
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = -1,
                ),
            )
            add(
                PageSectionRegistration(
                    nodeId = "sts2_version_manager_list",
                    pageId = VERSION_MANAGER_PAGE_ID,
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 0,
                    title = PageTextDirect(localized(language, "android.entrypoint.0095")),
                    subtitle = PageTextDirect(
                        localized(language, "android.entrypoint.0096"),
                    ),
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_version_manager_create",
                    pageId = VERSION_MANAGER_PAGE_ID,
                    parentNodeId = "sts2_version_manager_tools",
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 0,
                    widget = PageWidgetDetailCard(
                        title = PageTextDirect(localized(language, "android.entrypoint.0097")),
                        subtitle = PageTextDirect(
                            localized(language, "android.entrypoint.0098", listOf(game.displayName)),
                        ),
                        actions = listOf(
                            PageActionRegistration(
                                id = "sts2_open_version_create",
                                label = PageTextDirect(localized(language, "android.entrypoint.0099")),
                                style = PageActionStyle.FILLED_TONAL,
                                onClick = { context.openPage(VERSION_CREATE_PAGE_ID) },
                            ),
                        ),
                    ),
                ),
            )
            if (versions.isEmpty()) {
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_version_manager_empty",
                        pageId = VERSION_MANAGER_PAGE_ID,
                        parentNodeId = "sts2_version_manager_list",
                        sourceId = STS2_TEMPLATE_ID,
                        orderHint = 0,
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(localized(language, "android.entrypoint.0100")),
                            subtitle = PageTextDirect(
                                localized(language, "android.entrypoint.0101"),
                            ),
                        ),
                    ),
                )
            } else {
                versions.forEachIndexed { index, version ->
                    val selected = version.clientId == selectedClientId
                    add(
                        PageWidgetRegistration(
                            nodeId = "sts2_version_${version.clientId}",
                            pageId = VERSION_MANAGER_PAGE_ID,
                            parentNodeId = "sts2_version_manager_list",
                            sourceId = STS2_TEMPLATE_ID,
                            orderHint = index,
                            widget = PageWidgetDetailCard(
                                title = PageTextDirect(versionDisplayName(language, version)),
                                subtitle = PageTextDirect(
                                    if (selected) {
                                        localized(language, "android.entrypoint.0102")
                                    } else {
                                        localized(language, "android.entrypoint.0103")
                                    },
                                ),
                                rows = versionRows(language, version, selected),
                                actions = listOf(
                                    PageActionRegistration(
                                        id = "sts2_open_version_detail_${version.clientId}",
                                        label = PageTextDirect(localized(language, "android.entrypoint.0104")),
                                        compactLabel = PageTextDirect("i"),
                                        style = PageActionStyle.OUTLINED,
                                        onClick = { context.openPage(detailPageId(version.clientId)) },
                                    ),
                                ),
                                tone = if (selected) PageWidgetTone.ACCENT else PageWidgetTone.DEFAULT,
                                onClick = {
                                    if (!selected && versionStore.selectVersion(game.id, version.clientId)) {
                                        context.refreshPage()
                                    }
                                },
                            ),
                        ),
                    )
                }
            }
        },
    )
}

private fun buildCreateVersionContribution(
    context: PageContext,
    game: GameInstance,
    draft: Sts2VersionDraft,
    validationError: Sts2VersionValidationError?,
    suggestedClientId: Int,
    versionStore: Sts2VersionStore,
): PageContributionBundle {
    val language = context.strings.language
    return PageContributionBundle(
        sourceId = STS2_TEMPLATE_ID,
        page = PageRegistration(
            id = VERSION_CREATE_PAGE_ID,
            sourceId = STS2_TEMPLATE_ID,
            title = PageTextDirect(localized(language, "android.entrypoint.0105")),
            subtitle = PageTextDirect(
                localized(language, "android.entrypoint.0106"),
            ),
            actionLabel = PageTextDirect(context.strings.commonBack),
            action = { context.goBack() },
            supportedTargets = setOf(PlatformTarget.ANDROID),
        ),
        nodes = buildList {
            add(
                PageSectionRegistration(
                    nodeId = "sts2_version_create_editor",
                    pageId = VERSION_CREATE_PAGE_ID,
                    sourceId = STS2_TEMPLATE_ID,
                    title = PageTextDirect(localized(language, "android.entrypoint.0107")),
                    subtitle = PageTextDirect(
                        localized(language, "android.entrypoint.0108", listOf(game.displayName)),
                    ),
                ),
            )
            addAll(
                versionEditorWidgets(
                    pageId = VERSION_CREATE_PAGE_ID,
                    parentNodeId = "sts2_version_create_editor",
                    language = language,
                    draft = draft,
                    validationError = validationError,
                    onClientIdChange = { value ->
                        versionStore.updateCreateDraft(game.id) { current -> current.copy(clientIdText = value) }
                    },
                    onVersionNameChange = { value ->
                        versionStore.updateCreateDraft(game.id) { current -> current.copy(versionName = value) }
                    },
                    onGameDirectoryChange = { value ->
                        versionStore.updateCreateDraft(game.id) { current -> current.copy(gameDirectory = value) }
                    },
                    onSaveDirectoryChange = { value ->
                        versionStore.updateCreateDraft(game.id) { current -> current.copy(saveDirectory = value) }
                    },
                    onModDirectoryChange = { value ->
                        versionStore.updateCreateDraft(game.id) { current -> current.copy(modDirectory = value) }
                    },
                    directoryPicker = context.directoryPickerState,
                    blankClientIdPlaceholder = suggestedClientId.toString(),
                    autoAssignClientIdWhenBlank = true,
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_version_create_actions",
                    pageId = VERSION_CREATE_PAGE_ID,
                    parentNodeId = "sts2_version_create_editor",
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 6,
                    widget = PageWidgetButtonStack(
                        actions = listOf(
                            PageActionRegistration(
                                id = "sts2_version_create_save",
                                label = PageTextDirect(localized(language, "android.entrypoint.0109")),
                                style = PageActionStyle.FILLED_TONAL,
                                onClick = {
                                    if (versionStore.saveCreateDraft(game.id)) {
                                        context.replaceCurrentPage(VERSION_MANAGER_PAGE_ID)
                                    } else {
                                        context.refreshPage()
                                    }
                                },
                            ),
                            PageActionRegistration(
                                id = "sts2_version_create_back",
                                label = PageTextDirect(localized(language, "android.entrypoint.0110")),
                                style = PageActionStyle.OUTLINED,
                                onClick = { context.replaceCurrentPage(VERSION_MANAGER_PAGE_ID) },
                            ),
                        ),
                    ),
                ),
            )
        },
    )
}

private fun buildVersionDetailContribution(
    context: PageContext,
    game: GameInstance,
    version: Sts2VersionDefinition,
    selected: Boolean,
    draft: Sts2VersionDraft,
    validationError: Sts2VersionValidationError?,
    versionStore: Sts2VersionStore,
): PageContributionBundle {
    val language = context.strings.language
    fun persistVersionDetailDraft() {
        if (versionStore.saveEditDraft(game.id, version.clientId)) {
            context.refreshUi()
        } else {
            context.refreshPage()
        }
    }
    return PageContributionBundle(
        sourceId = STS2_TEMPLATE_ID,
        page = PageRegistration(
            id = detailPageId(version.clientId),
            sourceId = STS2_TEMPLATE_ID,
            title = PageTextDirect(versionDisplayName(language, version)),
            subtitle = PageTextDirect(
                if (selected) {
                    localized(language, "android.entrypoint.0111")
                } else {
                    localized(language, "android.entrypoint.0112")
                },
            ),
            actionLabel = PageTextDirect(context.strings.commonBack),
            action = { context.goBack() },
            supportedTargets = setOf(PlatformTarget.ANDROID),
        ),
        nodes = buildList {
            add(
                PageSectionRegistration(
                    nodeId = "sts2_version_detail_summary_${version.clientId}",
                    pageId = detailPageId(version.clientId),
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = -1,
                    title = PageTextDirect(localized(language, "android.entrypoint.0113")),
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_version_detail_info_${version.clientId}",
                    pageId = detailPageId(version.clientId),
                    parentNodeId = "sts2_version_detail_summary_${version.clientId}",
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 0,
                    widget = PageWidgetDetailCard(
                        title = PageTextDirect(localized(language, "android.entrypoint.0114")),
                        subtitle = PageTextDirect(
                            localized(language, "android.entrypoint.0115", listOf(game.displayName)),
                        ),
                        rows = versionRows(language, version, selected),
                        tone = if (selected) PageWidgetTone.ACCENT else PageWidgetTone.DEFAULT,
                    ),
                ),
            )
            add(
                PageSectionRegistration(
                    nodeId = "sts2_version_detail_editor_${version.clientId}",
                    pageId = detailPageId(version.clientId),
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 0,
                    title = PageTextDirect(localized(language, "android.entrypoint.0116")),
                ),
            )
            addAll(
                versionEditorWidgets(
                    pageId = detailPageId(version.clientId),
                    parentNodeId = "sts2_version_detail_editor_${version.clientId}",
                    language = language,
                    draft = draft,
                    validationError = validationError,
                    onClientIdChange = { value ->
                        versionStore.updateEditDraft(game.id, version.clientId) { current -> current.copy(clientIdText = value) }
                        persistVersionDetailDraft()
                    },
                    onVersionNameChange = { value ->
                        versionStore.updateEditDraft(game.id, version.clientId) { current -> current.copy(versionName = value) }
                        persistVersionDetailDraft()
                    },
                    onGameDirectoryChange = { value ->
                        versionStore.updateEditDraft(game.id, version.clientId) { current -> current.copy(gameDirectory = value) }
                        persistVersionDetailDraft()
                    },
                    onSaveDirectoryChange = { value ->
                        versionStore.updateEditDraft(game.id, version.clientId) { current -> current.copy(saveDirectory = value) }
                        persistVersionDetailDraft()
                    },
                    onModDirectoryChange = { value ->
                        versionStore.updateEditDraft(game.id, version.clientId) { current -> current.copy(modDirectory = value) }
                        persistVersionDetailDraft()
                    },
                    directoryPicker = context.directoryPickerState,
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_version_detail_actions_${version.clientId}",
                    pageId = detailPageId(version.clientId),
                    parentNodeId = "sts2_version_detail_editor_${version.clientId}",
                    sourceId = STS2_TEMPLATE_ID,
                    orderHint = 6,
                    widget = PageWidgetButtonStack(
                        actions = listOf(
                            PageActionRegistration(
                                id = "sts2_version_delete_${version.clientId}",
                                label = PageTextDirect(localized(language, "android.entrypoint.0117")),
                                style = PageActionStyle.OUTLINED,
                                onClick = {
                                    if (versionStore.deleteVersion(game.id, version.clientId)) {
                                        context.refreshUi()
                                        context.replaceCurrentPage(VERSION_MANAGER_PAGE_ID)
                                    }
                                },
                            ),
                        ),
                    ),
                ),
            )
        },
    )
}

private fun versionEditorWidgets(
    pageId: String,
    parentNodeId: String,
    language: SupportedLanguage,
    draft: Sts2VersionDraft,
    validationError: Sts2VersionValidationError?,
    onClientIdChange: (String) -> Unit,
    onVersionNameChange: (String) -> Unit,
    onGameDirectoryChange: (String) -> Unit,
    onSaveDirectoryChange: (String) -> Unit,
    onModDirectoryChange: (String) -> Unit,
    directoryPicker: com.dreamyloong.tlauncher.sdk.platform.DirectoryPickerState? = null,
    blankClientIdPlaceholder: String = "1",
    autoAssignClientIdWhenBlank: Boolean = false,
): List<PageWidgetRegistration> {
    val clientIdSupportingText = if (autoAssignClientIdWhenBlank) {
        localized(language, "android.entrypoint.0118", listOf(blankClientIdPlaceholder))
    } else {
        localized(language, "android.entrypoint.0119")
    }
    return listOf(
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_client_id",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            orderHint = 0,
            widget = PageWidgetTextInputCard(
                title = PageTextDirect(localized(language, "android.entrypoint.0120")),
                value = PageTextDirect(draft.clientIdText),
                placeholder = PageTextDirect(if (autoAssignClientIdWhenBlank) blankClientIdPlaceholder else "1"),
                supportingText = PageTextDirect(
                    validationErrorText(language, validationError) ?: clientIdSupportingText,
                ),
                onValueChange = onClientIdChange,
            ),
        ),
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_version_name",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            orderHint = 1,
            widget = PageWidgetTextInputCard(
                title = PageTextDirect(localized(language, "android.entrypoint.0121")),
                value = PageTextDirect(draft.versionName),
                placeholder = PageTextDirect(localized(language, "android.entrypoint.0122")),
                supportingText = PageTextDirect(
                    localized(language, "android.entrypoint.0123"),
                ),
                onValueChange = onVersionNameChange,
            ),
        ),
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_game_directory",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            orderHint = 2,
            widget = PageWidgetDirectoryInputCard(
                title = PageTextDirect(localized(language, "android.entrypoint.0124")),
                value = PageTextDirect(draft.gameDirectory),
                placeholder = PageTextDirect("/storage/emulated/0/TLauncher/.sts2/versions/{Version_Number}"),
                supportingText = PageTextDirect(localized(language, "android.entrypoint.0125")),
                onValueChange = onGameDirectoryChange,
                pickButtonLabel = PageTextDirect(localized(language, "android.entrypoint.0126")),
                onPickDirectory = directoryPicker?.let { picker ->
                    { currentValue, onPicked -> picker.pickDirectory(currentValue, onPicked) }
                },
            ),
        ),
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_save_directory",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            orderHint = 3,
            widget = PageWidgetDirectoryInputCard(
                title = PageTextDirect(localized(language, "android.entrypoint.0127")),
                value = PageTextDirect(draft.saveDirectory),
                placeholder = PageTextDirect("/storage/emulated/0/TLauncher/.sts2/saves/{Version_Number}"),
                supportingText = PageTextDirect(localized(language, "android.entrypoint.0128")),
                onValueChange = onSaveDirectoryChange,
                pickButtonLabel = PageTextDirect(localized(language, "android.entrypoint.0129")),
                onPickDirectory = directoryPicker?.let { picker ->
                    { currentValue, onPicked -> picker.pickDirectory(currentValue, onPicked) }
                },
            ),
        ),
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_mod_directory",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            orderHint = 4,
            widget = PageWidgetDirectoryInputCard(
                title = PageTextDirect(localized(language, "android.entrypoint.0130")),
                value = PageTextDirect(draft.modDirectory),
                placeholder = PageTextDirect("/storage/emulated/0/TLauncher/.sts2/mods/{Version_Number}"),
                supportingText = PageTextDirect(localized(language, "android.entrypoint.0131")),
                onValueChange = onModDirectoryChange,
                pickButtonLabel = PageTextDirect(localized(language, "android.entrypoint.0132")),
                onPickDirectory = directoryPicker?.let { picker ->
                    { currentValue, onPicked -> picker.pickDirectory(currentValue, onPicked) }
                },
            ),
        ),
    )
}

private fun launchSettingsWidgets(
    pageId: String,
    parentNodeId: String,
    language: SupportedLanguage,
    draft: Sts2LaunchSettingsDraft,
    shaderCacheEnabled: Boolean,
    shaderCacheEntries: List<Sts2ShaderCacheEntry>,
    validationError: Sts2LaunchSettingsValidationError?,
    onOpenShaderCacheManager: () -> Unit,
    onSpineUpdateDivisorChange: (String) -> Unit,
    onPreloadTrimEnabledChange: (Boolean) -> Unit,
    onAssetLoadingBatchSizeChange: (String) -> Unit,
    onMobileShadersEnabledChange: (Boolean) -> Unit,
    onShaderCacheEnabledChange: (Boolean) -> Unit,
    onRendererChange: (String) -> Unit,
    onParticleScalePercentChange: (String) -> Unit,
    onGlowModeChange: (String) -> Unit,
    onVfxLimitEnabledChange: (Boolean) -> Unit,
): List<PageWidgetRegistration> {
    val normalizedRenderer = normalizeLaunchRenderer(draft.renderer)
    val normalizedGlowMode = normalizeLaunchGlowMode(draft.glowMode)
    val currentShaderCache = shaderCacheEntries.firstOrNull { entry -> entry.isCurrent }
    val shaderCacheManagerSubtitle = currentShaderCache?.let { entry ->
        localized(language, "android.entrypoint.0133", listOf(entry.releaseVersion ?: entry.key, entry.fileCount, formatFileSize(entry.sizeBytes)))
    } ?: localized(language, "android.entrypoint.0134")
    return listOf(
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_spine_update_divisor",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            orderHint = 0,
            widget = PageWidgetTextInputCard(
                title = PageTextDirect(localized(language, "android.entrypoint.0135")),
                value = PageTextDirect(draft.spineUpdateDivisorText),
                placeholder = PageTextDirect("2"),
                supportingText = PageTextDirect(
                    launchSettingsValidationMessage(
                        language = language,
                        validationError = validationError,
                        fieldError = Sts2LaunchSettingsValidationError.INVALID_SPINE_UPDATE_DIVISOR,
                        defaultText = localized(language, "android.entrypoint.0136"),
                    ),
                ),
                onValueChange = onSpineUpdateDivisorChange,
            ),
        ),
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_preload_trim",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            orderHint = 10,
            widget = PageWidgetToggleCard(
                title = PageTextDirect(localized(language, "android.entrypoint.0137")),
                subtitle = PageTextDirect(
                    localized(language, "android.entrypoint.0138"),
                ),
                checked = draft.preloadTrimEnabled,
                onCheckedChange = onPreloadTrimEnabledChange,
            ),
        ),
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_asset_loading_batch_size",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            orderHint = 20,
            widget = PageWidgetTextInputCard(
                title = PageTextDirect(localized(language, "android.entrypoint.0139")),
                value = PageTextDirect(draft.assetLoadingBatchSizeText),
                placeholder = PageTextDirect("8"),
                supportingText = PageTextDirect(
                    launchSettingsValidationMessage(
                        language = language,
                        validationError = validationError,
                        fieldError = Sts2LaunchSettingsValidationError.INVALID_ASSET_LOADING_BATCH_SIZE,
                        defaultText = localized(language, "android.entrypoint.0140"),
                    ),
                ),
                onValueChange = onAssetLoadingBatchSizeChange,
            ),
        ),
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_mobile_shaders",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            orderHint = 30,
            widget = PageWidgetToggleCard(
                title = PageTextDirect(localized(language, "android.entrypoint.0141")),
                subtitle = PageTextDirect(
                    localized(language, "android.entrypoint.0142"),
                ),
                checked = draft.mobileShadersEnabled,
                onCheckedChange = onMobileShadersEnabledChange,
            ),
        ),
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_shader_cache",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            orderHint = 35,
            widget = PageWidgetToggleCard(
                title = PageTextDirect(localized(language, "android.entrypoint.0143")),
                subtitle = PageTextDirect(
                    localized(language, "android.entrypoint.0144"),
                ),
                checked = shaderCacheEnabled,
                onCheckedChange = onShaderCacheEnabledChange,
            ),
        ),
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_shader_cache_manager",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            orderHint = 36,
            widget = PageWidgetDetailCard(
                title = PageTextDirect(localized(language, "android.entrypoint.0145")),
                subtitle = PageTextDirect(shaderCacheManagerSubtitle),
                rows = listOf(
                    row(language, "android.entrypoint.row.0010", shaderCacheEntries.size.toString()),
                ),
                tone = if (currentShaderCache != null) PageWidgetTone.ACCENT else PageWidgetTone.DEFAULT,
                onClick = onOpenShaderCacheManager,
            ),
        ),
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_renderer",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            orderHint = 40,
            widget = PageWidgetChoiceCard(
                title = PageTextDirect(localized(language, "android.entrypoint.0146")),
                subtitle = PageTextDirect(
                    localized(language, "android.entrypoint.0147"),
                ),
                options = listOf(
                    launchRendererOption("vulkan", normalizedRenderer, onRendererChange),
                    launchRendererOption("opengl", normalizedRenderer, onRendererChange),
                ),
            ),
        ),
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_particle_scale_percent",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            orderHint = 50,
            widget = PageWidgetTextInputCard(
                title = PageTextDirect(localized(language, "android.entrypoint.0148")),
                value = PageTextDirect(draft.particleScalePercentText),
                placeholder = PageTextDirect("50"),
                supportingText = PageTextDirect(
                    launchSettingsValidationMessage(
                        language = language,
                        validationError = validationError,
                        fieldError = Sts2LaunchSettingsValidationError.INVALID_PARTICLE_SCALE_PERCENT,
                        defaultText = localized(language, "android.entrypoint.0149"),
                    ),
                ),
                onValueChange = onParticleScalePercentChange,
            ),
        ),
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_glow_mode",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            orderHint = 60,
            widget = PageWidgetChoiceCard(
                title = PageTextDirect(localized(language, "android.entrypoint.0150")),
                subtitle = PageTextDirect(
                    localized(language, "android.entrypoint.0151"),
                ),
                options = listOf(
                    launchGlowModeOption(language, "full", normalizedGlowMode, onGlowModeChange),
                    launchGlowModeOption(language, "reduced", normalizedGlowMode, onGlowModeChange),
                    launchGlowModeOption(language, "minimal", normalizedGlowMode, onGlowModeChange),
                    launchGlowModeOption(language, "low", normalizedGlowMode, onGlowModeChange),
                    launchGlowModeOption(language, "off", normalizedGlowMode, onGlowModeChange),
                ),
            ),
        ),
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_vfx_limit_enabled",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            orderHint = 70,
            widget = PageWidgetToggleCard(
                title = PageTextDirect(localized(language, "android.entrypoint.0152")),
                subtitle = PageTextDirect(
                    localized(language, "android.entrypoint.0153"),
                ),
                checked = draft.vfxLimitEnabled,
                onCheckedChange = onVfxLimitEnabledChange,
            ),
        ),
    )
}

private fun launchRendererOption(
    value: String,
    selectedValue: String,
    onRendererChange: (String) -> Unit,
): PageChoiceOptionRegistration {
    val label = when (value) {
        "opengl" -> "OpenGL"
        else -> "Vulkan"
    }
    return PageChoiceOptionRegistration(
        id = "sts2_launch_renderer_$value",
        label = PageTextDirect(label),
        selected = value == selectedValue,
        onClick = { onRendererChange(value) },
    )
}

private fun launchGlowModeOption(
    language: SupportedLanguage,
    value: String,
    selectedValue: String,
    onGlowModeChange: (String) -> Unit,
): PageChoiceOptionRegistration {
    val label = when (value) {
        "full" -> localized(language, "android.entrypoint.0154")
        "reduced" -> localized(language, "android.entrypoint.0155")
        "minimal" -> localized(language, "android.entrypoint.0156")
        "low" -> localized(language, "android.entrypoint.0157")
        "off" -> localized(language, "android.entrypoint.0158")
        else -> value
    }
    return PageChoiceOptionRegistration(
        id = "sts2_launch_glow_mode_$value",
        label = PageTextDirect(label),
        selected = value == selectedValue,
        onClick = { onGlowModeChange(value) },
    )
}

private fun launchSettingsValidationMessage(
    language: SupportedLanguage,
    validationError: Sts2LaunchSettingsValidationError?,
    fieldError: Sts2LaunchSettingsValidationError,
    defaultText: String,
): String {
    return if (validationError == fieldError) {
        launchSettingsValidationText(language, validationError) ?: defaultText
    } else {
        defaultText
    }
}

private fun launchSettingsValidationText(
    language: SupportedLanguage,
    error: Sts2LaunchSettingsValidationError?,
): String? {
    return when (error) {
        Sts2LaunchSettingsValidationError.INVALID_SPINE_UPDATE_DIVISOR ->
            localized(language, "android.entrypoint.0159")

        Sts2LaunchSettingsValidationError.INVALID_ASSET_LOADING_BATCH_SIZE ->
            localized(language, "android.entrypoint.0160")

        Sts2LaunchSettingsValidationError.INVALID_PARTICLE_SCALE_PERCENT ->
            localized(language, "android.entrypoint.0161")

        null -> null
    }
}

private fun normalizeLaunchGlowMode(glowMode: String): String {
    return when (glowMode.trim().lowercase()) {
        "full" -> "full"
        "reduced" -> "reduced"
        "minimal" -> "minimal"
        "low" -> "low"
        "off" -> "off"
        else -> "reduced"
    }
}

private fun normalizeLaunchRenderer(renderer: String): String {
    return when (renderer.trim().lowercase()) {
        "opengl" -> "opengl"
        else -> "vulkan"
    }
}

private fun normalizeLaunchMode(launchMode: String): String {
    return when (launchMode.trim()) {
        STS2_LAUNCH_MODE_SHADER_WARMUP -> STS2_LAUNCH_MODE_SHADER_WARMUP
        else -> STS2_LAUNCH_MODE_GAME
    }
}

private fun buildSts2LaunchRequest(
    game: GameInstance,
    version: Sts2VersionDefinition?,
    gameFileCheck: TemplateFileCheckResult,
    runtimeReady: Boolean,
    packageName: String?,
    launchMode: String = STS2_LAUNCH_MODE_GAME,
    shaderCacheEnabled: Boolean = false,
): GameLaunchRequest.AndroidRuntime? {
    if (!gameFileCheck.passed || !runtimeReady || version == null) return null
    val gameDirectory = version.gameDirectory.trim()
    if (gameDirectory.isBlank()) return null
    val preparedVersion = version.copy(gameDirectory = gameDirectory)
    return GameLaunchRequest.AndroidRuntime(
        gameInstanceId = game.id,
        gameDisplayName = game.displayName,
        templatePackageId = sts2TemplatePackageId,
        projectDirectory = gameDirectory,
        packFileName = STS2_PACK_FILE_NAME,
        launchContextJson = buildSts2LaunchContextJson(
            version = preparedVersion,
            packageName = packageName?.takeIf { it.isNotBlank() } ?: "com.dreamyloong.tlauncher",
            launchMode = launchMode,
            shaderCacheEnabled = shaderCacheEnabled,
        ),
        launchContextFileName = STS2_LAUNCH_CONTEXT_FILE_NAME,
        nativeLibraryResourceDirectory = STS2_NATIVE_LIBRARY_DIRECTORY,
        dynamicJarResourcePaths = listOf(
            STS2_GODOT_CLASSES_JAR_PATH,
            STS2_FMOD_JAR_PATH,
            STS2_ANDROID_CRYPTO_SUPPORT_JAR_PATH,
        ),
        runtimeBridgeClassName = STS2_ANDROID_RUNTIME_BRIDGE_CLASS,
        hostProjectKey = sts2ShaderCacheHostProjectKey(preparedVersion),
        classLoaderBackedNativeLibraryNames = setOf(
            "libSystem.Security.Cryptography.Native.Android.so",
            "libfmod.so",
            "libfmodstudio.so",
        ),
        nativeLibraryLoadOrder = listOf(
            "libgodot_android.so",
            "libSystem.Native.so",
            "libSystem.IO.Compression.Native.so",
            "libSystem.Globalization.Native.so",
            "libSystem.Security.Cryptography.Native.Android.so",
            "libmonosgen-2.0.so",
            "libmono-component-debugger.so",
            "libmono-component-diagnostics_tracing.so",
            "libmono-component-hot_reload.so",
            "libmono-component-marshal-ilgen.so",
            "libsentry.so",
            "libsteam_api.so",
            "libfmod.so",
            "libfmodstudio.so",
        ),
        hostNativeLibraryExcludes = setOf(
            "libGodotFmod.android.template_release.arm64.so",
            "libspine_godot.android.template_release.arm64.so",
        ),
    )
}

private fun buildSts2LaunchContextJson(
    version: Sts2VersionDefinition,
    packageName: String,
    launchMode: String = STS2_LAUNCH_MODE_GAME,
    shaderCacheEnabled: Boolean = false,
): String {
    val versionId = version.versionId.ifBlank { version.clientId.toString() }
    val configPath = "/data/data/$packageName/files/tlauncher.sts2config_${version.clientId}.json"
    return buildString {
        append('{')
        appendJsonField("versionId", versionId)
        append(',')
        appendJsonField("launchMode", normalizeLaunchMode(launchMode))
        append(',')
        appendJsonField("gameRoot", version.gameDirectory)
        append(',')
        appendJsonField("gameConfigPath", configPath)
        append(',')
        appendJsonField("gameFilesDir", version.gameDirectory)
        append(',')
        appendJsonNumberField("spineUpdateDivisor", version.spineUpdateDivisor)
        append(',')
        appendJsonBooleanField("preloadTrimEnabled", version.preloadTrimEnabled)
        append(',')
        appendJsonNumberField("assetLoadingBatchSize", version.assetLoadingBatchSize)
        append(',')
        appendJsonBooleanField("mobileShadersEnabled", version.mobileShadersEnabled)
        append(',')
        appendJsonBooleanField("shaderCacheEnabled", shaderCacheEnabled)
        append(',')
        appendJsonNumberField("particleScalePercent", version.particleScalePercent)
        append(',')
        appendJsonField("glowMode", normalizeLaunchGlowMode(version.glowMode))
        append(',')
        appendJsonBooleanField("vfxLimitEnabled", version.vfxLimitEnabled)
        append(',')
        appendJsonField("renderer", normalizeLaunchRenderer(version.renderer))
        if (version.saveDirectory.isNotBlank()) {
            append(',')
            appendJsonField("savesDir", version.saveDirectory.trim())
        }
        if (version.modDirectory.isNotBlank()) {
            append(',')
            appendJsonField("modsDir", version.modDirectory.trim())
        }
        append('}')
    }
}

private fun StringBuilder.appendJsonField(
    name: String,
    value: String,
) {
    append('"')
    append(name)
    append('"')
    append(':')
    appendJsonString(value)
}

private fun StringBuilder.appendJsonNumberField(
    name: String,
    value: Int,
) {
    append('"')
    append(name)
    append('"')
    append(':')
    append(value)
}

private fun StringBuilder.appendJsonBooleanField(
    name: String,
    value: Boolean,
) {
    append('"')
    append(name)
    append('"')
    append(':')
    append(value.toString())
}

private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
    append('"')
}

private fun runtimeActions(
    language: SupportedLanguage,
    context: PageContext,
    runtimeReport: Sts2GodotRuntimeReport?,
    onQuickFixRuntime: () -> Boolean,
): List<PageActionRegistration> {
    val actions = mutableListOf<PageActionRegistration>()
    val manageStorageAccessState = context.manageStorageAccessState
    if (runtimeReport?.quickFixAvailable == true) {
        actions += PageActionRegistration(
            id = "sts2_runtime_quick_fix",
            label = PageTextDirect(localized(language, "android.entrypoint.0162")),
            style = PageActionStyle.FILLED_TONAL,
            onClick = {
                onQuickFixRuntime()
                context.refreshPage()
            },
        )
    }
    if (manageStorageAccessState?.isSupported == true && !manageStorageAccessState.isGranted) {
        actions += PageActionRegistration(
            id = "sts2_runtime_request_storage",
            label = PageTextDirect(localized(language, "android.entrypoint.0163")),
            style = PageActionStyle.OUTLINED,
            onClick = manageStorageAccessState.requestAccess,
        )
    }
    actions += PageActionRegistration(
        id = "sts2_runtime_refresh",
        label = PageTextDirect(localized(language, "android.entrypoint.0164")),
        style = PageActionStyle.OUTLINED,
        onClick = { context.refreshPage() },
    )
    return actions
}

private data class Sts2GameFilePanelState(
    val mode: Sts2GameFileCheckMode,
    val status: Sts2GameFileCheckStatus,
    val launchCheck: TemplateFileCheckResult,
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

private fun currentGameFileCheck(
    language: SupportedLanguage,
    version: Sts2VersionDefinition?,
    manageStorageGranted: Boolean,
    steamVerificationEnabled: Boolean,
    steamBranch: String,
    selectedSteamAccount: LauncherAccount?,
    storedSnapshot: Sts2GameFileCheckSnapshot,
    steamVerificationRecord: Sts2SteamVerificationRecord,
): Sts2GameFilePanelState {
    if (!steamVerificationEnabled) {
        return simpleGameFilePanelState(language, version)
    }
    if (version == null) {
        return idleSteamGameFileCheckState(
            language = language,
            version = null,
            message = localized(language, "android.entrypoint.0165"),
        )
    }
    val gameDirectory = version.gameDirectory.trim()
    if (gameDirectory.isBlank()) {
        return idleSteamGameFileCheckState(
            language = language,
            version = version,
            message = localized(language, "android.entrypoint.0166"),
        )
    }
    val matchingSnapshot = storedSnapshot.matches(version, Sts2GameFileCheckMode.STEAM, steamBranch)
    if (matchingSnapshot && storedSnapshot.status == Sts2GameFileCheckStatus.RUNNING) {
        return storedSnapshot.toPanelState(language)
    }
    if (steamVerificationRecord.status != Sts2SteamVerificationStatus.UNVERIFIED) {
        val snapshotPanel = if (matchingSnapshot && storedSnapshot.matchesSteamVerificationRecord(steamVerificationRecord)) {
            storedSnapshot.toPanelState(language)
        } else {
            null
        }
        return snapshotPanel?.copy(
            launchCheck = snapshotPanel.launchCheck.copy(passed = steamVerificationRecord.passed),
        ) ?: steamVerificationRecord.toPanelState(language, version)
    }
    if (!manageStorageGranted) {
        return idleSteamGameFileCheckState(
            language = language,
            version = version,
            message = localized(language, "android.entrypoint.0167"),
        )
    }
    if (selectedSteamAccount == null) {
        return idleSteamGameFileCheckState(
            language = language,
            version = version,
            message = localized(language, "android.entrypoint.0168"),
        )
    }
    return steamVerificationRecord.toPanelState(
        language = language,
        version = version,
        unverifiedMessage = localized(language, "android.entrypoint.0169"),
    )
}

private fun currentDownloadSnapshot(
    version: Sts2VersionDefinition?,
    steamBranch: String,
    selectedSteamAccount: LauncherAccount?,
    storedSnapshot: Sts2GameFileDownloadSnapshot,
): Sts2GameFileDownloadSnapshot {
    if (!storedSnapshot.matches(version, steamBranch)) {
        return Sts2GameFileDownloadSnapshot()
    }
    val storedAccount = storedSnapshot.steamAccountSubjectId?.takeIf { it.isNotBlank() }
    val selectedAccount = selectedSteamAccount?.subjectId
    return if (storedAccount == null || storedAccount == selectedAccount) {
        storedSnapshot
    } else {
        Sts2GameFileDownloadSnapshot()
    }
}

private fun simpleLaunchFileCheck(
    language: SupportedLanguage,
    version: Sts2VersionDefinition?,
): TemplateFileCheckResult {
    if (version == null) {
        return TemplateFileCheckResult(
            passed = false,
            subtitle = localized(language, "android.entrypoint.0170"),
            path = null,
        )
    }
    val gameDirectory = version.gameDirectory.trim()
    if (gameDirectory.isBlank()) {
        return TemplateFileCheckResult(
            passed = false,
            subtitle = localized(language, "android.entrypoint.0171"),
            path = null,
        )
    }
    val filePath = appendPath(gameDirectory, STS2_PACK_FILE_NAME)
    return TemplateFileCheckResult(
        passed = java.io.File(filePath).isFile,
        subtitle = localized(language, "android.entrypoint.0172"),
        path = filePath,
    )
}

private fun simpleGameFilePanelState(
    language: SupportedLanguage,
    version: Sts2VersionDefinition?,
): Sts2GameFilePanelState {
    val launchCheck = simpleLaunchFileCheck(language, version)
    return Sts2GameFilePanelState(
        mode = Sts2GameFileCheckMode.SIMPLE,
        status = Sts2GameFileCheckStatus.COMPLETED,
        launchCheck = launchCheck,
        message = if (launchCheck.passed) {
            localized(language, "android.entrypoint.0173")
        } else {
            launchCheck.subtitle
        },
        targetPath = launchCheck.path,
    )
}

private fun idleSteamGameFileCheckState(
    language: SupportedLanguage,
    version: Sts2VersionDefinition?,
    message: String,
): Sts2GameFilePanelState {
    val targetPath = version?.gameDirectory?.trim()?.takeIf { it.isNotBlank() }
    return Sts2GameFilePanelState(
        mode = Sts2GameFileCheckMode.STEAM,
        status = Sts2GameFileCheckStatus.IDLE,
        launchCheck = TemplateFileCheckResult(
            passed = false,
            subtitle = message,
            path = targetPath,
        ),
        message = message,
        targetPath = targetPath,
        currentFilePath = null,
    )
}

private fun Sts2SteamVerificationRecord.toPanelState(
    language: SupportedLanguage,
    version: Sts2VersionDefinition?,
    unverifiedMessage: String = localized(language, "android.entrypoint.0174"),
): Sts2GameFilePanelState {
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
            Sts2SteamVerificationStatus.PASSED -> localized(language, "android.entrypoint.0175")
            Sts2SteamVerificationStatus.FAILED -> localized(language, "android.entrypoint.0176")
        }
    }
    return Sts2GameFilePanelState(
        mode = Sts2GameFileCheckMode.STEAM,
        status = checkStatus,
        launchCheck = TemplateFileCheckResult(
            passed = passed,
            subtitle = effectiveMessage,
            path = target,
        ),
        message = effectiveMessage,
        targetPath = target,
    )
}

private fun Sts2GameFileCheckSnapshot.toPanelState(language: SupportedLanguage): Sts2GameFilePanelState {
    val target = targetPath?.takeIf { it.isNotBlank() } ?: gameDirectory?.takeIf { it.isNotBlank() }
    val effectiveMessage = message.ifBlank {
        when (status) {
            Sts2GameFileCheckStatus.RUNNING -> localized(language, "android.entrypoint.0177")
            Sts2GameFileCheckStatus.COMPLETED ->
                if (passed) {
                    localized(language, "android.entrypoint.0178")
                } else {
                    localized(language, "android.entrypoint.0179")
                }

            Sts2GameFileCheckStatus.CANCELED -> localized(language, "android.entrypoint.0180")
            Sts2GameFileCheckStatus.FAILED -> localized(language, "android.entrypoint.0181")
            Sts2GameFileCheckStatus.IDLE -> localized(language, "android.entrypoint.0182")
        }
    }
    return Sts2GameFilePanelState(
        mode = mode,
        status = status,
        launchCheck = TemplateFileCheckResult(
            passed = passed && status == Sts2GameFileCheckStatus.COMPLETED,
            subtitle = effectiveMessage,
            path = target,
        ),
        message = effectiveMessage,
        targetPath = target,
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

private fun versionRows(
    language: SupportedLanguage,
    version: Sts2VersionDefinition,
    selected: Boolean,
): List<PageValueItemRegistration> {
    return listOf(
        row(language, "android.entrypoint.row.0011", if (selected) localized(language, "android.entrypoint.0183") else localized(language, "android.entrypoint.0184")),
        row(language, "android.entrypoint.row.0012", version.versionId),
        row(language, "android.entrypoint.row.0013", version.versionName.ifBlank { localized(language, "android.entrypoint.0185") }),
        row(language, "android.entrypoint.row.0014", version.gameDirectory.ifBlank { localized(language, "android.entrypoint.0186") }),
        row(language, "android.entrypoint.row.0015", version.saveDirectory.ifBlank { localized(language, "android.entrypoint.0187") }),
        row(language, "android.entrypoint.row.0016", version.modDirectory.ifBlank { localized(language, "android.entrypoint.0188") }),
    )
}

private fun gameFileCheckRows(
    language: SupportedLanguage,
    gameFileState: Sts2GameFilePanelState,
    steamVerificationEnabled: Boolean,
    selectedSteamBranch: String,
    selectedSteamAccount: LauncherAccount?,
): List<PageValueItemRegistration> {
    return buildList {
        add(
            row(language, "android.entrypoint.row.0017", if (steamVerificationEnabled) {
                    localized(language, "android.entrypoint.0189")
                } else {
                    localized(language, "android.entrypoint.0190")
                }, ),
        )
        if (steamVerificationEnabled) {
            add(
                row(language, "android.entrypoint.row.0018", steamBranchLabel(language, selectedSteamBranch), ),
            )
        }
        add(row(language, "android.entrypoint.row.0019", gameFileStatusText(language, gameFileState.status, gameFileState.launchCheck.passed)))
        add(
            row(language, "android.entrypoint.row.0020", selectedSteamAccount?.let { steamAccountTitle(language, it) }
                    ?: localized(language, "android.entrypoint.0191"), ),
        )
        add(
            row(language, "android.entrypoint.row.0021", gameFileState.targetPath ?: localized(language, "android.entrypoint.0192"), ),
        )
        gameFileState.currentFilePath?.takeIf { gameFileState.status == Sts2GameFileCheckStatus.RUNNING }?.let { currentFile ->
            add(
                row(language, "android.entrypoint.row.0022", currentFile, ),
            )
        }
        if (gameFileState.mode == Sts2GameFileCheckMode.STEAM) {
            gameFileState.expectedFileCount?.let { add(row(language, "android.entrypoint.row.0023", it.toString())) }
            gameFileState.localFileCount?.let { add(row(language, "android.entrypoint.row.0024", it.toString())) }
            gameFileState.checkedFileCount?.let { add(row(language, "android.entrypoint.row.0025", it.toString())) }
            checkProgressText(language, gameFileState)?.takeUnless {
                gameFileState.status == Sts2GameFileCheckStatus.RUNNING
            }?.let { progress ->
                add(row(language, "android.entrypoint.row.0026", progress))
            }
            gameFileState.okFileCount?.let { add(row(language, "android.entrypoint.row.0027", it.toString())) }
            add(
                row(language, "android.entrypoint.row.0028", steamDifferenceSummary(language, gameFileState), ),
            )
            gameFileState.extraFileCount?.takeIf { it > 0 }?.let { count ->
                add(
                    row(language, "android.entrypoint.row.0029", count.toString(), ),
                )
            }
            if (gameFileState.problemFilesPreview.isNotEmpty()) {
                add(
                    row(language, "android.entrypoint.row.0030", gameFileState.problemFilesPreview.joinToString("\n"), ),
                )
            }
        }
    }
}

private fun gameFileDownloadRows(
    language: SupportedLanguage,
    selectedVersion: Sts2VersionDefinition?,
    selectedSteamBranch: String,
    selectedSteamAccount: LauncherAccount?,
    downloadSnapshot: Sts2GameFileDownloadSnapshot,
): List<PageValueItemRegistration> {
    return buildList {
        add(
            row(language, "android.entrypoint.row.0031", steamBranchLabel(language, selectedSteamBranch), ),
        )
        add(
            row(language, "android.entrypoint.row.0032", selectedSteamAccount?.let { steamAccountTitle(language, it) }
                    ?: localized(language, "android.entrypoint.0193"), ),
        )
        add(row(language, "android.entrypoint.row.0033", downloadStatusText(language, downloadSnapshot.status)))
        add(
            row(language, "android.entrypoint.row.0034", selectedVersion?.gameDirectory?.trim()?.takeIf { it.isNotBlank() }
                    ?: localized(language, "android.entrypoint.0194"), ),
        )
    downloadSnapshot.currentFilePath?.takeIf {
        downloadSnapshot.status == Sts2GameFileDownloadStatus.RUNNING ||
            downloadSnapshot.status == Sts2GameFileDownloadStatus.PAUSED
    }?.let { currentFile ->
            add(
                row(language, "android.entrypoint.row.0035", currentFile, ),
            )
        }
        downloadSnapshot.totalFileCount?.let { add(row(language, "android.entrypoint.row.0036", it.toString())) }
        downloadSnapshot.completedFileCount?.let { add(row(language, "android.entrypoint.row.0037", it.toString())) }
        downloadSnapshot.skippedFileCount?.let { add(row(language, "android.entrypoint.row.0038", it.toString())) }
        downloadSnapshot.deletedExtraFileCount?.takeIf { it > 0 }?.let { count ->
            add(
                row(language, "android.entrypoint.row.0039", count.toString(), ),
            )
        }
        downloadProgressText(language, downloadSnapshot)?.takeUnless {
            downloadSnapshot.status == Sts2GameFileDownloadStatus.RUNNING
        }?.let { progress ->
            add(row(language, "android.entrypoint.row.0040", progress))
        }
        currentFileDownloadProgressText(language, downloadSnapshot)?.takeUnless {
            downloadSnapshot.status == Sts2GameFileDownloadStatus.RUNNING
        }?.let { progress ->
            add(row(language, "android.entrypoint.row.0041", progress))
        }
    }
}

private data class Sts2HomeComputedStateKey(
    val gameId: String,
    val selectedClientId: Int?,
    val selectedGameDirectory: String?,
    val selectedSaveDirectory: String?,
    val selectedModDirectory: String?,
    val manageStorageGranted: Boolean,
)

private data class Sts2HomeComputedState(
    val key: Sts2HomeComputedStateKey,
    val runtimeReport: Sts2GodotRuntimeReport?,
    val runtimeFiles: List<Sts2RuntimeFileEntry>,
    val modScanResult: Sts2ModScanResult?,
    val modSettingsSnapshot: Sts2ModSettingsSnapshot,
)

private fun gameFileCheckProgressCard(
    language: SupportedLanguage,
    gameFileState: Sts2GameFilePanelState,
): PageWidgetRegistration? {
    if (gameFileState.status != Sts2GameFileCheckStatus.RUNNING) {
        return null
    }
    return PageWidgetRegistration(
        nodeId = "sts2.game_files.check_progress",
        pageId = PageIds.HOME,
        parentNodeId = "sts2.game_files.section",
        sourceId = STS2_TEMPLATE_ID,
        orderHint = 26,
        widget = PageWidgetProgressCard(
            title = PageTextDirect(localized(language, "android.entrypoint.0195")),
            subtitle = PageTextDirect(
                gameFileState.currentFilePath
                    ?: gameFileState.message,
            ),
            progress = PageProgressRegistration(
                fraction = checkProgressFraction(gameFileState),
                label = checkProgressText(language, gameFileState)?.let(::PageTextDirect),
                supportingText = gameFileState.currentFilePath
                    ?.let { _ -> PageTextDirect(gameFileState.message) },
            ),
            tone = PageWidgetTone.ACCENT,
        ),
    )
}

private fun gameFileDownloadProgressCard(
    language: SupportedLanguage,
    downloadSnapshot: Sts2GameFileDownloadSnapshot,
): PageWidgetRegistration? {
    if (downloadSnapshot.status != Sts2GameFileDownloadStatus.RUNNING) {
        return null
    }
    return PageWidgetRegistration(
        nodeId = "sts2.game_files.download_progress",
        pageId = PageIds.HOME,
        parentNodeId = "sts2.game_files.section",
        sourceId = STS2_TEMPLATE_ID,
        orderHint = 28,
        widget = PageWidgetProgressCard(
            title = PageTextDirect(localized(language, "android.entrypoint.0196")),
            subtitle = PageTextDirect(
                downloadSnapshot.currentFilePath
                    ?: downloadSnapshot.message,
            ),
            progress = PageProgressRegistration(
                fraction = downloadProgressFraction(downloadSnapshot),
                label = downloadProgressText(language, downloadSnapshot)?.let(::PageTextDirect),
                supportingText = buildProgressSupportingText(
                    message = downloadSnapshot.currentFilePath
                        ?.let { downloadSnapshot.message },
                    trailing = currentFileDownloadProgressText(language, downloadSnapshot),
                )?.let(::PageTextDirect),
            ),
            tone = PageWidgetTone.ACCENT,
        ),
    )
}

private fun buildProgressSupportingText(
    message: String?,
    trailing: String?,
): String? {
    return buildList {
        message?.takeIf { it.isNotBlank() }?.let(::add)
        trailing?.takeIf { it.isNotBlank() }?.let(::add)
    }.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

private fun runtimeRows(
    language: SupportedLanguage,
    runtimeReport: Sts2GodotRuntimeReport?,
): List<PageValueItemRegistration> {
    if (runtimeReport == null) {
        return listOf(
            row(language, "android.entrypoint.row.0042", localized(language, "android.entrypoint.0197")),
        )
    }
    return listOf(
        row(language, "android.entrypoint.row.0043", if (runtimeReport.isComplete) localized(language, "android.entrypoint.0198") else localized(language, "android.entrypoint.0199")),
        row(language, "android.entrypoint.row.0044", runtimeReport.runtimeDirectoryPath ?: localized(language, "android.entrypoint.0200")),
        row(language, "android.entrypoint.row.0045", runtimeReport.selectedVersionSourceDirectoryPath ?: localized(language, "android.entrypoint.0201")),
        row(language, "android.entrypoint.row.0046", runtimeReport.bundledReferenceFileCount.toString()),
        row(language, "android.entrypoint.row.0047", runtimeReport.selectedVersionFileCount.toString()),
        row(language, "android.entrypoint.row.0048", runtimeReport.runtimeFileCount.toString()),
        row(language, "android.entrypoint.row.0049", if (runtimeReport.issues.isEmpty()) localized(language, "android.entrypoint.0202") else runtimeReport.issues.size.toString()),
    )
}

private fun openRuntimeManagerAction(
    language: SupportedLanguage,
    context: PageContext,
): PageActionRegistration {
    return PageActionRegistration(
        id = "sts2_open_runtime_manager",
        label = PageTextDirect(localized(language, "android.entrypoint.0203")),
        style = PageActionStyle.OUTLINED,
        onClick = { context.openPage(RUNTIME_MANAGER_PAGE_ID) },
    )
}

private fun runtimeIssueTitle(
    language: SupportedLanguage,
    issue: Sts2RuntimeIssue,
): String {
    return when (issue.source) {
        Sts2RuntimeIssueSource.BUNDLED_RUNTIME ->
            localized(language, "android.entrypoint.0204", listOf(issue.fileName))

        Sts2RuntimeIssueSource.SELECTED_VERSION_RUNTIME ->
            localized(language, "android.entrypoint.0205", listOf(issue.fileName))

        Sts2RuntimeIssueSource.RUNTIME_DIRECTORY ->
            localized(language, "android.entrypoint.0206")
    }
}

private fun runtimeIssueSubtitle(
    language: SupportedLanguage,
    issue: Sts2RuntimeIssue,
): String {
    return when (issue.kind) {
        Sts2RuntimeIssueKind.MANAGE_STORAGE_NOT_GRANTED ->
            localized(language, "android.entrypoint.0207")

        Sts2RuntimeIssueKind.VERSION_NOT_SELECTED ->
            localized(language, "android.entrypoint.0208")

        Sts2RuntimeIssueKind.MISSING_TARGET ->
            localized(language, "android.entrypoint.0209")

        Sts2RuntimeIssueKind.TARGET_MISMATCH ->
            localized(language, "android.entrypoint.0210")

        Sts2RuntimeIssueKind.SOURCE_DIRECTORY_MISSING ->
            localized(language, "android.entrypoint.0211")

        Sts2RuntimeIssueKind.RUNTIME_DIRECTORY_UNAVAILABLE ->
            localized(language, "android.entrypoint.0212")
    }
}

private fun runtimeIssueRows(
    language: SupportedLanguage,
    issue: Sts2RuntimeIssue,
): List<PageValueItemRegistration> {
    return buildList {
        issue.sourcePath?.let { path ->
            add(row(language, "android.entrypoint.row.0050", path))
        }
        issue.targetPath?.let { path ->
            add(row(language, "android.entrypoint.row.0051", path))
        }
    }
}

private fun formatFileSize(sizeBytes: Long): String {
    return when {
        sizeBytes >= 1024L * 1024L -> "${scaledSize(sizeBytes / (1024.0 * 1024.0))} MB"
        sizeBytes >= 1024L -> "${scaledSize(sizeBytes / 1024.0)} KB"
        else -> "$sizeBytes B"
    }
}

private fun scaledSize(value: Double): String {
    val scaled = kotlin.math.round(value * 100.0) / 100.0
    val whole = scaled.toLong().toDouble() == scaled
    return if (whole) {
        scaled.toLong().toString()
    } else {
        scaled.toString()
    }
}

private fun shaderCacheEntryTitle(
    language: SupportedLanguage,
    entry: Sts2ShaderCacheEntry,
): String {
    val releaseLabel = entry.releaseVersion
        ?: localized(language, "android.entrypoint.0213")
    return if (entry.isCurrent) {
        localized(language, "android.entrypoint.0214", listOf(releaseLabel))
    } else {
        localized(language, "android.entrypoint.0215", listOf(releaseLabel))
    }
}

private fun shaderCacheEntryRows(
    language: SupportedLanguage,
    entry: Sts2ShaderCacheEntry,
): List<PageValueItemRegistration> {
    return buildList {
        add(
            row(language, "android.entrypoint.row.0052", if (entry.isCurrent) {
                    localized(language, "android.entrypoint.0216")
                } else {
                    localized(language, "android.entrypoint.0217")
                }, ),
        )
        entry.versionDisplayName?.let { displayName ->
            add(row(language, "android.entrypoint.row.0053", displayName))
        }
        add(row(language, "android.entrypoint.row.0054", entry.key))
        add(
            row(language, "android.entrypoint.row.0055", entry.releaseVersion ?: localized(language, "android.entrypoint.0218"), ),
        )
        add(row(language, "android.entrypoint.row.0056", entry.fileCount.toString()))
        add(row(language, "android.entrypoint.row.0057", formatFileSize(entry.sizeBytes)))
        add(row(language, "android.entrypoint.row.0058", formatTimestamp(language, entry.lastModified)))
        entry.hostProjectDirectoryPath?.let { path ->
            add(row(language, "android.entrypoint.row.0059", path))
        }
        entry.godotUserDirectoryPath?.let { path ->
            add(row(language, "android.entrypoint.row.0060", path))
        }
        entry.markerFilePath?.let { path ->
            add(row(language, "android.entrypoint.row.0061", path))
        }
    }
}

private fun formatTimestamp(
    language: SupportedLanguage,
    timestampMillis: Long,
): String {
    if (timestampMillis <= 0L) {
        return localized(language, "android.entrypoint.0219")
    }
    return runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestampMillis))
    }.getOrDefault(timestampMillis.toString())
}

private fun String.toPageNodeIdPart(): String {
    return map { character ->
        if (character.isLetterOrDigit() || character == '.' || character == '-' || character == '_') {
            character
        } else {
            '_'
        }
    }.joinToString("").ifBlank { "item" }
}

private fun row(
    language: SupportedLanguage,
    labelKey: String,
    value: String,
): PageValueItemRegistration {
    return PageValueItemRegistration(
        label = PageTextDirect(localized(language, labelKey)),
        value = PageTextDirect(value),
    )
}

private fun validationErrorText(
    language: SupportedLanguage,
    error: Sts2VersionValidationError?,
): String? {
    return when (error) {
        Sts2VersionValidationError.INVALID_CLIENT_ID ->
            localized(language, "android.entrypoint.0220")
        Sts2VersionValidationError.DUPLICATE_CLIENT_ID ->
            localized(language, "android.entrypoint.0221")
        Sts2VersionValidationError.INVALID_SPINE_UPDATE_DIVISOR ->
            launchSettingsValidationText(language, Sts2LaunchSettingsValidationError.INVALID_SPINE_UPDATE_DIVISOR)
        Sts2VersionValidationError.INVALID_ASSET_LOADING_BATCH_SIZE ->
            launchSettingsValidationText(language, Sts2LaunchSettingsValidationError.INVALID_ASSET_LOADING_BATCH_SIZE)
        Sts2VersionValidationError.INVALID_PARTICLE_SCALE_PERCENT ->
            launchSettingsValidationText(language, Sts2LaunchSettingsValidationError.INVALID_PARTICLE_SCALE_PERCENT)
        null -> null
    }
}

private fun gameFileCheckActions(
    language: SupportedLanguage,
    context: PageContext,
    selectedVersion: Sts2VersionDefinition?,
    steamVerificationEnabled: Boolean,
    selectedSteamAccount: LauncherAccount?,
    gameFileState: Sts2GameFilePanelState,
    downloadSnapshot: Sts2GameFileDownloadSnapshot,
    onRunGameFileCheck: () -> Unit,
    onCancelGameFileCheck: () -> Unit,
): List<PageActionRegistration> {
    val actions = mutableListOf<PageActionRegistration>()
    val manageStorageAccessState = context.manageStorageAccessState
    if (steamVerificationEnabled && manageStorageAccessState?.isSupported == true && !manageStorageAccessState.isGranted) {
        actions += PageActionRegistration(
            id = "sts2_game_files_request_storage",
            label = PageTextDirect(localized(language, "android.entrypoint.0222")),
            style = PageActionStyle.OUTLINED,
            onClick = manageStorageAccessState.requestAccess,
        )
    }
    if (steamVerificationEnabled && selectedSteamAccount == null) {
        actions += PageActionRegistration(
            id = "sts2_game_files_open_account_manager",
            label = PageTextDirect(localized(language, "android.entrypoint.0223")),
            style = PageActionStyle.OUTLINED,
            onClick = { context.openPage(PageIds.ACCOUNT_MANAGER) },
        )
    }
    if (gameFileState.status == Sts2GameFileCheckStatus.RUNNING) {
        actions += PageActionRegistration(
            id = "sts2_game_files_cancel_check",
            label = PageTextDirect(localized(language, "android.entrypoint.0224")),
            style = PageActionStyle.FILLED_TONAL,
            onClick = onCancelGameFileCheck,
        )
    } else {
        actions += PageActionRegistration(
            id = "sts2_game_files_check",
            label = PageTextDirect(localized(language, "android.entrypoint.0225")),
            style = PageActionStyle.FILLED_TONAL,
            enabled = canRunGameFileCheck(
                selectedVersion = selectedVersion,
                steamVerificationEnabled = steamVerificationEnabled,
                selectedSteamAccount = selectedSteamAccount,
                manageStorageGranted = manageStorageAccessState?.isGranted == true,
                gameFileState = gameFileState,
                downloadSnapshot = downloadSnapshot,
            ),
            onClick = onRunGameFileCheck,
        )
    }
    return actions
}

private fun gameFileDownloadActions(
    language: SupportedLanguage,
    context: PageContext,
    selectedVersion: Sts2VersionDefinition?,
    selectedSteamAccount: LauncherAccount?,
    gameFileState: Sts2GameFilePanelState,
    downloadSnapshot: Sts2GameFileDownloadSnapshot,
    onDownloadGameFiles: () -> Unit,
    onCancelGameFileDownload: () -> Unit,
    onPauseGameFileDownload: () -> Unit,
): List<PageActionRegistration> {
    val actions = mutableListOf<PageActionRegistration>()
    val manageStorageAccessState = context.manageStorageAccessState
    if (manageStorageAccessState?.isSupported == true && !manageStorageAccessState.isGranted) {
        actions += PageActionRegistration(
            id = "sts2_game_files_download_request_storage",
            label = PageTextDirect(localized(language, "android.entrypoint.0226")),
            style = PageActionStyle.OUTLINED,
            onClick = manageStorageAccessState.requestAccess,
        )
    }
    if (selectedSteamAccount == null) {
        actions += PageActionRegistration(
            id = "sts2_game_files_download_open_account_manager",
            label = PageTextDirect(localized(language, "android.entrypoint.0227")),
            style = PageActionStyle.OUTLINED,
            onClick = { context.openPage(PageIds.ACCOUNT_MANAGER) },
        )
    }
    if (downloadSnapshot.status == Sts2GameFileDownloadStatus.RUNNING) {
        actions += PageActionRegistration(
            id = "sts2_game_files_pause_download",
            label = PageTextDirect(localized(language, "android.entrypoint.0228")),
            style = PageActionStyle.OUTLINED,
            onClick = onPauseGameFileDownload,
        )
        actions += PageActionRegistration(
            id = "sts2_game_files_cancel_download",
            label = PageTextDirect(localized(language, "android.entrypoint.0229")),
            style = PageActionStyle.FILLED_TONAL,
            onClick = onCancelGameFileDownload,
        )
    } else {
        actions += PageActionRegistration(
            id = "sts2_game_files_download",
            label = PageTextDirect(
                localized(
                    language,
                    if (downloadSnapshot.status == Sts2GameFileDownloadStatus.PAUSED) {
                        "android.entrypoint.game_files.resume_download"
                    } else {
                        "android.entrypoint.game_files.download_game_files"
                    },
                ),
            ),
            style = PageActionStyle.FILLED_TONAL,
            enabled = canDownloadGameFiles(
                selectedVersion = selectedVersion,
                selectedSteamAccount = selectedSteamAccount,
                manageStorageGranted = manageStorageAccessState?.isGranted == true,
                gameFileState = gameFileState,
                downloadSnapshot = downloadSnapshot,
            ),
            onClick = onDownloadGameFiles,
        )
    }
    return actions
}

private fun modManagerCardSubtitle(
    language: SupportedLanguage,
    selectedVersion: Sts2VersionDefinition?,
    modScanResult: Sts2ModScanResult?,
): String {
    if (selectedVersion == null) {
        return localized(language, "android.entrypoint.0230")
    }
    if (modScanResult == null || !modScanResult.modDirectoryConfigured) {
        return localized(language, "android.entrypoint.0231")
    }
    if (!modScanResult.modDirectoryExists) {
        return localized(language, "android.entrypoint.0232")
    }
    if (modScanResult.mods.isEmpty() && modScanResult.problems.isEmpty()) {
        return localized(language, "android.entrypoint.0233")
    }
    return localized(language, "android.entrypoint.0234", listOf(modScanResult.mods.size, modScanResult.totalProblemEntryCount))
}

private fun modManagerCardRows(
    language: SupportedLanguage,
    selectedVersion: Sts2VersionDefinition?,
    modScanResult: Sts2ModScanResult?,
): List<PageValueItemRegistration> {
    return buildList {
        selectedVersion?.let { version ->
            add(row(language, "android.entrypoint.row.0062", versionDisplayName(language, version)))
        }
        if (modScanResult != null && modScanResult.modDirectoryConfigured) {
            add(row(language, "android.entrypoint.row.0063", modScanResult.modDirectoryPath))
            if (modScanResult.modDirectoryExists) {
                add(row(language, "android.entrypoint.row.0064", modScanResult.mods.size.toString()))
                add(
                    row(language, "android.entrypoint.row.0065", modScanResult.totalProblemEntryCount.toString(), ),
                )
            }
        }
    }
}

private fun modManagerCardTone(
    selectedVersion: Sts2VersionDefinition?,
    modScanResult: Sts2ModScanResult?,
): PageWidgetTone {
    return when {
        selectedVersion == null -> PageWidgetTone.DANGER
        modScanResult == null || !modScanResult.modDirectoryConfigured -> PageWidgetTone.DANGER
        !modScanResult.modDirectoryExists -> PageWidgetTone.DANGER
        modScanResult.totalProblemEntryCount > 0 -> PageWidgetTone.DANGER
        modScanResult.mods.isNotEmpty() -> PageWidgetTone.ACCENT
        else -> PageWidgetTone.DEFAULT
    }
}

private fun modImportStatusTone(status: Sts2ModImportStatus): PageWidgetTone {
    return when (status.kind) {
        Sts2ModImportStatusKind.SUCCESS -> PageWidgetTone.ACCENT
        Sts2ModImportStatusKind.WARNING -> PageWidgetTone.DEFAULT
        Sts2ModImportStatusKind.ERROR -> PageWidgetTone.DANGER
    }
}

private fun modImportStatusRows(
    language: SupportedLanguage,
    status: Sts2ModImportStatus,
): List<PageValueItemRegistration> {
    return buildList {
        add(row(language, "android.entrypoint.row.0066", status.sourceFileName))
        status.modId?.let { modId ->
            add(row(language, "android.entrypoint.row.0067", modId))
        }
        status.targetDirectoryPath?.let { targetDirectoryPath ->
            add(row(language, "android.entrypoint.row.0068", targetDirectoryPath))
        }
        if (status.importedFileNames.isNotEmpty()) {
            add(
                row(language, "android.entrypoint.row.0069", status.importedFileNames.joinToString(", "), ),
            )
        }
        if (status.missingFileNames.isNotEmpty()) {
            add(
                row(language, "android.entrypoint.row.0070", status.missingFileNames.joinToString(", "), ),
            )
        }
    }
}

private fun modPendingReplacementRows(
    language: SupportedLanguage,
    pendingReplacement: Sts2ModPendingReplacement,
): List<PageValueItemRegistration> {
    return buildList {
        add(row(language, "android.entrypoint.row.0071", pendingReplacement.sourceFileName))
        add(row(language, "android.entrypoint.row.0072", pendingReplacement.modId))
        if (pendingReplacement.kind == Sts2ModPendingReplacementKind.MOD_PACKAGE) {
            pendingReplacement.existingVersion?.let { existingVersion ->
                add(row(language, "android.entrypoint.row.0073", existingVersion))
            }
            pendingReplacement.manifestVersion?.let { manifestVersion ->
                add(row(language, "android.entrypoint.row.0074", manifestVersion))
            }
        }
        add(row(language, "android.entrypoint.row.0075", pendingReplacement.targetDirectoryPath))
        if (pendingReplacement.replacingFileNames.isNotEmpty()) {
            add(
                row(language, "android.entrypoint.row.0076", pendingReplacement.replacingFileNames.joinToString(", "), ),
            )
        }
        if (pendingReplacement.addingFileNames.isNotEmpty()) {
            add(
                row(language, "android.entrypoint.row.0077", pendingReplacement.addingFileNames.joinToString(", "), ),
            )
        }
        if (pendingReplacement.missingFileNames.isNotEmpty()) {
            add(
                row(language, "android.entrypoint.row.0078", pendingReplacement.missingFileNames.joinToString(", "), ),
            )
        }
    }
}

private fun modPendingReplacementTitle(
    language: SupportedLanguage,
    pendingReplacement: Sts2ModPendingReplacement,
): String {
    val isUpdate = pendingReplacement.kind == Sts2ModPendingReplacementKind.MOD_PACKAGE &&
        isSts2ModVersionUpdate(pendingReplacement.manifestVersion, pendingReplacement.existingVersion)
    return if (isUpdate) {
        localized(language, "android.entrypoint.0235")
    } else {
        localized(language, "android.entrypoint.0236")
    }
}

private fun modPendingReplacementConfirmLabel(
    language: SupportedLanguage,
    pendingReplacement: Sts2ModPendingReplacement,
): String {
    val isUpdate = pendingReplacement.kind == Sts2ModPendingReplacementKind.MOD_PACKAGE &&
        isSts2ModVersionUpdate(pendingReplacement.manifestVersion, pendingReplacement.existingVersion)
    return if (isUpdate) {
        localized(language, "android.entrypoint.0237")
    } else {
        localized(language, "android.entrypoint.0238")
    }
}

private fun modDetailRows(
    language: SupportedLanguage,
    selectedVersion: Sts2VersionDefinition?,
    mod: Sts2ScannedMod,
    modEnabledState: Sts2ResolvedModEnabledState,
): List<PageValueItemRegistration> {
    return buildList {
        add(row(language, "android.entrypoint.row.0079", mod.manifest.id))
        mod.manifest.name?.takeIf { it.isNotBlank() }?.let { name ->
            add(row(language, "android.entrypoint.row.0080", name))
        }
        add(
            row(language, "android.entrypoint.row.0081", modEnabledStatusLabel(language, modEnabledState), ),
        )
        add(
            row(language, "android.entrypoint.row.0082", mod.manifest.version ?: localized(language, "android.entrypoint.0239"), ),
        )
        add(
            row(language, "android.entrypoint.row.0083", mod.manifest.author ?: localized(language, "android.entrypoint.0240"), ),
        )
        mod.manifest.description?.takeIf { it.isNotBlank() }?.let { description ->
            add(row(language, "android.entrypoint.row.0084", description))
        }
        add(
            row(language, "android.entrypoint.row.0085", mod.manifest.dependencies.takeIf(List<String>::isNotEmpty)?.joinToString(", ")
                    ?: localized(language, "android.entrypoint.0241"), ),
        )
        add(row(language, "android.entrypoint.row.0086", modManagerArtifactSummary(language, mod.manifest)))
        add(
            row(language, "android.entrypoint.row.0087", modArtifactDetectedLabel(language, mod.manifest.hasPck, mod.pckDetected, "${mod.manifest.id}.pck"), ),
        )
        add(
            row(language, "android.entrypoint.row.0088", modArtifactDetectedLabel(language, mod.manifest.hasDll, mod.dllDetected, "${mod.manifest.id}.dll"), ),
        )
        add(
            row(language, "android.entrypoint.row.0089", if (mod.manifest.affectsGameplay) {
                    localized(language, "android.entrypoint.0242")
                } else {
                    localized(language, "android.entrypoint.0243")
                }, ),
        )
        selectedVersion?.let { version ->
            add(row(language, "android.entrypoint.row.0090", version.modDirectory))
        }
        modEnabledState.settingsFilePath?.let { settingsFilePath ->
            add(row(language, "android.entrypoint.row.0091", settingsFilePath))
        }
        modEnabledState.readError?.let { readError ->
            add(row(language, "android.entrypoint.row.0092", readError))
        }
        add(row(language, "android.entrypoint.row.0093", mod.relativeModPath))
        add(row(language, "android.entrypoint.row.0094", mod.manifestFilePath))
        if (mod.issues.isNotEmpty()) {
            add(row(language, "android.entrypoint.row.0095", modManagerIssueSummary(language, mod.issues)))
        }
    }
}

private fun modArtifactDetectedLabel(
    language: SupportedLanguage,
    required: Boolean,
    detectedState: Int,
    fileName: String,
): String {
    if (!required || detectedState == STS2_MOD_ARTIFACT_NOT_REQUIRED) {
        return localized(language, "android.entrypoint.0244")
    }
    return when (detectedState) {
        STS2_MOD_ARTIFACT_DETECTED -> localized(language, "android.entrypoint.0245", listOf(fileName))

        STS2_MOD_ARTIFACT_NOT_DETECTED -> localized(language, "android.entrypoint.0246", listOf(fileName))

        else -> localized(language, "android.entrypoint.0247")
    }
}

private fun modManagerDisplayName(mod: Sts2ScannedMod): String {
    return mod.manifest.name?.takeIf { value -> value.isNotBlank() } ?: mod.manifest.id
}

private fun modManagerSubtitle(
    language: SupportedLanguage,
    mod: Sts2ScannedMod,
): String {
    val description = mod.manifest.description?.trim().orEmpty()
    if (description.isNotBlank()) {
        return description
    }
    if (mod.issues.isNotEmpty()) {
        return modManagerIssueSummary(language, mod.issues)
    }
    return localized(language, "android.entrypoint.0248", listOf(mod.relativeManifestPath))
}

private fun modManagerRows(
    language: SupportedLanguage,
    mod: Sts2ScannedMod,
    modEnabledState: Sts2ResolvedModEnabledState,
): List<PageValueItemRegistration> {
    return buildList {
        add(row(language, "android.entrypoint.row.0096", mod.manifest.id))
        add(row(language, "android.entrypoint.row.0097", modEnabledStatusLabel(language, modEnabledState)))
        add(
            row(language, "android.entrypoint.row.0098", mod.manifest.version ?: localized(language, "android.entrypoint.0249"), ),
        )
        add(
            row(language, "android.entrypoint.row.0099", mod.manifest.author ?: localized(language, "android.entrypoint.0250"), ),
        )
        add(
            row(language, "android.entrypoint.row.0100", mod.manifest.dependencies.takeIf(List<String>::isNotEmpty)?.joinToString(", ")
                    ?: localized(language, "android.entrypoint.0251"), ),
        )
        add(row(language, "android.entrypoint.row.0101", modManagerArtifactSummary(language, mod.manifest)))
        add(
            row(language, "android.entrypoint.row.0102", if (mod.manifest.affectsGameplay) {
                    localized(language, "android.entrypoint.0252")
                } else {
                    localized(language, "android.entrypoint.0253")
                }, ),
        )
        add(row(language, "android.entrypoint.row.0103", mod.relativeModPath))
        add(row(language, "android.entrypoint.row.0104", mod.relativeManifestPath))
        if (mod.issues.isNotEmpty()) {
            add(row(language, "android.entrypoint.row.0105", modManagerIssueSummary(language, mod.issues)))
        }
    }
}

private fun modEnabledStatusLabel(
    language: SupportedLanguage,
    modEnabledState: Sts2ResolvedModEnabledState,
): String {
    return when {
        modEnabledState.readError != null -> localized(language, "android.entrypoint.0254")

        modEnabledState.blockedByGlobalSwitch -> localized(language, "android.entrypoint.0255")

        modEnabledState.enabled && modEnabledState.explicit -> localized(language, "android.entrypoint.0256")

        modEnabledState.enabled -> localized(language, "android.entrypoint.0257")

        else -> localized(language, "android.entrypoint.0258")
    }
}

private fun modEnabledToggleSubtitle(
    language: SupportedLanguage,
    modEnabledState: Sts2ResolvedModEnabledState,
): String {
    return when {
        modEnabledState.readError != null -> localized(language, "android.entrypoint.0259")

        modEnabledState.settingsFilePath == null -> localized(language, "android.entrypoint.0260")

        modEnabledState.blockedByGlobalSwitch -> localized(language, "android.entrypoint.0261")

        modEnabledState.explicit -> localized(language, "android.entrypoint.0262")

        else -> localized(language, "android.entrypoint.0263")
    }
}

private fun modManagerArtifactSummary(
    language: SupportedLanguage,
    manifest: Sts2ModManifest,
): String {
    val parts = buildList {
        if (manifest.hasDll) {
            add("DLL")
        }
        if (manifest.hasPck) {
            add("PCK")
        }
    }
    return if (parts.isNotEmpty()) {
        parts.joinToString(" + ")
    } else {
        localized(language, "android.entrypoint.0264")
    }
}

private fun modManagerIssueSummary(
    language: SupportedLanguage,
    issues: List<Sts2ScannedModIssue>,
): String {
    return issues.joinToString(
        separator = if (language == SupportedLanguage.ZH_CN) "；" else "; ",
    ) { issue ->
        when (issue) {
            is Sts2ScannedModIssue.DuplicateId -> localized(language, "android.entrypoint.0265", listOf(issue.id, issue.duplicateCount))

            is Sts2ScannedModIssue.MissingDependencies -> localized(language, "android.entrypoint.0266", listOf(issue.dependencies.joinToString(", ")))

            Sts2ScannedModIssue.MissingDll -> localized(language, "android.entrypoint.0267")

            Sts2ScannedModIssue.MissingPck -> localized(language, "android.entrypoint.0268")

            Sts2ScannedModIssue.NoRuntimeArtifactDeclared -> localized(language, "android.entrypoint.0269")
        }
    }
}

private fun canRunGameFileCheck(
    selectedVersion: Sts2VersionDefinition?,
    steamVerificationEnabled: Boolean,
    selectedSteamAccount: LauncherAccount?,
    manageStorageGranted: Boolean,
    gameFileState: Sts2GameFilePanelState,
    downloadSnapshot: Sts2GameFileDownloadSnapshot,
): Boolean {
    if (selectedVersion == null || selectedVersion.gameDirectory.trim().isBlank()) {
        return false
    }
    if (
        gameFileState.status == Sts2GameFileCheckStatus.RUNNING ||
        downloadSnapshot.status.blocksGameFileCheck()
    ) {
        return false
    }
    return if (steamVerificationEnabled) {
        manageStorageGranted && selectedSteamAccount != null
    } else {
        true
    }
}

private fun canDownloadGameFiles(
    selectedVersion: Sts2VersionDefinition?,
    selectedSteamAccount: LauncherAccount?,
    manageStorageGranted: Boolean,
    gameFileState: Sts2GameFilePanelState,
    downloadSnapshot: Sts2GameFileDownloadSnapshot,
): Boolean {
    if (selectedVersion == null || selectedVersion.gameDirectory.trim().isBlank()) {
        return false
    }
    if (selectedSteamAccount == null || !manageStorageGranted) {
        return false
    }
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

private fun gameFileCheckCardSubtitle(
    language: SupportedLanguage,
    gameFileState: Sts2GameFilePanelState,
    steamVerificationEnabled: Boolean,
    selectedSteamBranch: String,
): String {
    if (
        (gameFileState.status == Sts2GameFileCheckStatus.RUNNING ||
            gameFileState.status == Sts2GameFileCheckStatus.COMPLETED) &&
        gameFileState.message.isNotBlank()
    ) {
        return gameFileState.message
    }
    if (gameFileState.message.isNotBlank()) {
        return gameFileState.message
    }
    return if (gameFileState.launchCheck.passed) {
        if (steamVerificationEnabled) {
            localized(language, "android.entrypoint.0270", listOf(steamBranchLabel(language, selectedSteamBranch)))
        } else {
            localized(language, "android.entrypoint.0271")
        }
    } else {
        localized(language, "android.entrypoint.0272")
    }
}

private fun gameFileDownloadCardSubtitle(
    language: SupportedLanguage,
    downloadSnapshot: Sts2GameFileDownloadSnapshot,
    selectedSteamBranch: String,
): String {
    if (downloadSnapshot.message.isNotBlank()) {
        return downloadSnapshot.message
    }
    return when (downloadSnapshot.status) {
        Sts2GameFileDownloadStatus.RUNNING ->
            localized(language, "android.entrypoint.0273", listOf(steamBranchLabel(language, selectedSteamBranch)))

        Sts2GameFileDownloadStatus.PAUSED ->
            localized(language, "android.entrypoint.0274")

        Sts2GameFileDownloadStatus.COMPLETED ->
            localized(language, "android.entrypoint.0275", listOf(steamBranchLabel(language, selectedSteamBranch)))

        Sts2GameFileDownloadStatus.CANCELED ->
            localized(language, "android.entrypoint.0276")

        Sts2GameFileDownloadStatus.FAILED ->
            localized(language, "android.entrypoint.0277")

        Sts2GameFileDownloadStatus.IDLE ->
            localized(language, "android.entrypoint.0278")
    }
}

private fun gameFileCheckCardTone(
    gameFileState: Sts2GameFilePanelState,
): PageWidgetTone {
    return when {
        gameFileState.status == Sts2GameFileCheckStatus.RUNNING -> PageWidgetTone.ACCENT
        gameFileState.launchCheck.passed -> PageWidgetTone.ACCENT
        gameFileState.status == Sts2GameFileCheckStatus.FAILED -> PageWidgetTone.DANGER
        gameFileState.status == Sts2GameFileCheckStatus.COMPLETED && !gameFileState.launchCheck.passed -> PageWidgetTone.DANGER
        else -> PageWidgetTone.DEFAULT
    }
}

private fun gameFileDownloadCardTone(
    selectedVersion: Sts2VersionDefinition?,
    selectedSteamAccount: LauncherAccount?,
    manageStorageGranted: Boolean,
    downloadSnapshot: Sts2GameFileDownloadSnapshot,
): PageWidgetTone {
    return when {
        downloadSnapshot.status == Sts2GameFileDownloadStatus.RUNNING -> PageWidgetTone.ACCENT
        downloadSnapshot.status == Sts2GameFileDownloadStatus.PAUSED -> PageWidgetTone.ACCENT
        downloadSnapshot.status == Sts2GameFileDownloadStatus.COMPLETED -> PageWidgetTone.ACCENT
        downloadSnapshot.status == Sts2GameFileDownloadStatus.FAILED -> PageWidgetTone.DANGER
        selectedVersion == null || selectedVersion.gameDirectory.trim().isBlank() -> PageWidgetTone.DANGER
        selectedSteamAccount == null || !manageStorageGranted -> PageWidgetTone.DANGER
        else -> PageWidgetTone.DEFAULT
    }
}

private fun steamBranchOptions(
    language: SupportedLanguage,
    selectedSteamBranch: String,
    enabled: Boolean,
    onSelectSteamBranch: (String) -> Unit,
): List<PageChoiceOptionRegistration> {
    return listOf(
        PageChoiceOptionRegistration(
            id = "sts2_game_files_branch_public",
            label = PageTextDirect(steamBranchLabel(language, STS2_STEAM_BRANCH_PUBLIC)),
            selected = selectedSteamBranch == STS2_STEAM_BRANCH_PUBLIC,
            enabled = enabled,
            onClick = { onSelectSteamBranch(STS2_STEAM_BRANCH_PUBLIC) },
        ),
        PageChoiceOptionRegistration(
            id = "sts2_game_files_branch_public_beta",
            label = PageTextDirect(steamBranchLabel(language, STS2_STEAM_BRANCH_PUBLIC_BETA)),
            selected = selectedSteamBranch == STS2_STEAM_BRANCH_PUBLIC_BETA,
            enabled = enabled,
            onClick = { onSelectSteamBranch(STS2_STEAM_BRANCH_PUBLIC_BETA) },
        ),
    )
}

private fun steamAccountCardSubtitle(
    language: SupportedLanguage,
    savedSteamAccounts: List<LauncherAccount>,
    compatibleSteamAccounts: List<LauncherAccount>,
    selectedSteamAccount: LauncherAccount?,
): String {
    return when {
        savedSteamAccounts.isEmpty() -> localized(language, "android.entrypoint.0279")

        compatibleSteamAccounts.isEmpty() -> localized(language, "android.entrypoint.0280")

        selectedSteamAccount == null -> localized(language, "android.entrypoint.0281")

        else -> localized(language, "android.entrypoint.0282", listOf(steamAccountTitle(language, selectedSteamAccount)))
    }
}

private fun steamAccountRows(
    language: SupportedLanguage,
    savedSteamAccounts: List<LauncherAccount>,
    compatibleSteamAccounts: List<LauncherAccount>,
    selectedSteamAccount: LauncherAccount?,
): List<PageValueItemRegistration> {
    return buildList {
        add(row(language, "android.entrypoint.row.0106", savedSteamAccounts.size.toString()))
        add(row(language, "android.entrypoint.row.0107", compatibleSteamAccounts.size.toString()))
        add(
            row(language, "android.entrypoint.row.0108", localized(language, "android.entrypoint.0283"), ),
        )
        if (selectedSteamAccount == null) {
            add(row(language, "android.entrypoint.row.0109", localized(language, "android.entrypoint.0284")))
            return@buildList
        }
        add(row(language, "android.entrypoint.row.0110", steamAccountTitle(language, selectedSteamAccount)))
        add(row(language, "android.entrypoint.row.0111", selectedSteamAccount.subjectId))
        add(row(language, "android.entrypoint.row.0112", steamLoginModesText(language, selectedSteamAccount)))
        add(
            row(language, "android.entrypoint.row.0113", if (selectedSteamAccount.active) {
                    localized(language, "android.entrypoint.0285")
                } else {
                    localized(language, "android.entrypoint.0286")
                }, ),
        )
    }
}

private fun LauncherAccount.supportsSts2SteamDepot(): Boolean {
    return provider == LauncherAccountProvider.STEAM &&
        SteamAccountLoginMode.CM in loginModes
}

private fun steamAccountOptionLabel(
    language: SupportedLanguage,
    account: LauncherAccount,
): String {
    return localized(language, "android.entrypoint.0287", listOf(steamAccountTitle(language, account), steamLoginModesText(language, account)))
}

private fun steamAccountTitle(
    language: SupportedLanguage,
    account: LauncherAccount,
): String {
    return account.displayName?.takeIf { it.isNotBlank() }
        ?: localized(language, "android.entrypoint.0288", listOf(account.subjectId))
}

private fun steamLoginModesText(
    language: SupportedLanguage,
    account: LauncherAccount,
): String {
    return when {
        account.loginModes.isEmpty() -> localized(language, "android.entrypoint.0289")
        else -> SteamAccountLoginMode.entries
            .filter { mode -> mode in account.loginModes }
            .joinToString(" / ") { mode ->
                when (mode) {
                    SteamAccountLoginMode.CM -> "CM"
                    SteamAccountLoginMode.WEB -> "Web"
                    SteamAccountLoginMode.MOBILE -> "Mobile"
                }
            }
    }
}

private fun gameFileStatusText(
    language: SupportedLanguage,
    status: Sts2GameFileCheckStatus,
    passed: Boolean,
): String {
    return when (status) {
        Sts2GameFileCheckStatus.IDLE -> localized(language, "android.entrypoint.0290")
        Sts2GameFileCheckStatus.RUNNING -> localized(language, "android.entrypoint.0291")
        Sts2GameFileCheckStatus.CANCELED -> localized(language, "android.entrypoint.0292")
        Sts2GameFileCheckStatus.FAILED -> localized(language, "android.entrypoint.0293")
        Sts2GameFileCheckStatus.COMPLETED ->
            if (passed) {
                localized(language, "android.entrypoint.0294")
            } else {
                localized(language, "android.entrypoint.0295")
            }
    }
}

private fun steamDifferenceSummary(
    language: SupportedLanguage,
    gameFileState: Sts2GameFilePanelState,
): String {
    val parts = buildList {
        gameFileState.missingFileCount?.takeIf { it > 0 }?.let { count ->
            add(localized(language, "android.entrypoint.0296", listOf(count)))
        }
        gameFileState.mismatchedFileCount?.takeIf { it > 0 }?.let { count ->
            add(localized(language, "android.entrypoint.0297", listOf(count)))
        }
    }
    return when {
        parts.isNotEmpty() -> parts.joinToString(" / ")
        gameFileState.status == Sts2GameFileCheckStatus.RUNNING -> localized(language, "android.entrypoint.0298")
        gameFileState.status == Sts2GameFileCheckStatus.COMPLETED -> localized(language, "android.entrypoint.0299")
        else -> localized(language, "android.entrypoint.0300")
    }
}

private fun downloadStatusText(
    language: SupportedLanguage,
    status: Sts2GameFileDownloadStatus,
): String {
    return when (status) {
        Sts2GameFileDownloadStatus.IDLE -> localized(language, "android.entrypoint.0301")
        Sts2GameFileDownloadStatus.RUNNING -> localized(language, "android.entrypoint.0302")
        Sts2GameFileDownloadStatus.PAUSED -> localized(language, "android.entrypoint.0303")
        Sts2GameFileDownloadStatus.COMPLETED -> localized(language, "android.entrypoint.0304")
        Sts2GameFileDownloadStatus.CANCELED -> localized(language, "android.entrypoint.0305")
        Sts2GameFileDownloadStatus.FAILED -> localized(language, "android.entrypoint.0306")
    }
}

private fun checkProgressText(
    language: SupportedLanguage,
    gameFileState: Sts2GameFilePanelState,
): String? {
    val total = gameFileState.expectedFileCount ?: return null
    val checked = gameFileState.checkedFileCount ?: return null
    return localized(language, "android.entrypoint.0307", listOf(checked, total))
}

private fun checkProgressFraction(gameFileState: Sts2GameFilePanelState): Float? {
    val total = gameFileState.expectedFileCount ?: return null
    val checked = gameFileState.checkedFileCount ?: return null
    if (total <= 0) {
        return null
    }
    return (checked.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

private fun downloadProgressText(
    language: SupportedLanguage,
    snapshot: Sts2GameFileDownloadSnapshot,
): String? {
    val total = snapshot.totalFileCount
    val completed = snapshot.completedFileCount
    if (total == null || completed == null) {
        return null
    }
    val filesText = localized(language, "android.entrypoint.0308", listOf(completed, total))
    val totalBytes = snapshot.totalBytes
    val downloadedBytes = snapshot.downloadedBytes
    val bytesText = when {
        totalBytes != null && downloadedBytes != null ->
            " (${formatFileSize(downloadedBytes)} / ${formatFileSize(totalBytes)})"

        else -> ""
    }
    return filesText + bytesText
}

private fun downloadProgressFraction(snapshot: Sts2GameFileDownloadSnapshot): Float? {
    val totalBytes = snapshot.totalBytes
    val downloadedBytes = snapshot.downloadedBytes
    if (totalBytes != null && downloadedBytes != null && totalBytes > 0L) {
        return (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    }
    val totalFiles = snapshot.totalFileCount
    val completedFiles = snapshot.completedFileCount
    if (totalFiles != null && completedFiles != null && totalFiles > 0) {
        return (completedFiles.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f)
    }
    return null
}

private fun currentFileDownloadProgressText(
    language: SupportedLanguage,
    snapshot: Sts2GameFileDownloadSnapshot,
): String? {
    val totalBytes = snapshot.currentFileTotalBytes ?: return null
    val downloadedBytes = snapshot.currentFileDownloadedBytes ?: return null
    return localized(language, "android.entrypoint.0309", listOf(formatFileSize(downloadedBytes), formatFileSize(totalBytes)))
}

private fun steamBranchLabel(
    language: SupportedLanguage,
    branch: String,
): String {
    return when (normalizeSteamBranch(branch)) {
        STS2_STEAM_BRANCH_PUBLIC_BETA -> localized(language, "android.entrypoint.0310")
        else -> localized(language, "android.entrypoint.0311")
    }
}

private fun runtimeCardSubtitle(
    language: SupportedLanguage,
    runtimeReport: Sts2GodotRuntimeReport?,
): String {
    if (runtimeReport == null) {
        return localized(language, "android.entrypoint.0312")
    }
    return when {
        runtimeReport.isComplete -> localized(language, "android.entrypoint.0313")
        !runtimeReport.manageStorageGranted -> localized(language, "android.entrypoint.0314")
        !runtimeReport.versionSelected -> localized(language, "android.entrypoint.0315")
        runtimeReport.quickFixAvailable -> localized(language, "android.entrypoint.0316")
        else -> localized(language, "android.entrypoint.0317")
    }
}

private fun versionDisplayName(language: SupportedLanguage, version: Sts2VersionDefinition): String {
    return version.versionName.ifBlank {
        localized(language, "android.entrypoint.version_display_fallback", listOf(version.versionId))
    }
}

private fun detailPageId(clientId: Int): String = "page.template.sts2.version_detail.$clientId"

private fun modDetailPageId(mod: Sts2ScannedMod): String = "page.template.sts2.mod_detail.${mod.discoveryOrder}"

private fun appendPath(directory: String, child: String): String {
    return directory.trimEnd('/', '\\') + "/$child"
}

private fun Sts2VersionDefinition.toDraft(): Sts2VersionDraft {
    return Sts2VersionDraft(
        clientIdText = versionId,
        versionName = versionName,
        gameDirectory = gameDirectory,
        saveDirectory = saveDirectory,
        modDirectory = modDirectory,
        spineUpdateDivisorText = spineUpdateDivisor.toString(),
        preloadTrimEnabled = preloadTrimEnabled,
        assetLoadingBatchSizeText = assetLoadingBatchSize.toString(),
        mobileShadersEnabled = mobileShadersEnabled,
        particleScalePercentText = particleScalePercent.toString(),
        glowMode = glowMode,
        vfxLimitEnabled = vfxLimitEnabled,
        renderer = normalizeLaunchRenderer(renderer),
    )
}

private fun Sts2VersionDefinition.toLaunchSettingsDraft(): Sts2LaunchSettingsDraft {
    return Sts2LaunchSettingsDraft(
        spineUpdateDivisorText = spineUpdateDivisor.toString(),
        preloadTrimEnabled = preloadTrimEnabled,
        assetLoadingBatchSizeText = assetLoadingBatchSize.toString(),
        mobileShadersEnabled = mobileShadersEnabled,
        particleScalePercentText = particleScalePercent.toString(),
        glowMode = normalizeLaunchGlowMode(glowMode),
        vfxLimitEnabled = vfxLimitEnabled,
        renderer = normalizeLaunchRenderer(renderer),
    )
}
