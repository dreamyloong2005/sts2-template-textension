package com.dreamyloong.template.sts2.windows

import com.dreamyloong.template.sts2.PersistentSts2VersionStore
import com.dreamyloong.template.sts2.STS2_DISPLAY_NAME
import com.dreamyloong.template.sts2.STS2_TEMPLATE_ID
import com.dreamyloong.template.sts2.STS2_WINDOWS_TEMPLATE_ID
import com.dreamyloong.template.sts2.Sts2LaunchSettingsDraft
import com.dreamyloong.template.sts2.Sts2GameFileCheckMode
import com.dreamyloong.template.sts2.Sts2GameFileCheckStatus
import com.dreamyloong.template.sts2.Sts2ModImportCoordinator
import com.dreamyloong.template.sts2.Sts2ModImportStatus
import com.dreamyloong.template.sts2.Sts2ModImportStatusKind
import com.dreamyloong.template.sts2.Sts2ModScanResult
import com.dreamyloong.template.sts2.Sts2ModSettingsSnapshot
import com.dreamyloong.template.sts2.Sts2ModPendingReplacement
import com.dreamyloong.template.sts2.Sts2ModPendingReplacementKind
import com.dreamyloong.template.sts2.Sts2ResolvedModEnabledState
import com.dreamyloong.template.sts2.Sts2ScannedMod
import com.dreamyloong.template.sts2.Sts2ScannedModIssue
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
import com.dreamyloong.tlauncher.sdk.account.LauncherAccount
import com.dreamyloong.tlauncher.sdk.account.LauncherAccountProvider
import com.dreamyloong.tlauncher.sdk.extension.ExtensionCapability
import com.dreamyloong.tlauncher.sdk.extension.ExtensionCompatibility
import com.dreamyloong.tlauncher.sdk.extension.ExtensionContext
import com.dreamyloong.tlauncher.sdk.extension.ExtensionEntrypoint
import com.dreamyloong.tlauncher.sdk.extension.ExtensionFeature
import com.dreamyloong.tlauncher.sdk.extension.ExtensionStateStore
import com.dreamyloong.tlauncher.sdk.extension.LauncherExtension
import com.dreamyloong.tlauncher.sdk.i18n.SupportedLanguage
import com.dreamyloong.tlauncher.sdk.model.ExtensionIdentityId
import com.dreamyloong.tlauncher.sdk.model.ExtensionKind
import com.dreamyloong.tlauncher.sdk.model.ExtensionManifest
import com.dreamyloong.tlauncher.sdk.model.GameInstance
import com.dreamyloong.tlauncher.sdk.model.GameInstanceId
import com.dreamyloong.tlauncher.sdk.model.LaunchSupportLevel
import com.dreamyloong.tlauncher.sdk.model.PlatformTarget
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
import com.dreamyloong.tlauncher.sdk.page.PageRegistration
import com.dreamyloong.tlauncher.sdk.page.PageSectionRegistration
import com.dreamyloong.tlauncher.sdk.page.PageTextDirect
import com.dreamyloong.tlauncher.sdk.page.PageValueItemRegistration
import com.dreamyloong.tlauncher.sdk.page.PageWidgetAutoRefresh
import com.dreamyloong.tlauncher.sdk.page.PageWidgetButtonStack
import com.dreamyloong.tlauncher.sdk.page.PageWidgetDetailCard
import com.dreamyloong.tlauncher.sdk.page.PageWidgetChoiceCard
import com.dreamyloong.tlauncher.sdk.page.PageWidgetDirectoryInputCard
import com.dreamyloong.tlauncher.sdk.page.PageWidgetLaunchBar
import com.dreamyloong.tlauncher.sdk.page.PageWidgetRegistration
import com.dreamyloong.tlauncher.sdk.page.PageWidgetTextInputCard
import com.dreamyloong.tlauncher.sdk.page.PageWidgetToggleCard
import com.dreamyloong.tlauncher.sdk.page.PageWidgetTone
import com.dreamyloong.tlauncher.sdk.plugin.PageContributionProviderExtension
import com.dreamyloong.tlauncher.sdk.template.TemplatePackage
import com.dreamyloong.tlauncher.sdk.template.TemplatePlatformFacet
import com.dreamyloong.tlauncher.sdk.template.TemplateProviderExtension
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.io.File

private const val WINDOWS_LAUNCH_OPTIONS_PAGE_ID = "page.template.sts2.windows.launch_options"
private const val WINDOWS_MOD_MANAGER_PAGE_ID = "page.template.sts2.windows.mods"
private const val WINDOWS_RUNNING_PROCESS_PAGE_ID = "page.template.sts2.windows.running_processes"
private const val WINDOWS_VERSION_MANAGER_PAGE_ID = "page.template.sts2.windows.version_manager"
private const val WINDOWS_VERSION_CREATE_PAGE_ID = "page.template.sts2.windows.version_create"
private const val WINDOWS_STS2_STEAM_RELATIVE_GAME_DIRECTORY = "steamapps\\common\\Slay the Spire 2"

private val sts2TemplatePackageId = ExtensionIdentityId(STS2_TEMPLATE_ID)

private enum class WindowsLaunchMode {
    DEFAULT,
    STEAM,
}

private enum class WindowsFastMpRole {
    HOST,
    CLIENT,
}

private data class WindowsLaunchOptionsState(
    val launchMode: WindowsLaunchMode = WindowsLaunchMode.DEFAULT,
    val clientIdText: String = "",
    val steamSaveDirectoryName: String = "",
    val fastMpEnabled: Boolean = false,
    val fastMpRole: WindowsFastMpRole = WindowsFastMpRole.CLIENT,
    val consoleLogEnabled: Boolean = false,
)

private data class WindowsResolvedLaunchOptions(
    val launchMode: WindowsLaunchMode,
    val clientId: Int?,
    val steamSaveDirectoryName: String?,
    val saveDirectory: String,
    val fastMpEnabled: Boolean,
    val fastMpRole: WindowsFastMpRole,
    val consoleLogEnabled: Boolean,
)

private data class WindowsRunningProcessRecord(
    val instanceId: GameInstanceId,
    val versionId: Int,
    val clientId: Int,
    val pid: Long,
    val startedAtMillis: Long,
)

object Sts2TemplateWindowsEntrypoint : ExtensionEntrypoint {
    override fun createExtension(): LauncherExtension = Sts2TemplateWindowsExtension()
}

private class Sts2TemplateWindowsExtension : LauncherExtension {
    private val runningProcessStore = InMemoryWindowsRunningProcessStateStore()

    override val extension: ExtensionManifest = ExtensionManifest(
        id = STS2_WINDOWS_TEMPLATE_ID,
        kind = ExtensionKind.TEMPLATE,
        supportedTargets = setOf(PlatformTarget.WINDOWS),
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
    override val entrypoint: String = Sts2TemplateWindowsEntrypoint::class.qualifiedName.orEmpty()

    override fun createFeatures(context: ExtensionContext): List<ExtensionFeature> {
        val templateStrings = loadSts2TemplateStrings(
            resources = context.packageResources,
            target = Sts2TemplateLocalizationTarget.WINDOWS,
        )
        return listOf(
            Sts2TemplateWindowsProvider(templateStrings),
            Sts2TemplateWindowsPageContributionProvider(
                versionStore = PersistentSts2VersionStore(context.stateStore),
                launchOptionsStore = PersistentWindowsLaunchOptionsStateStore(context.stateStore),
                runningProcessStore = runningProcessStore,
                modImportCoordinator = Sts2ModImportCoordinator(
                    stateStore = context.stateStore,
                    hostPaths = context.hostPaths,
                ),
                gameFileCoordinator = Sts2WindowsSteamDepotGameFileCoordinator(
                    stateStore = context.stateStore,
                    hostPaths = context.hostPaths,
                    steamDepot = context.hostServices.steamDepot,
                ),
            ),
        )
    }
}

private class Sts2TemplateWindowsProvider(
    private val strings: Sts2TemplateStrings,
) : TemplateProviderExtension {
    override fun provideTemplatePackages(): List<TemplatePackage> {
        return listOf(
            TemplatePackage(
                extension = ExtensionManifest(
                    id = STS2_WINDOWS_TEMPLATE_ID,
                    kind = ExtensionKind.TEMPLATE,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
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
                        target = PlatformTarget.WINDOWS,
                        supportLevel = LaunchSupportLevel.LAUNCHABLE,
                        capabilityKeys = setOf("browse", "version_manage", "launch"),
                        capabilityLabels = strings.capabilityLabels,
                    ),
                ),
            ),
        )
    }
}

private class Sts2TemplateWindowsPageContributionProvider(
    private val versionStore: Sts2VersionStore,
    private val launchOptionsStore: PersistentWindowsLaunchOptionsStateStore,
    private val runningProcessStore: InMemoryWindowsRunningProcessStateStore,
    private val modImportCoordinator: Sts2ModImportCoordinator,
    private val gameFileCoordinator: Sts2WindowsSteamDepotGameFileCoordinator,
) : PageContributionProviderExtension {
    override fun providePageContributions(context: PageContext): List<PageContributionBundle> {
        val template = context.currentTemplate ?: return emptyList()
        val currentGame = context.currentGame ?: return emptyList()
        if (template.packageId != sts2TemplatePackageId || context.target != PlatformTarget.WINDOWS) {
            return emptyList()
        }

        val language = context.strings.language
        val userHomeDirectory = windowsUserHomeDirectory()
        val detectedSteamGameDirectory = detectWindowsSteamGameDirectory()
        val detectedSteamSaveDirectories = detectWindowsSteamSaveDirectoryNames(userHomeDirectory)
        val versions = versionStore.versions(currentGame.id).map { version ->
            version.withWindowsAppliedLaunchOptions(
                userHomeDirectory = userHomeDirectory,
                launchOptions = launchOptionsStore.state(currentGame.id, version.clientId),
                detectedSteamSaveDirectories = detectedSteamSaveDirectories,
            )
        }
        val selectedClientId = versionStore.selectedClientId(currentGame.id)
        val selectedVersion = versions.firstOrNull { version -> version.clientId == selectedClientId }
        val savedSteamAccounts = context.accounts.filter { account ->
            account.provider == LauncherAccountProvider.STEAM
        }
        val gameFilePreferences = gameFileCoordinator.preferences(currentGame.id)
        val selectedSteamBranch = normalizeSteamBranch(gameFilePreferences.steamBranch)
        val selectedSteamAccount = savedSteamAccounts.firstOrNull { account ->
            account.subjectId == gameFilePreferences.selectedSteamAccountSubjectId
        }
        val gameFileState = currentWindowsGameFileCheck(
            language = language,
            version = selectedVersion,
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
        val downloadSnapshot = currentWindowsDownloadSnapshot(
            version = selectedVersion,
            steamBranch = selectedSteamBranch,
            selectedSteamAccount = selectedSteamAccount,
            storedSnapshot = gameFileCoordinator.downloadSnapshot(currentGame.id),
        )
        val selectedLaunchOptions = selectedVersion?.let { version ->
            resolveWindowsLaunchOptions(
                version = version,
                state = launchOptionsStore.state(currentGame.id, version.clientId),
                userHomeDirectory = userHomeDirectory,
                detectedSteamSaveDirectories = detectedSteamSaveDirectories,
            )
        }
        val runningProcesses = runningProcessStore.records()
        val selectedLaunchSettingsDraft = selectedVersion?.let { version ->
            versionStore.launchSettingsDraft(currentGame.id, version.clientId) ?: version.toLaunchSettingsDraft()
        }
        val modScanResult = scanSts2LocalMods(selectedVersion)
        val modSettingsSnapshot = readSts2ModSettingsSnapshot(selectedVersion)
        val modImportStatus = modImportCoordinator.status(currentGame.id)
        val pendingModReplacement = modImportCoordinator.pendingReplacement(currentGame.id)
        val suggestedCreateClientId = versionStore.suggestedCreateClientId(currentGame.id)
        val createDraft = versionStore.createDraft(currentGame.id).withWindowsDerivedPaths(
            userHomeDirectory = userHomeDirectory,
            detectedSteamGameDirectory = detectedSteamGameDirectory,
        )
        val createError = versionStore.createValidationError(currentGame.id)

        return buildList {
            add(
                buildWindowsHomeContribution(
                    context = context,
                    game = currentGame,
                    selectedVersion = selectedVersion,
                    modScanResult = modScanResult,
                    selectedLaunchOptions = selectedLaunchOptions,
                    savedSteamAccounts = savedSteamAccounts,
                    selectedSteamAccount = selectedSteamAccount,
                    gameFilePreferences = gameFilePreferences,
                    gameFileState = gameFileState,
                    downloadSnapshot = downloadSnapshot,
                    gameFileCoordinator = gameFileCoordinator,
                    runningProcesses = runningProcesses,
                    runningProcessStore = runningProcessStore,
                ),
            )
            add(
                buildWindowsLaunchOptionsContribution(
                    context = context,
                    game = currentGame,
                    selectedVersion = selectedVersion,
                    selectedLaunchOptions = selectedLaunchOptions,
                    launchOptionsState = selectedVersion?.let { version ->
                        launchOptionsStore.state(currentGame.id, version.clientId)
                    },
                    launchSettingsDraft = selectedLaunchSettingsDraft,
                    versionStore = versionStore,
                    detectedSteamSaveDirectories = detectedSteamSaveDirectories,
                    launchOptionsStore = launchOptionsStore,
                ),
            )
            add(
                buildWindowsModManagerContribution(
                    context = context,
                    selectedVersion = selectedVersion,
                    modScanResult = modScanResult,
                    modSettingsSnapshot = modSettingsSnapshot,
                    modImportStatus = modImportStatus,
                    pendingReplacement = pendingModReplacement,
                    onImportModPackage = context.filePickerState?.takeIf { it.isSupported }?.let { filePicker ->
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
                    },
                    onConfirmPendingReplacement = {
                        modImportCoordinator.confirmPendingReplacement(currentGame.id, language)
                        context.refreshPage()
                    },
                    onCancelPendingReplacement = {
                        modImportCoordinator.cancelPendingReplacement(currentGame.id, language)
                        context.refreshPage()
                    },
                ),
            )
            add(
                buildWindowsRunningProcessContribution(
                    context = context,
                    versions = versions,
                    runningProcesses = runningProcesses,
                    runningProcessStore = runningProcessStore,
                ),
            )
            add(
                buildWindowsVersionManagerContribution(
                    context = context,
                    game = currentGame,
                    versions = versions,
                    selectedClientId = selectedClientId,
                    versionStore = versionStore,
                    detectedSteamGameDirectory = detectedSteamGameDirectory,
                ),
            )
            add(
                buildWindowsCreateVersionContribution(
                    context = context,
                    game = currentGame,
                    draft = createDraft,
                    validationError = createError,
                    suggestedClientId = suggestedCreateClientId,
                    versionStore = versionStore,
                    detectedSteamGameDirectory = detectedSteamGameDirectory,
                ),
            )
            versions.forEach { version ->
                add(
                    buildWindowsVersionDetailContribution(
                        context = context,
                        game = currentGame,
                        version = version,
                        selected = version.clientId == selectedClientId,
                        launchOptions = resolveWindowsLaunchOptions(
                            version = version,
                            state = launchOptionsStore.state(currentGame.id, version.clientId),
                            userHomeDirectory = userHomeDirectory,
                            detectedSteamSaveDirectories = detectedSteamSaveDirectories,
                        ),
                        draft = (versionStore.editDraft(currentGame.id, version.clientId) ?: version.toEditableDraft())
                            .withWindowsDerivedPaths(
                                userHomeDirectory = userHomeDirectory,
                                detectedSteamGameDirectory = detectedSteamGameDirectory,
                            ),
                        validationError = versionStore.editValidationError(currentGame.id, version.clientId),
                        versionStore = versionStore,
                        launchOptionsStore = launchOptionsStore,
                        runningProcessStore = runningProcessStore,
                        detectedSteamGameDirectory = detectedSteamGameDirectory,
                    ),
                )
            }
            modScanResult?.mods?.forEach { mod ->
                add(
                    buildWindowsModDetailContribution(
                        context = context,
                        selectedVersion = selectedVersion,
                        mod = mod,
                        modEnabledState = resolveSts2ModEnabledState(modSettingsSnapshot, mod.manifest.id),
                        pendingReplacement = pendingModReplacement,
                        onImportPck = if (mod.manifest.hasPck) {
                            context.filePickerState?.takeIf { it.isSupported }?.let { filePicker ->
                                {
                                    filePicker.pickFile(listOf("application/octet-stream", "*/*")) { pickedFile ->
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
                            context.filePickerState?.takeIf { it.isSupported }?.let { filePicker ->
                                {
                                    filePicker.pickFile(listOf("application/x-msdownload", "application/octet-stream", "*/*")) { pickedFile ->
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
                        onConfirmPendingReplacement = {
                            modImportCoordinator.confirmPendingReplacement(currentGame.id, language)
                            context.refreshPage()
                        },
                        onCancelPendingReplacement = {
                            modImportCoordinator.cancelPendingReplacement(currentGame.id, language)
                            context.refreshPage()
                        },
                    ),
                )
            }
        }
    }
}

private fun buildWindowsHomeContribution(
    context: PageContext,
    game: GameInstance,
    selectedVersion: Sts2VersionDefinition?,
    modScanResult: Sts2ModScanResult?,
    selectedLaunchOptions: WindowsResolvedLaunchOptions?,
    savedSteamAccounts: List<LauncherAccount>,
    selectedSteamAccount: LauncherAccount?,
    gameFilePreferences: com.dreamyloong.template.sts2.Sts2GameFilePreferences,
    gameFileState: WindowsGameFilePanelState,
    downloadSnapshot: com.dreamyloong.template.sts2.Sts2GameFileDownloadSnapshot,
    gameFileCoordinator: Sts2WindowsSteamDepotGameFileCoordinator,
    runningProcesses: List<WindowsRunningProcessRecord>,
    runningProcessStore: InMemoryWindowsRunningProcessStateStore,
): PageContributionBundle {
    val language = context.strings.language
    val launchExecutable = selectedVersion?.let(::windowsLaunchExecutableFile)
    val launchExecutableExists = launchExecutable?.isFile == true
    val launchBlockedBySteamVerification = gameFilePreferences.steamVerificationEnabled &&
        !windowsSteamVerificationPassed(gameFileState)
    val canLaunch = launchExecutableExists && !launchBlockedBySteamVerification
    val hasRunningProcesses = runningProcesses.isNotEmpty()
    val requestedClientId = selectedLaunchOptions?.let(::windowsRecordedLaunchClientId)
    val currentLaunchConflict = requestedClientId != null && runningProcesses.any { record -> record.clientId == requestedClientId }
    val showMultiLaunchAction = when {
        selectedVersion == null -> null
        !canLaunch -> null
        !hasRunningProcesses -> null
        requestedClientId == null -> null
        runningProcesses.any { record -> record.clientId == requestedClientId } -> false
        else -> true
    }
    return PageContributionBundle(
        sourceId = STS2_TEMPLATE_ID,
        nodes = buildList {
            add(
            PageWidgetRegistration(
                nodeId = "sts2_windows_current_version",
                pageId = PageIds.HOME,
                parentNodeId = "home_current_game",
                sourceId = STS2_TEMPLATE_ID,
                orderHint = 10,
                supportedTargets = setOf(PlatformTarget.WINDOWS),
                widget = PageWidgetDetailCard(
                    title = PageTextDirect(localized(language, "windows.entrypoint.0001")),
                    subtitle = PageTextDirect(
                        selectedVersion?.let { version -> versionDisplayName(language, version) }
                            ?: localized(language, "windows.entrypoint.0002"),
                    ),
                    rows = selectedVersion?.let { version ->
                        selectedVersionRows(
                            language = language,
                            version = version,
                            launchOptions = selectedLaunchOptions,
                        )
                    }.orEmpty(),
                    tone = if (selectedVersion != null) PageWidgetTone.ACCENT else PageWidgetTone.DANGER,
                    onClick = { context.openPage(WINDOWS_VERSION_MANAGER_PAGE_ID) },
                ),
            ),
            )
            if (hasRunningProcesses) {
                add(
                    windowsRunningAutoRefreshRegistration(
                        nodeId = "sts2_windows_running_status_auto_refresh",
                        pageId = PageIds.HOME,
                        sourceId = STS2_TEMPLATE_ID,
                        runningProcessStore = runningProcessStore,
                        context = context,
                    ),
                )
            }
            add(
            PageWidgetRegistration(
                nodeId = "sts2_windows_mod_manager",
                pageId = PageIds.HOME,
                parentNodeId = "home_current_game",
                sourceId = STS2_TEMPLATE_ID,
                orderHint = 20,
                supportedTargets = setOf(PlatformTarget.WINDOWS),
                widget = PageWidgetDetailCard(
                    title = PageTextDirect(localized(language, "windows.entrypoint.0003")),
                    subtitle = PageTextDirect(
                        if (selectedVersion == null) {
                            localized(language, "windows.entrypoint.0004")
                        } else {
                            localized(language, "windows.entrypoint.0005")
                        }
                    ),
                    rows = selectedVersion?.let { version ->
                        buildList {
                            add(
                            row(language, "windows.entrypoint.row.0001", version.modDirectory),
                            )
                            add(row(language, "windows.entrypoint.row.0002", windowsSettingsSavePath(language, version.saveDirectory)))
                            modScanResult?.let { result ->
                                add(row(language, "windows.entrypoint.row.0003", result.mods.size.toString()))
                                add(row(language, "windows.entrypoint.row.0004", result.totalProblemEntryCount.toString()))
                            }
                        }
                    }.orEmpty(),
                    tone = if (selectedVersion != null) PageWidgetTone.DEFAULT else PageWidgetTone.DANGER,
                    onClick = { context.openPage(WINDOWS_MOD_MANAGER_PAGE_ID) },
                ),
            ),
            )
            addAll(
                buildWindowsGameFileHomeWidgets(
                    context = context,
                    gameId = game.id,
                    language = language,
                    selectedVersion = selectedVersion,
                    savedSteamAccounts = savedSteamAccounts,
                    selectedSteamAccount = selectedSteamAccount,
                    preferences = gameFilePreferences,
                    gameFileState = gameFileState,
                    downloadSnapshot = downloadSnapshot,
                    coordinator = gameFileCoordinator,
                ),
            )
            add(
            PageWidgetRegistration(
                nodeId = "sts2_windows_launch_bar",
                pageId = PageIds.HOME,
                sourceId = STS2_TEMPLATE_ID,
                orderHint = 0,
                supportedTargets = setOf(PlatformTarget.WINDOWS),
                placement = PageNodePlacement.FOOTER,
                footerLayout = PageFooterLayoutRegistration(
                    horizontalPaddingDp = 16,
                    topPaddingDp = 2,
                    bottomPaddingDp = 6,
                ),
                widget = PageWidgetLaunchBar(
                    primaryAction = PageActionRegistration(
                        id = "sts2_windows_launch",
                        label = PageTextDirect(
                            when {
                                hasRunningProcesses -> localized(language, "windows.entrypoint.0006")
                                else -> localized(language, "windows.entrypoint.0007")
                            },
                        ),
                        style = PageActionStyle.FILLED_TONAL,
                        enabled = canLaunch && !hasRunningProcesses && !currentLaunchConflict,
                        onClick = {
                            selectedVersion?.let { version ->
                                launchWindowsVersion(
                                    instanceId = game.id,
                                    version = version,
                                    launchOptions = selectedLaunchOptions,
                                    runningProcessStore = runningProcessStore,
                                )
                                context.refreshUi()
                            }
                        },
                    ),
                    secondaryActions = buildList {
                        if (hasRunningProcesses) {
                            add(
                                PageActionRegistration(
                                    id = "sts2_windows_force_close_running",
                                    label = PageTextDirect(localized(language, "windows.entrypoint.0008")),
                                    compactLabel = PageTextDirect(localized(language, "windows.entrypoint.0009")),
                                    style = PageActionStyle.OUTLINED,
                                    onClick = {
                                        if (runningProcesses.size == 1) {
                                            forceCloseWindowsRunningRecord(
                                                record = runningProcesses.first(),
                                                runningProcessStore = runningProcessStore,
                                            )
                                            context.refreshUi()
                                        } else {
                                            context.openPage(WINDOWS_RUNNING_PROCESS_PAGE_ID)
                                        }
                                    },
                                ),
                            )
                            if (showMultiLaunchAction == true) {
                                add(
                                    PageActionRegistration(
                                        id = "sts2_windows_multi_launch",
                                        label = PageTextDirect(localized(language, "windows.entrypoint.0010")),
                                        compactLabel = PageTextDirect(localized(language, "windows.entrypoint.0011")),
                                        style = PageActionStyle.FILLED_TONAL,
                                        enabled = canLaunch,
                                        onClick = {
                                            selectedVersion?.let { version ->
                                                launchWindowsVersion(
                                                    instanceId = game.id,
                                                    version = version,
                                                    launchOptions = selectedLaunchOptions,
                                                    runningProcessStore = runningProcessStore,
                                                )
                                                context.refreshUi()
                                            }
                                        },
                                    ),
                                )
                            }
                        }
                        add(
                            PageActionRegistration(
                                id = "sts2_windows_launch_options",
                                label = PageTextDirect(localized(language, "windows.entrypoint.0012")),
                                compactLabel = PageTextDirect("\u2699"),
                                style = PageActionStyle.OUTLINED,
                                enabled = selectedVersion != null,
                                onClick = { context.openPage(WINDOWS_LAUNCH_OPTIONS_PAGE_ID) },
                            ),
                        )
                    },
                    tone = if (canLaunch && !hasRunningProcesses && !currentLaunchConflict) PageWidgetTone.ACCENT else PageWidgetTone.DEFAULT,
                ),
            ),
            )
        },
    )
}

private fun windowsSteamVerificationPassed(
    gameFileState: WindowsGameFilePanelState,
): Boolean {
    return gameFileState.mode == Sts2GameFileCheckMode.STEAM &&
        gameFileState.status == Sts2GameFileCheckStatus.COMPLETED &&
        gameFileState.passed
}

private fun buildWindowsLaunchOptionsContribution(
    context: PageContext,
    game: GameInstance,
    selectedVersion: Sts2VersionDefinition?,
    selectedLaunchOptions: WindowsResolvedLaunchOptions?,
    launchOptionsState: WindowsLaunchOptionsState?,
    launchSettingsDraft: Sts2LaunchSettingsDraft?,
    versionStore: Sts2VersionStore,
    detectedSteamSaveDirectories: List<String>,
    launchOptionsStore: PersistentWindowsLaunchOptionsStateStore,
): PageContributionBundle {
    val language = context.strings.language
    return PageContributionBundle(
        sourceId = STS2_TEMPLATE_ID,
        page = PageRegistration(
            id = WINDOWS_LAUNCH_OPTIONS_PAGE_ID,
            sourceId = STS2_TEMPLATE_ID,
            title = PageTextDirect(localized(language, "windows.entrypoint.0013")),
            subtitle = PageTextDirect(
                localized(language, "windows.entrypoint.0014"),
            ),
            actionLabel = PageTextDirect(context.strings.commonBack),
            action = { context.goBack() },
            supportedTargets = setOf(PlatformTarget.WINDOWS),
        ),
        nodes = buildList {
            add(
                PageSectionRegistration(
                    nodeId = "sts2_windows_launch_options_overview",
                    pageId = WINDOWS_LAUNCH_OPTIONS_PAGE_ID,
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    title = PageTextDirect(localized(language, "windows.entrypoint.0015")),
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_windows_launch_options_current_version",
                    pageId = WINDOWS_LAUNCH_OPTIONS_PAGE_ID,
                    parentNodeId = "sts2_windows_launch_options_overview",
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    widget = PageWidgetDetailCard(
                        title = PageTextDirect(
                            selectedVersion?.let { version -> versionDisplayName(language, version) }
                                ?: localized(language, "windows.entrypoint.0016"),
                        ),
                        subtitle = PageTextDirect(
                            selectedVersion?.let {
                                localized(language, "windows.entrypoint.0017", listOf(game.displayName))
                            } ?: localized(language, "windows.entrypoint.0018"),
                        ),
                        rows = selectedVersion?.let { version ->
                            selectedLaunchOptions?.let { launchOptions ->
                                versionSummaryRows(language, version, selected = true, launchOptions = launchOptions)
                            }
                        }.orEmpty(),
                        actions = listOf(
                            PageActionRegistration(
                                id = "sts2_windows_launch_options_open_versions",
                                label = PageTextDirect(localized(language, "windows.entrypoint.0019")),
                                style = PageActionStyle.OUTLINED,
                                onClick = { context.openPage(WINDOWS_VERSION_MANAGER_PAGE_ID) },
                            ),
                        ),
                        tone = if (selectedVersion != null) PageWidgetTone.ACCENT else PageWidgetTone.DANGER,
                    ),
                ),
            )
            add(
                PageSectionRegistration(
                    nodeId = "sts2_windows_launch_options_settings",
                    pageId = WINDOWS_LAUNCH_OPTIONS_PAGE_ID,
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    orderHint = 10,
                    title = PageTextDirect(localized(language, "windows.entrypoint.0020")),
                    subtitle = PageTextDirect(
                        localized(language, "windows.entrypoint.0021"),
                    ),
                ),
            )
            if (selectedVersion == null || launchOptionsState == null || selectedLaunchOptions == null) {
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_windows_launch_options_missing_version",
                        pageId = WINDOWS_LAUNCH_OPTIONS_PAGE_ID,
                        parentNodeId = "sts2_windows_launch_options_settings",
                        sourceId = STS2_TEMPLATE_ID,
                        supportedTargets = setOf(PlatformTarget.WINDOWS),
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(localized(language, "windows.entrypoint.0022")),
                            subtitle = PageTextDirect(
                                localized(language, "windows.entrypoint.0023"),
                            ),
                            tone = PageWidgetTone.DANGER,
                        ),
                    ),
                )
            } else {
                fun updateLaunchRenderer(renderer: String) {
                    versionStore.updateLaunchSettingsDraft(game.id, selectedVersion.clientId) { current ->
                        current.copy(renderer = renderer)
                    }
                    versionStore.saveLaunchSettingsDraft(game.id, selectedVersion.clientId)
                    context.refreshUi()
                }
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_windows_launch_options_mode",
                        pageId = WINDOWS_LAUNCH_OPTIONS_PAGE_ID,
                        parentNodeId = "sts2_windows_launch_options_settings",
                        sourceId = STS2_TEMPLATE_ID,
                        supportedTargets = setOf(PlatformTarget.WINDOWS),
                        orderHint = 0,
                        widget = PageWidgetChoiceCard(
                            title = PageTextDirect(localized(language, "windows.entrypoint.0024")),
                            subtitle = PageTextDirect(
                                localized(language, "windows.entrypoint.0025"),
                            ),
                            options = listOf(
                                PageChoiceOptionRegistration(
                                    id = "sts2_windows_launch_mode_local",
                                    label = PageTextDirect(localized(language, "windows.entrypoint.0026")),
                                    selected = launchOptionsState.launchMode == WindowsLaunchMode.DEFAULT,
                                    onClick = {
                                        launchOptionsStore.updateLaunchMode(
                                            instanceId = game.id,
                                            versionId = selectedVersion.clientId,
                                            launchMode = WindowsLaunchMode.DEFAULT,
                                        )
                                        context.refreshUi()
                                    },
                                ),
                                PageChoiceOptionRegistration(
                                    id = "sts2_windows_launch_mode_steam",
                                    label = PageTextDirect(localized(language, "windows.entrypoint.0027")),
                                    selected = launchOptionsState.launchMode == WindowsLaunchMode.STEAM,
                                    onClick = {
                                        launchOptionsStore.updateLaunchMode(
                                            instanceId = game.id,
                                            versionId = selectedVersion.clientId,
                                            launchMode = WindowsLaunchMode.STEAM,
                                        )
                                        context.refreshUi()
                                    },
                                ),
                            ),
                        ),
                    ),
                )
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_windows_launch_options_renderer",
                        pageId = WINDOWS_LAUNCH_OPTIONS_PAGE_ID,
                        parentNodeId = "sts2_windows_launch_options_settings",
                        sourceId = STS2_TEMPLATE_ID,
                        supportedTargets = setOf(PlatformTarget.WINDOWS),
                        orderHint = 1,
                        widget = PageWidgetChoiceCard(
                            title = PageTextDirect(localized(language, "windows.entrypoint.0028")),
                            subtitle = PageTextDirect(
                                localized(language, "windows.entrypoint.0029"),
                            ),
                            options = listOf(
                                windowsRendererOption("vulkan", launchSettingsDraft?.renderer.orEmpty(), ::updateLaunchRenderer),
                                windowsRendererOption("opengl", launchSettingsDraft?.renderer.orEmpty(), ::updateLaunchRenderer),
                                windowsRendererOption("d3d12", launchSettingsDraft?.renderer.orEmpty(), ::updateLaunchRenderer),
                            ),
                        ),
                    ),
                )
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_windows_launch_options_fastmp_toggle",
                        pageId = WINDOWS_LAUNCH_OPTIONS_PAGE_ID,
                        parentNodeId = "sts2_windows_launch_options_settings",
                        sourceId = STS2_TEMPLATE_ID,
                        supportedTargets = setOf(PlatformTarget.WINDOWS),
                        orderHint = 2,
                        widget = PageWidgetToggleCard(
                            title = PageTextDirect(localized(language, "windows.entrypoint.0030")),
                            subtitle = PageTextDirect(
                                localized(language, "windows.entrypoint.0031"),
                            ),
                            checked = launchOptionsState.fastMpEnabled,
                            onCheckedChange = { enabled ->
                                launchOptionsStore.updateFastMpEnabled(
                                    instanceId = game.id,
                                    versionId = selectedVersion.clientId,
                                    fastMpEnabled = enabled,
                                )
                                context.refreshUi()
                            },
                        ),
                    ),
                )
                if (launchOptionsState.fastMpEnabled) {
                    add(
                        PageWidgetRegistration(
                            nodeId = "sts2_windows_launch_options_fastmp_role",
                            pageId = WINDOWS_LAUNCH_OPTIONS_PAGE_ID,
                            parentNodeId = "sts2_windows_launch_options_settings",
                            sourceId = STS2_TEMPLATE_ID,
                            supportedTargets = setOf(PlatformTarget.WINDOWS),
                            orderHint = 3,
                            widget = PageWidgetChoiceCard(
                                title = PageTextDirect(localized(language, "windows.entrypoint.0032")),
                                subtitle = PageTextDirect(
                                    localized(language, "windows.entrypoint.0033"),
                                ),
                                options = listOf(
                                    PageChoiceOptionRegistration(
                                        id = "sts2_windows_fastmp_role_client",
                                        label = PageTextDirect(localized(language, "windows.entrypoint.0034")),
                                        selected = launchOptionsState.fastMpRole == WindowsFastMpRole.CLIENT,
                                        onClick = {
                                            launchOptionsStore.updateFastMpRole(
                                                instanceId = game.id,
                                                versionId = selectedVersion.clientId,
                                                fastMpRole = WindowsFastMpRole.CLIENT,
                                            )
                                            context.refreshUi()
                                        },
                                    ),
                                    PageChoiceOptionRegistration(
                                        id = "sts2_windows_fastmp_role_host",
                                        label = PageTextDirect(localized(language, "windows.entrypoint.0035")),
                                        selected = launchOptionsState.fastMpRole == WindowsFastMpRole.HOST,
                                        onClick = {
                                            launchOptionsStore.updateFastMpRole(
                                                instanceId = game.id,
                                                versionId = selectedVersion.clientId,
                                                fastMpRole = WindowsFastMpRole.HOST,
                                            )
                                            context.refreshUi()
                                        },
                                    ),
                                ),
                            ),
                        ),
                    )
                }
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_windows_launch_options_console_log",
                        pageId = WINDOWS_LAUNCH_OPTIONS_PAGE_ID,
                        parentNodeId = "sts2_windows_launch_options_settings",
                        sourceId = STS2_TEMPLATE_ID,
                        supportedTargets = setOf(PlatformTarget.WINDOWS),
                        orderHint = 4,
                        widget = PageWidgetToggleCard(
                            title = PageTextDirect(localized(language, "windows.entrypoint.0036")),
                            subtitle = PageTextDirect(
                                localized(language, "windows.entrypoint.0037"),
                            ),
                            checked = launchOptionsState.consoleLogEnabled,
                            onCheckedChange = { enabled ->
                                launchOptionsStore.updateConsoleLogEnabled(
                                    instanceId = game.id,
                                    versionId = selectedVersion.clientId,
                                    consoleLogEnabled = enabled,
                                )
                                context.refreshUi()
                            },
                        ),
                    ),
                )
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_windows_launch_options_client_id",
                        pageId = WINDOWS_LAUNCH_OPTIONS_PAGE_ID,
                        parentNodeId = "sts2_windows_launch_options_settings",
                        sourceId = STS2_TEMPLATE_ID,
                        supportedTargets = setOf(PlatformTarget.WINDOWS),
                        orderHint = 5,
                        widget = if (launchOptionsState.launchMode == WindowsLaunchMode.DEFAULT) {
                            PageWidgetTextInputCard(
                                title = PageTextDirect(localized(language, "windows.entrypoint.0038")),
                                value = PageTextDirect(launchOptionsState.clientIdText),
                                placeholder = PageTextDirect("1"),
                                supportingText = PageTextDirect(
                                    windowsLaunchClientIdSupportingText(
                                        language = language,
                                        state = launchOptionsState,
                                    ),
                                ),
                                onValueChange = { value ->
                                    launchOptionsStore.updateClientIdText(
                                        instanceId = game.id,
                                        versionId = selectedVersion.clientId,
                                        clientIdText = value,
                                    )
                                    context.refreshUi()
                                },
                            )
                        } else {
                            PageWidgetChoiceCard(
                                title = PageTextDirect(localized(language, "windows.entrypoint.0039")),
                                subtitle = PageTextDirect(
                                    if (detectedSteamSaveDirectories.isEmpty()) {
                                        localized(language, "windows.entrypoint.0040")
                                    } else {
                                        localized(language, "windows.entrypoint.0041")
                                    }
                                ),
                                options = detectedSteamSaveDirectories.map { directoryName ->
                                    PageChoiceOptionRegistration(
                                        id = "sts2_windows_launch_steam_dir_$directoryName",
                                        label = PageTextDirect(directoryName),
                                        selected = launchOptionsState.steamSaveDirectoryName == directoryName,
                                        onClick = {
                                            launchOptionsStore.updateSteamSaveDirectoryName(
                                                instanceId = game.id,
                                                versionId = selectedVersion.clientId,
                                                steamSaveDirectoryName = directoryName,
                                            )
                                            context.refreshUi()
                                        },
                                    )
                                },
                            )
                        },
                    ),
                )
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_windows_launch_options_preview",
                        pageId = WINDOWS_LAUNCH_OPTIONS_PAGE_ID,
                        parentNodeId = "sts2_windows_launch_options_settings",
                        sourceId = STS2_TEMPLATE_ID,
                        supportedTargets = setOf(PlatformTarget.WINDOWS),
                        orderHint = 6,
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(localized(language, "windows.entrypoint.0042")),
                            subtitle = PageTextDirect(
                                localized(language, "windows.entrypoint.0043"),
                            ),
                            rows = windowsLaunchOptionsRows(language, selectedVersion, selectedLaunchOptions),
                            tone = PageWidgetTone.ACCENT,
                        ),
                    ),
                )
            }
        },
    )
}

private fun buildWindowsModManagerContribution(
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
    return PageContributionBundle(
        sourceId = STS2_TEMPLATE_ID,
        page = PageRegistration(
            id = WINDOWS_MOD_MANAGER_PAGE_ID,
            sourceId = STS2_TEMPLATE_ID,
            title = PageTextDirect(localized(language, "windows.entrypoint.0044")),
            subtitle = PageTextDirect(
                localized(language, "windows.entrypoint.0045"),
            ),
            actionLabel = PageTextDirect(context.strings.commonBack),
            action = { context.goBack() },
            supportedTargets = setOf(PlatformTarget.WINDOWS),
        ),
        nodes = buildList {
            add(
                PageSectionRegistration(
                    nodeId = "sts2_windows_mods_overview",
                    pageId = WINDOWS_MOD_MANAGER_PAGE_ID,
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    title = PageTextDirect(localized(language, "windows.entrypoint.0046")),
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_windows_mods_card",
                    pageId = WINDOWS_MOD_MANAGER_PAGE_ID,
                    parentNodeId = "sts2_windows_mods_overview",
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    widget = PageWidgetDetailCard(
                        title = PageTextDirect(localized(language, "windows.entrypoint.0047")),
                        subtitle = PageTextDirect(windowsModManagerSubtitle(language, selectedVersion, modScanResult)),
                        rows = windowsModManagerRows(language, selectedVersion, modScanResult),
                        actions = buildList {
                            add(
                                PageActionRegistration(
                                    id = "sts2_windows_mods_refresh",
                                    label = PageTextDirect(localized(language, "windows.entrypoint.0048")),
                                    style = PageActionStyle.OUTLINED,
                                    onClick = context::refreshPage,
                                ),
                            )
                            onImportModPackage?.let { importAction ->
                                add(
                                    PageActionRegistration(
                                        id = "sts2_windows_mods_import_package",
                                        label = PageTextDirect(localized(language, "windows.entrypoint.0049")),
                                        style = PageActionStyle.FILLED_TONAL,
                                        onClick = importAction,
                                    ),
                                )
                            }
                        },
                        tone = when {
                            selectedVersion == null || modScanResult == null || !modScanResult.modDirectoryExists -> PageWidgetTone.DANGER
                            modScanResult.totalProblemEntryCount > 0 -> PageWidgetTone.DANGER
                            else -> PageWidgetTone.ACCENT
                        },
                    ),
                ),
            )
            modImportStatus?.let { status ->
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_windows_mod_import_status",
                        pageId = WINDOWS_MOD_MANAGER_PAGE_ID,
                        parentNodeId = "sts2_windows_mods_overview",
                        sourceId = STS2_TEMPLATE_ID,
                        supportedTargets = setOf(PlatformTarget.WINDOWS),
                        orderHint = 1,
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(localized(language, "windows.entrypoint.0050")),
                            subtitle = PageTextDirect(status.detail),
                            rows = modImportStatusRows(language, status),
                            tone = modImportStatusTone(status),
                        ),
                    ),
                )
            }
            pendingReplacement?.takeIf { replacement ->
                replacement.kind == Sts2ModPendingReplacementKind.MOD_PACKAGE
            }?.let { replacement ->
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_windows_mod_pending_replacement",
                        pageId = WINDOWS_MOD_MANAGER_PAGE_ID,
                        parentNodeId = "sts2_windows_mods_overview",
                        sourceId = STS2_TEMPLATE_ID,
                        supportedTargets = setOf(PlatformTarget.WINDOWS),
                        orderHint = 2,
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(modPendingReplacementTitle(language, replacement)),
                            subtitle = PageTextDirect(replacement.detail),
                            rows = modPendingReplacementRows(language, replacement),
                            actions = listOf(
                                PageActionRegistration(
                                    id = "sts2_windows_mod_pending_confirm",
                                    label = PageTextDirect(modPendingReplacementConfirmLabel(language, replacement)),
                                    style = PageActionStyle.FILLED_TONAL,
                                    onClick = onConfirmPendingReplacement,
                                ),
                                PageActionRegistration(
                                    id = "sts2_windows_mod_pending_cancel",
                                    label = PageTextDirect(localized(language, "windows.entrypoint.0051")),
                                    style = PageActionStyle.OUTLINED,
                                    onClick = onCancelPendingReplacement,
                                ),
                            ),
                            tone = PageWidgetTone.DEFAULT,
                        ),
                    ),
                )
            }
            add(
                PageSectionRegistration(
                    nodeId = "sts2_windows_mods_list",
                    pageId = WINDOWS_MOD_MANAGER_PAGE_ID,
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    orderHint = 10,
                    title = PageTextDirect(localized(language, "windows.entrypoint.0052")),
                ),
            )
            when {
                selectedVersion == null -> add(
                    PageWidgetRegistration(
                        nodeId = "sts2_windows_mods_missing_version",
                        pageId = WINDOWS_MOD_MANAGER_PAGE_ID,
                        parentNodeId = "sts2_windows_mods_list",
                        sourceId = STS2_TEMPLATE_ID,
                        supportedTargets = setOf(PlatformTarget.WINDOWS),
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(localized(language, "windows.entrypoint.0053")),
                            subtitle = PageTextDirect(
                                localized(language, "windows.entrypoint.0054"),
                            ),
                            tone = PageWidgetTone.DANGER,
                        ),
                    ),
                )

                modScanResult == null || !modScanResult.modDirectoryConfigured -> add(
                    PageWidgetRegistration(
                        nodeId = "sts2_windows_mods_missing_directory",
                        pageId = WINDOWS_MOD_MANAGER_PAGE_ID,
                        parentNodeId = "sts2_windows_mods_list",
                        sourceId = STS2_TEMPLATE_ID,
                        supportedTargets = setOf(PlatformTarget.WINDOWS),
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(localized(language, "windows.entrypoint.0055")),
                            subtitle = PageTextDirect(
                                localized(language, "windows.entrypoint.0056"),
                            ),
                            tone = PageWidgetTone.DANGER,
                        ),
                    ),
                )

                !modScanResult.modDirectoryExists -> add(
                    PageWidgetRegistration(
                        nodeId = "sts2_windows_mods_directory_missing",
                        pageId = WINDOWS_MOD_MANAGER_PAGE_ID,
                        parentNodeId = "sts2_windows_mods_list",
                        sourceId = STS2_TEMPLATE_ID,
                        supportedTargets = setOf(PlatformTarget.WINDOWS),
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(localized(language, "windows.entrypoint.0057")),
                            subtitle = PageTextDirect(
                                localized(language, "windows.entrypoint.0058"),
                            ),
                            rows = listOf(row(language, "windows.entrypoint.row.0005", modScanResult.modDirectoryPath)),
                            tone = PageWidgetTone.DANGER,
                        ),
                    ),
                )

                modScanResult.mods.isEmpty() && modScanResult.problems.isEmpty() -> add(
                    PageWidgetRegistration(
                        nodeId = "sts2_windows_mods_empty",
                        pageId = WINDOWS_MOD_MANAGER_PAGE_ID,
                        parentNodeId = "sts2_windows_mods_list",
                        sourceId = STS2_TEMPLATE_ID,
                        supportedTargets = setOf(PlatformTarget.WINDOWS),
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(localized(language, "windows.entrypoint.0059")),
                            subtitle = PageTextDirect(
                                localized(language, "windows.entrypoint.0060"),
                            ),
                        ),
                    ),
                )

                else -> {
                    modScanResult.problems.forEachIndexed { index, problem ->
                        add(
                            PageWidgetRegistration(
                                nodeId = "sts2_windows_mod_problem_$index",
                                pageId = WINDOWS_MOD_MANAGER_PAGE_ID,
                                parentNodeId = "sts2_windows_mods_list",
                                sourceId = STS2_TEMPLATE_ID,
                                supportedTargets = setOf(PlatformTarget.WINDOWS),
                                orderHint = index,
                                widget = PageWidgetDetailCard(
                                    title = PageTextDirect(localized(language, "windows.entrypoint.0061")),
                                    subtitle = PageTextDirect(problem.reason),
                                    rows = listOf(row(language, "windows.entrypoint.row.0006", problem.relativeManifestPath)),
                                    tone = PageWidgetTone.DANGER,
                                ),
                            ),
                        )
                    }
                    modScanResult.mods.forEachIndexed { index, mod ->
                        val enabledState = resolveSts2ModEnabledState(modSettingsSnapshot, mod.manifest.id)
                        add(
                            PageWidgetRegistration(
                                nodeId = "sts2_windows_mod_${mod.discoveryOrder}",
                                pageId = WINDOWS_MOD_MANAGER_PAGE_ID,
                                parentNodeId = "sts2_windows_mods_list",
                                sourceId = STS2_TEMPLATE_ID,
                                supportedTargets = setOf(PlatformTarget.WINDOWS),
                                orderHint = 100 + index,
                                widget = PageWidgetDetailCard(
                                    title = PageTextDirect(windowsModDisplayName(mod)),
                                    subtitle = PageTextDirect(windowsModSubtitle(language, mod, enabledState)),
                                    rows = listOf(
                                        row(language, "windows.entrypoint.row.0007", mod.manifest.id),
                                        row(language, "windows.entrypoint.row.0008", mod.relativeModPath),
                                    ),
                                    tone = if (mod.issues.isEmpty()) PageWidgetTone.ACCENT else PageWidgetTone.DANGER,
                                    onClick = { context.openPage(windowsModDetailPageId(mod)) },
                                ),
                            ),
                        )
                    }
                }
            }
        },
    )
}

private fun buildWindowsRunningProcessContribution(
    context: PageContext,
    versions: List<Sts2VersionDefinition>,
    runningProcesses: List<WindowsRunningProcessRecord>,
    runningProcessStore: InMemoryWindowsRunningProcessStateStore,
): PageContributionBundle {
    val language = context.strings.language
    val gamesById = context.allGames.associateBy { instance -> instance.id }
    val currentInstanceId = context.currentGame?.id
    val versionsById = versions.associateBy { version -> version.clientId }
    return PageContributionBundle(
        sourceId = STS2_TEMPLATE_ID,
        page = PageRegistration(
            id = WINDOWS_RUNNING_PROCESS_PAGE_ID,
            sourceId = STS2_TEMPLATE_ID,
            title = PageTextDirect(localized(language, "windows.entrypoint.0062")),
            subtitle = PageTextDirect(
                localized(language, "windows.entrypoint.0063"),
            ),
            actionLabel = PageTextDirect(context.strings.commonBack),
            action = { context.goBack() },
            supportedTargets = setOf(PlatformTarget.WINDOWS),
        ),
        nodes = buildList {
            if (runningProcesses.isNotEmpty()) {
                add(
                    windowsRunningAutoRefreshRegistration(
                        nodeId = "sts2_windows_running_process_page_auto_refresh",
                        pageId = WINDOWS_RUNNING_PROCESS_PAGE_ID,
                        sourceId = STS2_TEMPLATE_ID,
                        runningProcessStore = runningProcessStore,
                        context = context,
                    ),
                )
            }
            add(
                PageSectionRegistration(
                    nodeId = "sts2_windows_running_process_list",
                    pageId = WINDOWS_RUNNING_PROCESS_PAGE_ID,
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    title = PageTextDirect(localized(language, "windows.entrypoint.0064")),
                ),
            )
            when {
                runningProcesses.isEmpty() -> add(
                    PageWidgetRegistration(
                        nodeId = "sts2_windows_running_process_empty",
                        pageId = WINDOWS_RUNNING_PROCESS_PAGE_ID,
                        parentNodeId = "sts2_windows_running_process_list",
                        sourceId = STS2_TEMPLATE_ID,
                        supportedTargets = setOf(PlatformTarget.WINDOWS),
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(localized(language, "windows.entrypoint.0065")),
                            subtitle = PageTextDirect(
                                localized(language, "windows.entrypoint.0066"),
                            ),
                        ),
                    ),
                )

                else -> {
                    runningProcesses.forEachIndexed { index, record ->
                        add(
                            PageWidgetRegistration(
                                nodeId = "sts2_windows_running_process_$index",
                                pageId = WINDOWS_RUNNING_PROCESS_PAGE_ID,
                                parentNodeId = "sts2_windows_running_process_list",
                                sourceId = STS2_TEMPLATE_ID,
                                supportedTargets = setOf(PlatformTarget.WINDOWS),
                                    orderHint = index,
                                    widget = PageWidgetDetailCard(
                                        title = PageTextDirect(windowsRunningProcessTitle(language, record)),
                                        subtitle = PageTextDirect(
                                            windowsRunningProcessSubtitle(
                                                language = language,
                                                instance = gamesById[record.instanceId],
                                                version = if (record.instanceId == currentInstanceId) versionsById[record.versionId] else null,
                                                record = record,
                                            ),
                                        ),
                                        rows = windowsRunningProcessRows(
                                            language = language,
                                            instance = gamesById[record.instanceId],
                                            version = if (record.instanceId == currentInstanceId) versionsById[record.versionId] else null,
                                            record = record,
                                        ),
                                        actions = listOf(
                                        PageActionRegistration(
                                            id = "sts2_windows_running_process_kill_${record.pid}",
                                            label = PageTextDirect(localized(language, "windows.entrypoint.0067")),
                                            style = PageActionStyle.OUTLINED,
                                            onClick = {
                                            forceCloseWindowsRunningRecord(record, runningProcessStore)
                                                context.refreshUi()
                                            },
                                        ),
                                    ),
                                    tone = PageWidgetTone.ACCENT,
                                ),
                            ),
                        )
                    }
                }
            }
        },
    )
}

private fun buildWindowsVersionManagerContribution(
    context: PageContext,
    game: GameInstance,
    versions: List<Sts2VersionDefinition>,
    selectedClientId: Int?,
    versionStore: Sts2VersionStore,
    detectedSteamGameDirectory: String?,
): PageContributionBundle {
    val language = context.strings.language
    return PageContributionBundle(
        sourceId = STS2_TEMPLATE_ID,
        page = PageRegistration(
            id = WINDOWS_VERSION_MANAGER_PAGE_ID,
            sourceId = STS2_TEMPLATE_ID,
            title = PageTextDirect(localized(language, "windows.entrypoint.0068")),
            subtitle = PageTextDirect(
                localized(language, "windows.entrypoint.0069"),
            ),
            actionLabel = PageTextDirect(context.strings.commonBack),
            action = { context.goBack() },
            supportedTargets = setOf(PlatformTarget.WINDOWS),
        ),
        nodes = buildList {
            add(
                PageSectionRegistration(
                    nodeId = "sts2_windows_versions_tools",
                    pageId = WINDOWS_VERSION_MANAGER_PAGE_ID,
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    orderHint = -1,
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_windows_versions_create",
                    pageId = WINDOWS_VERSION_MANAGER_PAGE_ID,
                    parentNodeId = "sts2_windows_versions_tools",
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    widget = PageWidgetDetailCard(
                        title = PageTextDirect(localized(language, "windows.entrypoint.0070")),
                        subtitle = PageTextDirect(
                            when {
                                detectedSteamGameDirectory.isNullOrBlank() -> localized(language, "windows.entrypoint.0071", listOf(game.displayName))

                                else -> localized(language, "windows.entrypoint.0072")
                            },
                        ),
                        actions = listOf(
                            PageActionRegistration(
                                id = "sts2_windows_versions_start_create",
                                label = PageTextDirect(localized(language, "windows.entrypoint.0073")),
                                style = PageActionStyle.FILLED_TONAL,
                                onClick = { context.openPage(WINDOWS_VERSION_CREATE_PAGE_ID) },
                            ),
                        ),
                    ),
                ),
            )
            add(
                PageSectionRegistration(
                    nodeId = "sts2_windows_versions_list",
                    pageId = WINDOWS_VERSION_MANAGER_PAGE_ID,
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    orderHint = 0,
                    title = PageTextDirect(localized(language, "windows.entrypoint.0074")),
                    subtitle = PageTextDirect(
                        localized(language, "windows.entrypoint.0075"),
                    ),
                ),
            )
            if (versions.isEmpty()) {
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_windows_versions_empty",
                        pageId = WINDOWS_VERSION_MANAGER_PAGE_ID,
                        parentNodeId = "sts2_windows_versions_list",
                        sourceId = STS2_TEMPLATE_ID,
                        supportedTargets = setOf(PlatformTarget.WINDOWS),
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(localized(language, "windows.entrypoint.0076")),
                            subtitle = PageTextDirect(
                                localized(language, "windows.entrypoint.0077"),
                            ),
                        ),
                    ),
                )
            } else {
                versions.forEachIndexed { index, version ->
                    val selected = version.clientId == selectedClientId
                    add(
                        PageWidgetRegistration(
                            nodeId = "sts2_windows_version_${version.clientId}",
                            pageId = WINDOWS_VERSION_MANAGER_PAGE_ID,
                            parentNodeId = "sts2_windows_versions_list",
                            sourceId = STS2_TEMPLATE_ID,
                            supportedTargets = setOf(PlatformTarget.WINDOWS),
                            orderHint = index,
                            widget = PageWidgetDetailCard(
                                title = PageTextDirect(versionDisplayName(language, version)),
                                subtitle = PageTextDirect(
                                    if (selected) {
                                        localized(language, "windows.entrypoint.0078")
                                    } else {
                                        localized(language, "windows.entrypoint.0079")
                                    },
                                ),
                                rows = versionSummaryRows(language, version, selected),
                                actions = listOf(
                                    PageActionRegistration(
                                        id = "sts2_windows_open_detail_${version.clientId}",
                                        label = PageTextDirect(localized(language, "windows.entrypoint.0080")),
                                        compactLabel = PageTextDirect("i"),
                                        style = PageActionStyle.OUTLINED,
                                        onClick = { context.openPage(windowsVersionDetailPageId(version.clientId)) },
                                    ),
                                ),
                                tone = if (selected) PageWidgetTone.ACCENT else PageWidgetTone.DEFAULT,
                                onClick = {
                                    if (!selected && versionStore.selectVersion(game.id, version.clientId)) {
                                        context.refreshUi()
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

private fun buildWindowsCreateVersionContribution(
    context: PageContext,
    game: GameInstance,
    draft: Sts2VersionDraft,
    validationError: Sts2VersionValidationError?,
    suggestedClientId: Int,
    versionStore: Sts2VersionStore,
    detectedSteamGameDirectory: String?,
): PageContributionBundle {
    val language = context.strings.language
    val canSave = validationError == null && draft.gameDirectory.trim().isNotBlank()
    return PageContributionBundle(
        sourceId = STS2_TEMPLATE_ID,
        page = PageRegistration(
            id = WINDOWS_VERSION_CREATE_PAGE_ID,
            sourceId = STS2_TEMPLATE_ID,
            title = PageTextDirect(localized(language, "windows.entrypoint.0081")),
            subtitle = PageTextDirect(
                if (detectedSteamGameDirectory.isNullOrBlank()) {
                    localized(language, "windows.entrypoint.0082")
                } else {
                    localized(language, "windows.entrypoint.0083")
                },
            ),
            actionLabel = PageTextDirect(context.strings.commonBack),
            action = { context.goBack() },
            supportedTargets = setOf(PlatformTarget.WINDOWS),
        ),
        nodes = buildList {
            add(
                PageSectionRegistration(
                    nodeId = "sts2_windows_version_create_editor",
                    pageId = WINDOWS_VERSION_CREATE_PAGE_ID,
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    title = PageTextDirect(localized(language, "windows.entrypoint.0084")),
                    subtitle = PageTextDirect(
                        localized(language, "windows.entrypoint.0085", listOf(game.displayName)),
                    ),
                ),
            )
            addAll(
                windowsVersionEditorWidgets(
                    pageId = WINDOWS_VERSION_CREATE_PAGE_ID,
                    parentNodeId = "sts2_windows_version_create_editor",
                    language = language,
                    draft = draft,
                    validationError = validationError,
                    suggestedVersionIdPlaceholder = suggestedClientId.toString(),
                    directoryPicker = context.directoryPickerState,
                    detectedSteamGameDirectory = detectedSteamGameDirectory,
                    onClientIdChange = { value ->
                        versionStore.updateCreateDraft(game.id) { current ->
                            current.copy(clientIdText = value)
                                .withWindowsDerivedPaths(
                                    userHomeDirectory = windowsUserHomeDirectory(),
                                    detectedSteamGameDirectory = detectedSteamGameDirectory,
                                )
                        }
                    },
                    onVersionNameChange = { value ->
                        versionStore.updateCreateDraft(game.id) { current -> current.copy(versionName = value) }
                    },
                    onGameDirectoryChange = { value ->
                        versionStore.updateCreateDraft(game.id) { current ->
                            current.copy(gameDirectory = value)
                                .withWindowsDerivedPaths(
                                    userHomeDirectory = windowsUserHomeDirectory(),
                                    detectedSteamGameDirectory = detectedSteamGameDirectory,
                                )
                        }
                    },
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_windows_version_create_actions",
                    pageId = WINDOWS_VERSION_CREATE_PAGE_ID,
                    parentNodeId = "sts2_windows_version_create_editor",
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    orderHint = 20,
                    widget = PageWidgetButtonStack(
                        actions = listOf(
                            PageActionRegistration(
                                id = "sts2_windows_version_create_save",
                                label = PageTextDirect(localized(language, "windows.entrypoint.0086")),
                                style = PageActionStyle.FILLED_TONAL,
                                enabled = canSave,
                                onClick = {
                                    versionStore.updateCreateDraft(game.id) { current ->
                                        current.withWindowsDerivedPaths(
                                            userHomeDirectory = windowsUserHomeDirectory(),
                                            detectedSteamGameDirectory = detectedSteamGameDirectory,
                                        )
                                    }
                                    if (versionStore.saveCreateDraft(game.id)) {
                                        context.refreshUi()
                                        context.replaceCurrentPage(WINDOWS_VERSION_MANAGER_PAGE_ID)
                                    } else {
                                        context.refreshPage()
                                    }
                                },
                            ),
                            PageActionRegistration(
                                id = "sts2_windows_version_create_back",
                                label = PageTextDirect(localized(language, "windows.entrypoint.0087")),
                                style = PageActionStyle.OUTLINED,
                                onClick = { context.replaceCurrentPage(WINDOWS_VERSION_MANAGER_PAGE_ID) },
                            ),
                        ),
                    ),
                ),
            )
        },
    )
}

private fun buildWindowsVersionDetailContribution(
    context: PageContext,
    game: GameInstance,
    version: Sts2VersionDefinition,
    selected: Boolean,
    launchOptions: WindowsResolvedLaunchOptions,
    draft: Sts2VersionDraft,
    validationError: Sts2VersionValidationError?,
    versionStore: Sts2VersionStore,
    launchOptionsStore: PersistentWindowsLaunchOptionsStateStore,
    runningProcessStore: InMemoryWindowsRunningProcessStateStore,
    detectedSteamGameDirectory: String?,
): PageContributionBundle {
    val language = context.strings.language
    fun persistWindowsVersionDetailDraft() {
        if (versionStore.saveEditDraft(game.id, version.clientId)) {
            context.refreshUi()
        } else {
            context.refreshPage()
        }
    }
    return PageContributionBundle(
        sourceId = STS2_TEMPLATE_ID,
        page = PageRegistration(
            id = windowsVersionDetailPageId(version.clientId),
            sourceId = STS2_TEMPLATE_ID,
            title = PageTextDirect(versionDisplayName(language, version)),
            subtitle = PageTextDirect(
                if (selected) {
                    localized(language, "windows.entrypoint.0088")
                } else {
                    localized(language, "windows.entrypoint.0089")
                }
            ),
            actionLabel = PageTextDirect(context.strings.commonBack),
            action = { context.goBack() },
            supportedTargets = setOf(PlatformTarget.WINDOWS),
        ),
        nodes = buildList {
            add(
                PageSectionRegistration(
                    nodeId = "sts2_windows_version_detail_summary_${version.clientId}",
                    pageId = windowsVersionDetailPageId(version.clientId),
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    orderHint = -1,
                    title = PageTextDirect(localized(language, "windows.entrypoint.0090")),
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_windows_version_detail_info_${version.clientId}",
                    pageId = windowsVersionDetailPageId(version.clientId),
                    parentNodeId = "sts2_windows_version_detail_summary_${version.clientId}",
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    widget = PageWidgetDetailCard(
                        title = PageTextDirect(localized(language, "windows.entrypoint.0091")),
                        subtitle = PageTextDirect(
                            localized(language, "windows.entrypoint.0092", listOf(game.displayName)),
                        ),
                        rows = versionSummaryRows(language, version, selected, launchOptions),
                        tone = if (selected) PageWidgetTone.ACCENT else PageWidgetTone.DEFAULT,
                    ),
                ),
            )
            add(
                PageSectionRegistration(
                    nodeId = "sts2_windows_version_detail_editor_${version.clientId}",
                    pageId = windowsVersionDetailPageId(version.clientId),
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    title = PageTextDirect(localized(language, "windows.entrypoint.0093")),
                ),
            )
            addAll(
                windowsVersionEditorWidgets(
                    pageId = windowsVersionDetailPageId(version.clientId),
                    parentNodeId = "sts2_windows_version_detail_editor_${version.clientId}",
                    language = language,
                    draft = draft,
                    validationError = validationError,
                    suggestedVersionIdPlaceholder = version.versionId,
                    directoryPicker = context.directoryPickerState,
                    detectedSteamGameDirectory = detectedSteamGameDirectory,
                    onClientIdChange = { value ->
                        versionStore.updateEditDraft(game.id, version.clientId) { current ->
                            current.copy(clientIdText = value)
                                .withWindowsDerivedPaths(
                                    userHomeDirectory = windowsUserHomeDirectory(),
                                    detectedSteamGameDirectory = detectedSteamGameDirectory,
                                )
                        }
                        persistWindowsVersionDetailDraft()
                    },
                    onVersionNameChange = { value ->
                        versionStore.updateEditDraft(game.id, version.clientId) { current -> current.copy(versionName = value) }
                        persistWindowsVersionDetailDraft()
                    },
                    onGameDirectoryChange = { value ->
                        versionStore.updateEditDraft(game.id, version.clientId) { current ->
                            current.copy(gameDirectory = value)
                                .withWindowsDerivedPaths(
                                    userHomeDirectory = windowsUserHomeDirectory(),
                                    detectedSteamGameDirectory = detectedSteamGameDirectory,
                                )
                        }
                        persistWindowsVersionDetailDraft()
                    },
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_windows_version_detail_actions_${version.clientId}",
                    pageId = windowsVersionDetailPageId(version.clientId),
                    parentNodeId = "sts2_windows_version_detail_editor_${version.clientId}",
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    orderHint = 20,
                    widget = PageWidgetButtonStack(
                        actions = listOf(
                            PageActionRegistration(
                                id = "sts2_windows_version_delete_${version.clientId}",
                                label = PageTextDirect(localized(language, "windows.entrypoint.0094")),
                                style = PageActionStyle.OUTLINED,
                                onClick = {
                                    if (versionStore.deleteVersion(game.id, version.clientId)) {
                                        launchOptionsStore.deleteState(game.id, version.clientId)
                                        runningProcessStore.removeVersionRecords(game.id, version.clientId)
                                        context.refreshUi()
                                        context.replaceCurrentPage(WINDOWS_VERSION_MANAGER_PAGE_ID)
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

private fun windowsVersionEditorWidgets(
    pageId: String,
    parentNodeId: String,
    language: SupportedLanguage,
    draft: Sts2VersionDraft,
    validationError: Sts2VersionValidationError?,
    suggestedVersionIdPlaceholder: String,
    directoryPicker: com.dreamyloong.tlauncher.sdk.platform.DirectoryPickerState?,
    detectedSteamGameDirectory: String?,
    onClientIdChange: (String) -> Unit,
    onVersionNameChange: (String) -> Unit,
    onGameDirectoryChange: (String) -> Unit,
): List<PageWidgetRegistration> {
    val preview = draft.withWindowsDerivedPaths(
        userHomeDirectory = windowsUserHomeDirectory(),
        detectedSteamGameDirectory = detectedSteamGameDirectory,
    )
    return listOf(
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_client_id",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            supportedTargets = setOf(PlatformTarget.WINDOWS),
            orderHint = 0,
            widget = PageWidgetTextInputCard(
                title = PageTextDirect(localized(language, "windows.entrypoint.0095")),
                value = PageTextDirect(draft.clientIdText),
                placeholder = PageTextDirect(suggestedVersionIdPlaceholder),
                supportingText = PageTextDirect(
                    validationErrorText(language, validationError) ?: localized(language, "windows.entrypoint.0096"),
                ),
                onValueChange = onClientIdChange,
            ),
        ),
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_version_name",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            supportedTargets = setOf(PlatformTarget.WINDOWS),
            orderHint = 1,
            widget = PageWidgetTextInputCard(
                title = PageTextDirect(localized(language, "windows.entrypoint.0097")),
                value = PageTextDirect(draft.versionName),
                placeholder = PageTextDirect(localized(language, "windows.entrypoint.0098")),
                supportingText = PageTextDirect(
                    localized(language, "windows.entrypoint.0099"),
                ),
                onValueChange = onVersionNameChange,
            ),
        ),
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_game_directory",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            supportedTargets = setOf(PlatformTarget.WINDOWS),
            orderHint = 2,
            widget = PageWidgetDirectoryInputCard(
                title = PageTextDirect(localized(language, "windows.entrypoint.0100")),
                value = PageTextDirect(draft.gameDirectory),
                placeholder = PageTextDirect("D:\\Games\\SlayTheSpire2"),
                supportingText = PageTextDirect(
                    if (draft.gameDirectory.trim().isBlank() && !detectedSteamGameDirectory.isNullOrBlank()) {
                        localized(language, "windows.entrypoint.0101")
                    } else if (draft.gameDirectory.trim().isBlank()) {
                        localized(language, "windows.entrypoint.0102")
                    } else {
                        localized(language, "windows.entrypoint.0103")
                    }
                ),
                onValueChange = onGameDirectoryChange,
                pickButtonLabel = PageTextDirect(localized(language, "windows.entrypoint.0104")),
                onPickDirectory = directoryPicker?.let { picker ->
                    { currentValue, onPicked -> picker.pickDirectory(currentValue, onPicked) }
                },
            ),
        ),
        PageWidgetRegistration(
            nodeId = "${parentNodeId}_derived_paths",
            pageId = pageId,
            parentNodeId = parentNodeId,
            sourceId = STS2_TEMPLATE_ID,
            supportedTargets = setOf(PlatformTarget.WINDOWS),
            orderHint = 3,
            widget = PageWidgetDetailCard(
                title = PageTextDirect(localized(language, "windows.entrypoint.0105")),
                    subtitle = PageTextDirect(
                        localized(language, "windows.entrypoint.0106"),
                    ),
                rows = listOf(
                    row(language, "windows.entrypoint.row.0009", preview.saveDirectory.ifBlank {
                        localized(language, "windows.entrypoint.0107")
                    }),
                    row(language, "windows.entrypoint.row.0010", preview.modDirectory.ifBlank {
                        localized(language, "windows.entrypoint.0108")
                    }),
                ),
                tone = PageWidgetTone.DEFAULT,
            ),
        ),
    )
}

private class PersistentWindowsLaunchOptionsStateStore(
    private val stateStore: ExtensionStateStore,
) {
    fun state(
        instanceId: GameInstanceId,
        versionId: Int,
    ): WindowsLaunchOptionsState {
        val lines = stateStore.read(stateKey(instanceId, versionId))
            ?.lineSequence()
            ?.filter { line -> line.isNotBlank() }
            ?.toList()
            .orEmpty()
        val launchMode = lines.firstOrNull { line -> line.startsWith("mode\t") }
            ?.substringAfter('\t')
            ?.trim()
            ?.uppercase()
            ?.let { mode -> WindowsLaunchMode.entries.firstOrNull { it.name == mode } }
            ?: WindowsLaunchMode.DEFAULT
        val clientIdText = lines.firstOrNull { line -> line.startsWith("clientId\t") }
            ?.substringAfter('\t')
            ?.trim()
            .orEmpty()
        val steamSaveDirectoryName = lines.firstOrNull { line -> line.startsWith("steamDir\t") }
            ?.substringAfter('\t')
            ?.trim()
            .orEmpty()
        val fastMpEnabled = lines.firstOrNull { line -> line.startsWith("fastMp\t") }
            ?.substringAfter('\t')
            ?.trim()
            ?.equals("true", ignoreCase = true)
            ?: false
        val fastMpRole = lines.firstOrNull { line -> line.startsWith("fastMpRole\t") }
            ?.substringAfter('\t')
            ?.trim()
            ?.uppercase()
            ?.let { role -> WindowsFastMpRole.entries.firstOrNull { it.name == role } }
            ?: WindowsFastMpRole.CLIENT
        val consoleLogEnabled = lines.firstOrNull { line -> line.startsWith("consoleLog\t") }
            ?.substringAfter('\t')
            ?.trim()
            ?.equals("true", ignoreCase = true)
            ?: false
        return WindowsLaunchOptionsState(
            launchMode = launchMode,
            clientIdText = clientIdText,
            steamSaveDirectoryName = steamSaveDirectoryName,
            fastMpEnabled = fastMpEnabled,
            fastMpRole = fastMpRole,
            consoleLogEnabled = consoleLogEnabled,
        )
    }

    fun updateLaunchMode(
        instanceId: GameInstanceId,
        versionId: Int,
        launchMode: WindowsLaunchMode,
    ) {
        persist(
            instanceId = instanceId,
            versionId = versionId,
            state = state(instanceId, versionId).copy(launchMode = launchMode),
        )
    }

    fun updateClientIdText(
        instanceId: GameInstanceId,
        versionId: Int,
        clientIdText: String,
    ) {
        persist(
            instanceId = instanceId,
            versionId = versionId,
            state = state(instanceId, versionId).copy(clientIdText = clientIdText),
        )
    }

    fun updateSteamSaveDirectoryName(
        instanceId: GameInstanceId,
        versionId: Int,
        steamSaveDirectoryName: String,
    ) {
        persist(
            instanceId = instanceId,
            versionId = versionId,
            state = state(instanceId, versionId).copy(steamSaveDirectoryName = steamSaveDirectoryName),
        )
    }

    fun updateFastMpEnabled(
        instanceId: GameInstanceId,
        versionId: Int,
        fastMpEnabled: Boolean,
    ) {
        persist(
            instanceId = instanceId,
            versionId = versionId,
            state = state(instanceId, versionId).copy(fastMpEnabled = fastMpEnabled),
        )
    }

    fun updateFastMpRole(
        instanceId: GameInstanceId,
        versionId: Int,
        fastMpRole: WindowsFastMpRole,
    ) {
        persist(
            instanceId = instanceId,
            versionId = versionId,
            state = state(instanceId, versionId).copy(fastMpRole = fastMpRole),
        )
    }

    fun updateConsoleLogEnabled(
        instanceId: GameInstanceId,
        versionId: Int,
        consoleLogEnabled: Boolean,
    ) {
        persist(
            instanceId = instanceId,
            versionId = versionId,
            state = state(instanceId, versionId).copy(consoleLogEnabled = consoleLogEnabled),
        )
    }

    fun moveState(
        instanceId: GameInstanceId,
        fromVersionId: Int,
        toVersionId: Int,
    ) {
        if (fromVersionId == toVersionId) return
        val lines = stateStore.read(stateKey(instanceId, fromVersionId))
        stateStore.write(stateKey(instanceId, fromVersionId), null)
        stateStore.write(stateKey(instanceId, toVersionId), lines)
    }

    fun deleteState(
        instanceId: GameInstanceId,
        versionId: Int,
    ) {
        stateStore.write(stateKey(instanceId, versionId), null)
    }

    private fun persist(
        instanceId: GameInstanceId,
        versionId: Int,
        state: WindowsLaunchOptionsState,
    ) {
        if (state.launchMode == WindowsLaunchMode.DEFAULT &&
            state.clientIdText.trim().isBlank() &&
            state.steamSaveDirectoryName.trim().isBlank() &&
            !state.fastMpEnabled &&
            state.fastMpRole == WindowsFastMpRole.CLIENT &&
            !state.consoleLogEnabled
        ) {
            stateStore.write(stateKey(instanceId, versionId), null)
            return
        }
        val lines = buildList {
            add("mode\t${state.launchMode.name}")
            add("clientId\t${state.clientIdText}")
            add("steamDir\t${state.steamSaveDirectoryName}")
            add("fastMp\t${state.fastMpEnabled}")
            add("fastMpRole\t${state.fastMpRole.name}")
            add("consoleLog\t${state.consoleLogEnabled}")
        }
        stateStore.write(stateKey(instanceId, versionId), lines.joinToString("\n"))
    }

    private fun stateKey(
        instanceId: GameInstanceId,
        versionId: Int,
    ): String = "sts2.windows.launch_options.${instanceId.value}.$versionId"
}

private class InMemoryWindowsRunningProcessStateStore {
    private var records = emptyList<WindowsRunningProcessRecord>()

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread(
                {
                    shutdownAllProcesses()
                },
                "sts2-windows-running-process-shutdown",
            ),
        )
    }

    @Synchronized
    fun records(): List<WindowsRunningProcessRecord> {
        val persisted = records
        val alive = persisted
            .distinctBy { record -> record.pid }
            .filter { record -> isWindowsProcessAlive(record.pid) }
            .sortedBy { record -> record.startedAtMillis }
        if (alive != persisted) {
            replace(alive)
        }
        return alive
    }

    @Synchronized
    fun pruneDeadRecords(): Boolean {
        val persisted = records
        val alive = persisted
            .distinctBy { record -> record.pid }
            .filter { record -> isWindowsProcessAlive(record.pid) }
            .sortedBy { record -> record.startedAtMillis }
        if (alive == persisted) {
            return false
        }
        replace(alive)
        return true
    }

    @Synchronized
    fun recordLaunch(
        instanceId: GameInstanceId,
        versionId: Int,
        clientId: Int,
        pid: Long,
    ) {
        if (pid <= 0L) return
        val updated = records()
            .filterNot { record -> record.pid == pid }
            .plus(
                WindowsRunningProcessRecord(
                    instanceId = instanceId,
                    versionId = versionId,
                    clientId = clientId,
                    pid = pid,
                    startedAtMillis = System.currentTimeMillis(),
                ),
            )
            .sortedBy { record -> record.startedAtMillis }
        replace(updated)
    }

    @Synchronized
    fun moveVersionRecords(
        instanceId: GameInstanceId,
        fromVersionId: Int,
        toVersionId: Int,
    ) {
        if (fromVersionId == toVersionId) return
        val updated = records().map { record ->
            if (record.instanceId == instanceId && record.versionId == fromVersionId) {
                record.copy(versionId = toVersionId)
            } else {
                record
            }
        }
        replace(updated)
    }

    @Synchronized
    fun removeVersionRecords(
        instanceId: GameInstanceId,
        versionId: Int,
    ) {
        replace(records().filterNot { record -> record.instanceId == instanceId && record.versionId == versionId })
    }

    @Synchronized
    fun removeRecord(pid: Long) {
        replace(records().filterNot { record -> record.pid == pid })
    }

    @Synchronized
    private fun shutdownAllProcesses() {
        records.asSequence()
            .distinctBy { record -> record.pid }
            .sortedByDescending { record -> record.startedAtMillis }
            .forEach { record ->
                forceKillWindowsProcess(record.pid)
            }
        records = emptyList()
    }

    private fun replace(records: List<WindowsRunningProcessRecord>) {
        this.records = records
    }
}

private fun detectWindowsSteamGameDirectory(): String? {
    return steamLibraryRoots()
        .map { root -> File(root, WINDOWS_STS2_STEAM_RELATIVE_GAME_DIRECTORY) }
        .firstOrNull(File::isDirectory)
        ?.absolutePath
}

private fun steamLibraryRoots(): List<File> {
    val roots = linkedSetOf<File>()
    windowsSteamRootCandidates().forEach { steamRoot ->
        val canonicalRoot = runCatching { steamRoot.canonicalFile }.getOrDefault(steamRoot)
        if (!canonicalRoot.isDirectory) return@forEach
        roots += canonicalRoot
        val libraryFoldersFile = File(canonicalRoot, "steamapps\\libraryfolders.vdf")
        if (libraryFoldersFile.isFile) {
            roots += parseSteamLibraryFolders(libraryFoldersFile)
        }
    }
    return roots.toList()
}

private fun windowsSteamRootCandidates(): List<File> {
    return buildList {
        addAll(readWindowsSteamRegistryPaths())
        System.getenv("ProgramFiles(x86)")?.takeIf { it.isNotBlank() }?.let { add(File(it, "Steam")) }
        System.getenv("ProgramFiles")?.takeIf { it.isNotBlank() }?.let { add(File(it, "Steam")) }
        System.getenv("USERPROFILE")?.takeIf { it.isNotBlank() }?.let { add(File(it, "AppData\\Local\\Steam")) }
        File.listRoots().forEach { root ->
            add(File(root, "Steam"))
            add(File(root, "SteamLibrary"))
        }
    }
}

private fun readWindowsSteamRegistryPaths(): List<File> {
    val candidates = linkedSetOf<String>()
    queryWindowsRegistryValue("HKCU\\Software\\Valve\\Steam", "SteamPath")?.let(candidates::add)
    queryWindowsRegistryValue("HKCU\\Software\\Valve\\Steam", "InstallPath")?.let(candidates::add)
    queryWindowsRegistryValue("HKLM\\SOFTWARE\\WOW6432Node\\Valve\\Steam", "SteamPath")?.let(candidates::add)
    queryWindowsRegistryValue("HKLM\\SOFTWARE\\WOW6432Node\\Valve\\Steam", "InstallPath")?.let(candidates::add)
    queryWindowsRegistryValue("HKLM\\SOFTWARE\\Valve\\Steam", "SteamPath")?.let(candidates::add)
    queryWindowsRegistryValue("HKLM\\SOFTWARE\\Valve\\Steam", "InstallPath")?.let(candidates::add)
    return candidates
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { path -> File(path.replace('/', '\\')) }
}

private fun queryWindowsRegistryValue(
    keyPath: String,
    valueName: String,
): String? {
    return runCatching {
        val systemRoot = System.getenv("SystemRoot").orEmpty().ifBlank { "C:\\Windows" }
        val regExe = File(systemRoot, "System32\\reg.exe").absolutePath
        val process = ProcessBuilder(regExe, "query", keyPath, "/v", valueName)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            return null
        }
        val valueRegex = Regex("^\\s*${Regex.escape(valueName)}\\s+REG_\\w+\\s+(.+)$", RegexOption.MULTILINE)
        valueRegex.find(output)?.groupValues?.getOrNull(1)?.trim()
    }.getOrNull()
}

private fun parseSteamLibraryFolders(file: File): List<File> {
    val text = runCatching { file.readText() }.getOrDefault("")
    if (text.isBlank()) return emptyList()
    val pathRegex = Regex("\"path\"\\s+\"([^\"]+)\"")
    return pathRegex.findAll(text)
        .mapNotNull { match ->
            match.groupValues.getOrNull(1)
                ?.replace("\\\\", "\\")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
        }
        .toList()
}

private fun detectWindowsSteamSaveDirectoryNames(userHomeDirectory: String): List<String> {
    val steamSaveRoot = windowsSteamSaveRoot(userHomeDirectory)
    return steamSaveRoot.listFiles()
        ?.filter(File::isDirectory)
        ?.map(File::getName)
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.sortedWith(compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it })
        .orEmpty()
}

private fun selectedVersionRows(
    language: SupportedLanguage,
    version: Sts2VersionDefinition,
    launchOptions: WindowsResolvedLaunchOptions?,
): List<PageValueItemRegistration> {
    return windowsCurrentVersionRows(language, version)
}

private fun resolveWindowsLaunchOptions(
    version: Sts2VersionDefinition,
    state: WindowsLaunchOptionsState,
    userHomeDirectory: String,
    detectedSteamSaveDirectories: List<String>,
): WindowsResolvedLaunchOptions {
    val clientId = resolveWindowsLaunchClientId(state.clientIdText)
    val selectedSteamSaveDirectory = state.steamSaveDirectoryName
        .trim()
        .takeIf { directoryName -> directoryName.isNotBlank() && detectedSteamSaveDirectories.contains(directoryName) }
    val saveDirectory = when (state.launchMode) {
        WindowsLaunchMode.DEFAULT -> windowsSaveDirectory(userHomeDirectory, clientId)
        WindowsLaunchMode.STEAM -> selectedSteamSaveDirectory?.let { directoryName ->
            windowsSteamSaveDirectory(userHomeDirectory, directoryName)
        }.orEmpty()
    }
    return WindowsResolvedLaunchOptions(
        launchMode = state.launchMode,
        clientId = if (state.launchMode == WindowsLaunchMode.DEFAULT) clientId else null,
        steamSaveDirectoryName = selectedSteamSaveDirectory,
        saveDirectory = saveDirectory,
        fastMpEnabled = state.fastMpEnabled,
        fastMpRole = state.fastMpRole,
        consoleLogEnabled = state.consoleLogEnabled,
    )
}

private fun Sts2VersionDefinition.withWindowsAppliedLaunchOptions(
    userHomeDirectory: String,
    launchOptions: WindowsLaunchOptionsState,
    detectedSteamSaveDirectories: List<String>,
): Sts2VersionDefinition {
    val resolvedLaunchOptions = resolveWindowsLaunchOptions(
        version = this,
        state = launchOptions,
        userHomeDirectory = userHomeDirectory,
        detectedSteamSaveDirectories = detectedSteamSaveDirectories,
    )
    return copy(
        saveDirectory = resolvedLaunchOptions.saveDirectory,
        modDirectory = gameDirectory
            .trim()
            .takeIf { directory -> directory.isNotBlank() }
            ?.let { directory -> File(directory, "mods").absolutePath }
            .orEmpty(),
    )
}

private fun resolveWindowsLaunchClientId(clientIdText: String): Int {
    return clientIdText.trim().toIntOrNull()?.takeIf { clientId -> clientId >= 1 } ?: 1
}

private fun windowsLaunchModeLabel(
    language: SupportedLanguage,
    launchMode: WindowsLaunchMode,
): String {
    return when (launchMode) {
        WindowsLaunchMode.DEFAULT -> localized(language, "windows.entrypoint.0109")
        WindowsLaunchMode.STEAM -> localized(language, "windows.entrypoint.0110")
    }
}

private fun windowsLaunchClientIdSupportingText(
    language: SupportedLanguage,
    state: WindowsLaunchOptionsState,
): String {
    val normalizedClientId = state.clientIdText.trim()
    if (normalizedClientId.isNotBlank() && normalizedClientId.toIntOrNull()?.takeIf { it >= 1 } == null) {
        return localized(language, "windows.entrypoint.0111")
    }
    return localized(language, "windows.entrypoint.0112")
}

private fun windowsLaunchOptionsRows(
    language: SupportedLanguage,
    version: Sts2VersionDefinition,
    launchOptions: WindowsResolvedLaunchOptions,
): List<PageValueItemRegistration> {
    return listOf(
        row(language, "windows.entrypoint.row.0011", windowsLaunchModeLabel(language, launchOptions.launchMode)),
        row(language, "windows.entrypoint.row.0012", windowsRendererLabel(version.renderer)),
        row(language, "windows.entrypoint.row.0013", windowsFastMpSummaryLabel(language, launchOptions)),
        row(language, "windows.entrypoint.row.0014", windowsConsoleLogSummaryLabel(language, launchOptions)),
        if (launchOptions.launchMode == WindowsLaunchMode.DEFAULT) {
            row(language, "windows.entrypoint.row.0015", launchOptions.clientId?.toString().orEmpty())
        } else {
            row(language, "windows.entrypoint.row.0016", launchOptions.steamSaveDirectoryName.orEmpty(), )
        },
        row(language, "windows.entrypoint.row.0017", launchOptions.saveDirectory),
        row(language, "windows.entrypoint.row.0018", windowsSettingsSavePath(language, launchOptions.saveDirectory)),
        row(language, "windows.entrypoint.row.0019", version.modDirectory),
    )
}

private fun buildWindowsModDetailContribution(
    context: PageContext,
    selectedVersion: Sts2VersionDefinition?,
    mod: Sts2ScannedMod,
    modEnabledState: Sts2ResolvedModEnabledState,
    pendingReplacement: Sts2ModPendingReplacement?,
    onImportPck: (() -> Unit)?,
    onImportDll: (() -> Unit)?,
    onConfirmPendingReplacement: () -> Unit,
    onCancelPendingReplacement: () -> Unit,
): PageContributionBundle {
    val language = context.strings.language
    val matchingPendingReplacement = pendingReplacement?.takeIf { replacement ->
        replacement.modId == mod.manifest.id
    }
    return PageContributionBundle(
        sourceId = STS2_TEMPLATE_ID,
        page = PageRegistration(
            id = windowsModDetailPageId(mod),
            sourceId = STS2_TEMPLATE_ID,
            title = PageTextDirect(windowsModDisplayName(mod)),
            subtitle = PageTextDirect(windowsModSubtitle(language, mod, modEnabledState)),
            actionLabel = PageTextDirect(context.strings.commonBack),
            action = { context.goBack() },
            supportedTargets = setOf(PlatformTarget.WINDOWS),
        ),
        nodes = buildList {
            add(
                PageSectionRegistration(
                    nodeId = "sts2_windows_mod_detail_${mod.discoveryOrder}",
                    pageId = windowsModDetailPageId(mod),
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    title = PageTextDirect(localized(language, "windows.entrypoint.0113")),
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_windows_mod_detail_card_${mod.discoveryOrder}",
                    pageId = windowsModDetailPageId(mod),
                    parentNodeId = "sts2_windows_mod_detail_${mod.discoveryOrder}",
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    widget = PageWidgetDetailCard(
                        title = PageTextDirect(windowsModDisplayName(mod)),
                        subtitle = PageTextDirect(windowsModSubtitle(language, mod, modEnabledState)),
                        rows = windowsModDetailRows(language, selectedVersion, mod, modEnabledState),
                        tone = if (mod.issues.isEmpty()) PageWidgetTone.ACCENT else PageWidgetTone.DANGER,
                    ),
                ),
            )
            matchingPendingReplacement?.let { replacement ->
                add(
                    PageWidgetRegistration(
                        nodeId = "sts2_windows_mod_detail_pending_${mod.discoveryOrder}",
                        pageId = windowsModDetailPageId(mod),
                        parentNodeId = "sts2_windows_mod_detail_${mod.discoveryOrder}",
                        sourceId = STS2_TEMPLATE_ID,
                        supportedTargets = setOf(PlatformTarget.WINDOWS),
                        orderHint = 5,
                        widget = PageWidgetDetailCard(
                            title = PageTextDirect(modPendingReplacementTitle(language, replacement)),
                            subtitle = PageTextDirect(replacement.detail),
                            rows = modPendingReplacementRows(language, replacement),
                            actions = listOf(
                                PageActionRegistration(
                                    id = "sts2_windows_mod_detail_pending_confirm_${mod.discoveryOrder}",
                                    label = PageTextDirect(modPendingReplacementConfirmLabel(language, replacement)),
                                    style = PageActionStyle.FILLED_TONAL,
                                    onClick = onConfirmPendingReplacement,
                                ),
                                PageActionRegistration(
                                    id = "sts2_windows_mod_detail_pending_cancel_${mod.discoveryOrder}",
                                    label = PageTextDirect(localized(language, "windows.entrypoint.0114")),
                                    style = PageActionStyle.OUTLINED,
                                    onClick = onCancelPendingReplacement,
                                ),
                            ),
                            tone = PageWidgetTone.DEFAULT,
                        ),
                    ),
                )
            }
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_windows_mod_detail_toggle_${mod.discoveryOrder}",
                    pageId = windowsModDetailPageId(mod),
                    parentNodeId = "sts2_windows_mod_detail_${mod.discoveryOrder}",
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    orderHint = 10,
                    widget = com.dreamyloong.tlauncher.sdk.page.PageWidgetToggleCard(
                        title = PageTextDirect(localized(language, "windows.entrypoint.0115")),
                        subtitle = PageTextDirect(windowsModEnabledToggleSubtitle(language, modEnabledState)),
                        checked = modEnabledState.enabled,
                        enabled = modEnabledState.settingsFilePath != null && modEnabledState.readError == null,
                        onCheckedChange = { enabled ->
                            updateSts2ModEnabledState(selectedVersion, mod.manifest.id, enabled)
                            context.refreshPage()
                        },
                    ),
                ),
            )
            add(
                PageWidgetRegistration(
                    nodeId = "sts2_windows_mod_detail_actions_${mod.discoveryOrder}",
                    pageId = windowsModDetailPageId(mod),
                    parentNodeId = "sts2_windows_mod_detail_${mod.discoveryOrder}",
                    sourceId = STS2_TEMPLATE_ID,
                    supportedTargets = setOf(PlatformTarget.WINDOWS),
                    orderHint = 20,
                    widget = PageWidgetButtonStack(
                        actions = buildList {
                            onImportPck?.let { importPck ->
                                add(
                                    PageActionRegistration(
                                        id = "sts2_windows_mod_import_pck_${mod.discoveryOrder}",
                                        label = PageTextDirect(localized(language, "windows.entrypoint.0116")),
                                        style = PageActionStyle.OUTLINED,
                                        onClick = importPck,
                                    ),
                                )
                            }
                            onImportDll?.let { importDll ->
                                add(
                                    PageActionRegistration(
                                        id = "sts2_windows_mod_import_dll_${mod.discoveryOrder}",
                                        label = PageTextDirect(localized(language, "windows.entrypoint.0117")),
                                        style = PageActionStyle.OUTLINED,
                                        onClick = importDll,
                                    ),
                                )
                            }
                            add(
                                PageActionRegistration(
                                    id = "sts2_windows_mod_delete_${mod.discoveryOrder}",
                                    label = PageTextDirect(localized(language, "windows.entrypoint.0118")),
                                    style = PageActionStyle.FILLED_TONAL,
                                    onClick = {
                                        if (deleteSts2ScannedMod(selectedVersion, mod)) {
                                            removeSts2ModSettingsEntry(selectedVersion, mod.manifest.id)
                                            context.replaceCurrentPage(WINDOWS_MOD_MANAGER_PAGE_ID)
                                        } else {
                                            context.refreshPage()
                                        }
                                    },
                                ),
                            )
                            add(
                                PageActionRegistration(
                                    id = "sts2_windows_mod_back_${mod.discoveryOrder}",
                                    label = PageTextDirect(localized(language, "windows.entrypoint.0119")),
                                    style = PageActionStyle.OUTLINED,
                                    onClick = { context.replaceCurrentPage(WINDOWS_MOD_MANAGER_PAGE_ID) },
                                ),
                            )
                        },
                    ),
                ),
            )
        },
    )
}

private fun windowsModManagerSubtitle(
    language: SupportedLanguage,
    selectedVersion: Sts2VersionDefinition?,
    modScanResult: Sts2ModScanResult?,
): String {
    return when {
        selectedVersion == null -> localized(language, "windows.entrypoint.0120")
        modScanResult == null || !modScanResult.modDirectoryConfigured -> localized(language, "windows.entrypoint.0121")
        !modScanResult.modDirectoryExists -> localized(language, "windows.entrypoint.0122")
        modScanResult.totalProblemEntryCount > 0 -> localized(language, "windows.entrypoint.0123")
        else -> localized(language, "windows.entrypoint.0124")
    }
}

private fun windowsModManagerRows(
    language: SupportedLanguage,
    selectedVersion: Sts2VersionDefinition?,
    modScanResult: Sts2ModScanResult?,
): List<PageValueItemRegistration> {
    return buildList {
        if (selectedVersion != null) {
            add(row(language, "windows.entrypoint.row.0020", selectedVersion.modDirectory))
            add(row(language, "windows.entrypoint.row.0021", windowsSettingsSavePath(language, selectedVersion.saveDirectory)))
        }
        if (modScanResult != null) {
            add(row(language, "windows.entrypoint.row.0022", modScanResult.mods.size.toString()))
            add(row(language, "windows.entrypoint.row.0023", modScanResult.totalProblemEntryCount.toString()))
            add(row(language, "windows.entrypoint.row.0024", modScanResult.affectedModCount.toString()))
        }
    }
}

private fun windowsModDisplayName(mod: Sts2ScannedMod): String {
    return mod.manifest.name?.trim()?.takeIf { it.isNotBlank() } ?: mod.manifest.id
}

private fun windowsModSubtitle(
    language: SupportedLanguage,
    mod: Sts2ScannedMod,
    modEnabledState: Sts2ResolvedModEnabledState,
): String {
    val description = mod.manifest.description?.trim().orEmpty()
    if (description.isNotBlank()) {
        return description
    }
    if (mod.issues.isNotEmpty()) {
        return windowsModIssueSummary(language, mod.issues)
    }
    return localized(language, "windows.entrypoint.0125", listOf(mod.relativeManifestPath))
}

private fun windowsModIssueSummary(
    language: SupportedLanguage,
    issues: List<Sts2ScannedModIssue>,
): String {
    return issues.joinToString(
        separator = if (language == SupportedLanguage.ZH_CN) "；" else "; ",
    ) { issue ->
        when (issue) {
            is Sts2ScannedModIssue.DuplicateId -> localized(language, "windows.entrypoint.0126", listOf(issue.id, issue.duplicateCount))

            is Sts2ScannedModIssue.MissingDependencies -> localized(language, "windows.entrypoint.0127", listOf(issue.dependencies.joinToString(", ")))

            Sts2ScannedModIssue.MissingDll -> localized(language, "windows.entrypoint.0128")

            Sts2ScannedModIssue.MissingPck -> localized(language, "windows.entrypoint.0129")

            Sts2ScannedModIssue.NoRuntimeArtifactDeclared -> localized(language, "windows.entrypoint.0130")
        }
    }
}

private fun windowsModDetailRows(
    language: SupportedLanguage,
    selectedVersion: Sts2VersionDefinition?,
    mod: Sts2ScannedMod,
    modEnabledState: Sts2ResolvedModEnabledState,
): List<PageValueItemRegistration> {
    return buildList {
        add(row(language, "windows.entrypoint.row.0025", mod.manifest.id))
        mod.manifest.name?.takeIf { it.isNotBlank() }?.let { add(row(language, "windows.entrypoint.row.0026", it)) }
        add(row(language, "windows.entrypoint.row.0027", windowsModEnabledStatusLabel(language, modEnabledState)))
        add(row(language, "windows.entrypoint.row.0028", mod.manifest.version ?: localized(language, "windows.entrypoint.0131")))
        add(row(language, "windows.entrypoint.row.0029", mod.manifest.author ?: localized(language, "windows.entrypoint.0132")))
        mod.manifest.description?.takeIf { it.isNotBlank() }?.let { add(row(language, "windows.entrypoint.row.0030", it)) }
        add(
            row(language, "windows.entrypoint.row.0031", mod.manifest.dependencies.takeIf(List<String>::isNotEmpty)?.joinToString(", ")
                    ?: localized(language, "windows.entrypoint.0133"), ),
        )
        add(row(language, "windows.entrypoint.row.0032", windowsModArtifactSummary(language, mod.manifest)))
        add(
            row(language, "windows.entrypoint.row.0033", windowsModArtifactDetectedLabel(language, mod.manifest.hasPck, mod.pckDetected, "${mod.manifest.id}.pck"), ),
        )
        add(
            row(language, "windows.entrypoint.row.0034", windowsModArtifactDetectedLabel(language, mod.manifest.hasDll, mod.dllDetected, "${mod.manifest.id}.dll"), ),
        )
        add(
            row(language, "windows.entrypoint.row.0035", if (mod.manifest.affectsGameplay) localized(language, "windows.entrypoint.0134") else localized(language, "windows.entrypoint.0135"), ),
        )
        selectedVersion?.let { version ->
            add(row(language, "windows.entrypoint.row.0036", version.modDirectory))
            add(row(language, "windows.entrypoint.row.0037", windowsSettingsSavePath(language, version.saveDirectory)))
        }
        modEnabledState.readError?.let { readError ->
            add(row(language, "windows.entrypoint.row.0038", readError))
        }
        add(row(language, "windows.entrypoint.row.0039", mod.relativeModPath))
        add(row(language, "windows.entrypoint.row.0040", mod.manifestFilePath))
        if (mod.issues.isNotEmpty()) {
            add(row(language, "windows.entrypoint.row.0041", windowsModIssueSummary(language, mod.issues)))
        }
    }
}

private fun windowsModEnabledToggleSubtitle(
    language: SupportedLanguage,
    state: Sts2ResolvedModEnabledState,
): String {
    return when {
        state.readError != null -> localized(language, "windows.entrypoint.0136")
        state.settingsFilePath == null -> localized(language, "windows.entrypoint.0137")
        state.blockedByGlobalSwitch -> localized(language, "windows.entrypoint.0138")
        state.explicit -> localized(language, "windows.entrypoint.0139")
        else -> localized(language, "windows.entrypoint.0140")
    }
}

private fun windowsModArtifactDetectedLabel(
    language: SupportedLanguage,
    required: Boolean,
    detectedState: Int,
    fileName: String,
): String {
    if (!required) {
        return localized(language, "windows.entrypoint.0141")
    }
    return when (detectedState) {
        1 -> localized(language, "windows.entrypoint.0142", listOf(fileName))
        0 -> localized(language, "windows.entrypoint.0143", listOf(fileName))
        else -> localized(language, "windows.entrypoint.0144")
    }
}

private fun windowsModEnabledStatusLabel(
    language: SupportedLanguage,
    modEnabledState: Sts2ResolvedModEnabledState,
): String {
    return when {
        modEnabledState.readError != null -> localized(language, "windows.entrypoint.0145")
        modEnabledState.blockedByGlobalSwitch -> localized(language, "windows.entrypoint.0146")
        modEnabledState.enabled && modEnabledState.explicit -> localized(language, "windows.entrypoint.0147")
        modEnabledState.enabled -> localized(language, "windows.entrypoint.0148")
        else -> localized(language, "windows.entrypoint.0149")
    }
}

private fun windowsModArtifactSummary(
    language: SupportedLanguage,
    manifest: com.dreamyloong.template.sts2.Sts2ModManifest,
): String {
    val parts = buildList {
        if (manifest.hasDll) add("DLL")
        if (manifest.hasPck) add("PCK")
    }
    return if (parts.isNotEmpty()) parts.joinToString(" + ") else localized(language, "windows.entrypoint.0150")
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
        add(row(language, "windows.entrypoint.row.0042", status.sourceFileName))
        status.modId?.let { add(row(language, "windows.entrypoint.row.0043", it)) }
        status.targetDirectoryPath?.let { add(row(language, "windows.entrypoint.row.0044", it)) }
        if (status.importedFileNames.isNotEmpty()) {
            add(row(language, "windows.entrypoint.row.0045", status.importedFileNames.joinToString(", ")))
        }
        if (status.missingFileNames.isNotEmpty()) {
            add(row(language, "windows.entrypoint.row.0046", status.missingFileNames.joinToString(", ")))
        }
    }
}

private fun modPendingReplacementRows(
    language: SupportedLanguage,
    pendingReplacement: Sts2ModPendingReplacement,
): List<PageValueItemRegistration> {
    return buildList {
        add(row(language, "windows.entrypoint.row.0047", pendingReplacement.sourceFileName))
        add(row(language, "windows.entrypoint.row.0048", pendingReplacement.modId))
        if (pendingReplacement.kind == Sts2ModPendingReplacementKind.MOD_PACKAGE) {
            pendingReplacement.existingVersion?.let { add(row(language, "windows.entrypoint.row.0049", it)) }
            pendingReplacement.manifestVersion?.let { add(row(language, "windows.entrypoint.row.0050", it)) }
        }
        add(row(language, "windows.entrypoint.row.0051", pendingReplacement.targetDirectoryPath))
        if (pendingReplacement.replacingFileNames.isNotEmpty()) {
            add(row(language, "windows.entrypoint.row.0052", pendingReplacement.replacingFileNames.joinToString(", ")))
        }
        if (pendingReplacement.addingFileNames.isNotEmpty()) {
            add(row(language, "windows.entrypoint.row.0053", pendingReplacement.addingFileNames.joinToString(", ")))
        }
        if (pendingReplacement.missingFileNames.isNotEmpty()) {
            add(row(language, "windows.entrypoint.row.0054", pendingReplacement.missingFileNames.joinToString(", ")))
        }
    }
}

private fun modPendingReplacementTitle(
    language: SupportedLanguage,
    pendingReplacement: Sts2ModPendingReplacement,
): String {
    val isUpdate = pendingReplacement.kind == Sts2ModPendingReplacementKind.MOD_PACKAGE &&
        isSts2ModVersionUpdate(pendingReplacement.manifestVersion, pendingReplacement.existingVersion)
    return if (isUpdate) localized(language, "windows.entrypoint.0151")
    else localized(language, "windows.entrypoint.0152")
}

private fun modPendingReplacementConfirmLabel(
    language: SupportedLanguage,
    pendingReplacement: Sts2ModPendingReplacement,
): String {
    val isUpdate = pendingReplacement.kind == Sts2ModPendingReplacementKind.MOD_PACKAGE &&
        isSts2ModVersionUpdate(pendingReplacement.manifestVersion, pendingReplacement.existingVersion)
    return if (isUpdate) localized(language, "windows.entrypoint.0153")
    else localized(language, "windows.entrypoint.0154")
}

private fun windowsModDetailPageId(mod: Sts2ScannedMod): String = "$WINDOWS_MOD_MANAGER_PAGE_ID.detail.${mod.discoveryOrder}"

private fun versionSummaryRows(
    language: SupportedLanguage,
    version: Sts2VersionDefinition,
    selected: Boolean,
    launchOptions: WindowsResolvedLaunchOptions? = null,
): List<PageValueItemRegistration> {
    return buildList {
        add(
            row(language, "windows.entrypoint.row.0055", if (selected) {
                    localized(language, "windows.entrypoint.0155")
                } else {
                    localized(language, "windows.entrypoint.0156")
                }, ),
            )
            addAll(
                if (selected) {
                    windowsCurrentVersionRows(language, version)
                } else {
                    versionDirectoryRows(language, version, launchOptions)
                },
            )
        }
    }

private fun windowsCurrentVersionRows(
    language: SupportedLanguage,
    version: Sts2VersionDefinition,
): List<PageValueItemRegistration> {
    return listOf(
        row(language, "windows.entrypoint.row.0056", version.versionId),
        row(language, "windows.entrypoint.row.0057", version.gameDirectory),
        row(language, "windows.entrypoint.row.0058", version.modDirectory),
    )
}

private fun versionDirectoryRows(
    language: SupportedLanguage,
    version: Sts2VersionDefinition,
    launchOptions: WindowsResolvedLaunchOptions? = null,
): List<PageValueItemRegistration> {
    return buildList {
        add(row(language, "windows.entrypoint.row.0059", version.versionId))
        launchOptions?.let { options ->
            add(row(language, "windows.entrypoint.row.0060", windowsLaunchModeLabel(language, options.launchMode)))
            add(row(language, "windows.entrypoint.row.0061", windowsRendererLabel(version.renderer)))
            add(row(language, "windows.entrypoint.row.0062", windowsFastMpSummaryLabel(language, options)))
            add(row(language, "windows.entrypoint.row.0063", windowsConsoleLogSummaryLabel(language, options)))
            if (options.launchMode == WindowsLaunchMode.DEFAULT) {
                add(row(language, "windows.entrypoint.row.0064", options.clientId?.toString().orEmpty()))
            } else {
                add(row(language, "windows.entrypoint.row.0065", options.steamSaveDirectoryName.orEmpty()))
            }
        }
        add(row(language, "windows.entrypoint.row.0066", version.gameDirectory))
        add(row(language, "windows.entrypoint.row.0067", launchOptions?.saveDirectory ?: version.saveDirectory))
        add(row(language, "windows.entrypoint.row.0068", version.modDirectory))
    }
}

private fun validationErrorText(
    language: SupportedLanguage,
    error: Sts2VersionValidationError?,
): String? {
    return when (error) {
        Sts2VersionValidationError.INVALID_CLIENT_ID -> localized(language, "windows.entrypoint.0157")

        Sts2VersionValidationError.DUPLICATE_CLIENT_ID -> localized(language, "windows.entrypoint.0158")

        Sts2VersionValidationError.INVALID_SPINE_UPDATE_DIVISOR,
        Sts2VersionValidationError.INVALID_ASSET_LOADING_BATCH_SIZE,
        Sts2VersionValidationError.INVALID_PARTICLE_SCALE_PERCENT,
        null,
        -> null
    }
}

private fun Sts2VersionDraft.withWindowsDerivedPaths(
    userHomeDirectory: String,
    detectedSteamGameDirectory: String?,
): Sts2VersionDraft {
    val normalizedGameDirectory = gameDirectory
        .trim()
        .ifBlank { detectedSteamGameDirectory.orEmpty().trim() }
    return copy(
        gameDirectory = normalizedGameDirectory,
        saveDirectory = windowsSaveDirectory(userHomeDirectory, 1),
        modDirectory = if (normalizedGameDirectory.isBlank()) {
            ""
        } else {
            File(normalizedGameDirectory, "mods").absolutePath
        },
    )
}

private fun Sts2VersionDefinition.toEditableDraft(): Sts2VersionDraft {
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
        renderer = renderer,
    )
}

private fun Sts2VersionDefinition.toLaunchSettingsDraft(): Sts2LaunchSettingsDraft {
    return Sts2LaunchSettingsDraft(
        spineUpdateDivisorText = spineUpdateDivisor.toString(),
        preloadTrimEnabled = preloadTrimEnabled,
        assetLoadingBatchSizeText = assetLoadingBatchSize.toString(),
        mobileShadersEnabled = mobileShadersEnabled,
        particleScalePercentText = particleScalePercent.toString(),
        glowMode = glowMode,
        vfxLimitEnabled = vfxLimitEnabled,
        renderer = normalizeWindowsRenderer(renderer),
    )
}

private fun windowsSaveDirectory(
    userHomeDirectory: String,
    clientId: Int,
): String {
    return File(
        File(
            File(
                File(userHomeDirectory),
                "AppData",
            ),
            "Roaming",
        ),
        "SlayTheSpire2\\default\\$clientId",
    ).absolutePath
}

private fun windowsSteamSaveDirectory(
    userHomeDirectory: String,
    steamDirectoryName: String,
): String {
    return File(windowsSteamSaveRoot(userHomeDirectory), steamDirectoryName).absolutePath
}

private fun windowsSteamSaveRoot(userHomeDirectory: String): File {
    return File(
        File(
            File(
                File(userHomeDirectory),
                "AppData",
            ),
            "Roaming",
        ),
        "SlayTheSpire2\\steam",
    )
}

private fun windowsSettingsSavePath(
    language: SupportedLanguage,
    saveDirectory: String,
): String {
    val normalized = saveDirectory.trim()
    return if (normalized.isBlank()) {
        localized(language, "windows.entrypoint.0159")
    } else {
        File(normalized, "settings.save").absolutePath
    }
}

private fun windowsLaunchExecutableFile(version: Sts2VersionDefinition): File {
    return File(version.gameDirectory.trim(), "SlayTheSpire2.exe")
}

private fun launchWindowsVersion(
    instanceId: GameInstanceId,
    version: Sts2VersionDefinition,
    launchOptions: WindowsResolvedLaunchOptions?,
    runningProcessStore: InMemoryWindowsRunningProcessStateStore,
) {
    val executable = windowsLaunchExecutableFile(version)
    if (!executable.isFile) return
    val recordedClientId = when (launchOptions?.launchMode) {
        WindowsLaunchMode.STEAM -> 0
        WindowsLaunchMode.DEFAULT -> launchOptions.clientId ?: 1
        null -> 1
    }
    val gameCommand = buildList {
        add(executable.absolutePath)
        add("--rendering-driver")
        add(windowsRendererDriverArgument(version.renderer))
        if (launchOptions?.launchMode == WindowsLaunchMode.DEFAULT) {
            add("-force-steam=off")
            launchOptions.clientId?.let { clientId ->
                add("-clientId=$clientId")
            }
        }
        if (launchOptions?.fastMpEnabled == true) {
            add("--fastmp=${launchOptions.fastMpRole.name.lowercase()}")
        }
        if (launchOptions?.consoleLogEnabled == true) {
            add("--log")
        }
    }
    val launchedPid = if (launchOptions?.consoleLogEnabled == true) {
        startWindowsConsoleLaunch(
            workingDirectory = executable.parentFile,
            command = gameCommand,
        )
    } else {
        ProcessBuilder(gameCommand)
            .directory(executable.parentFile)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .pid()
    }
    runningProcessStore.recordLaunch(
        instanceId = instanceId,
        versionId = version.clientId,
        clientId = recordedClientId,
        pid = launchedPid,
    )
}

private fun startWindowsConsoleLaunch(
    workingDirectory: File,
    command: List<String>,
): Long {
    val javaExecutable = resolveWindowsJavaExecutable()
    val runtimeJar = resolveWindowsTemplateRuntimeJar()
    val pidFile = File.createTempFile("sts2_windows_console_launch_", ".pid").apply {
        delete()
        deleteOnExit()
    }
    val helperCommand = buildList {
        add(javaExecutable.absolutePath)
        add("-cp")
        add(runtimeJar.absolutePath)
        add("com.dreamyloong.template.sts2.windows.Sts2WindowsConsoleLaunchHelper")
        add("--working-dir")
        add(workingDirectory.absolutePath)
        add("--pid-file")
        add(pidFile.absolutePath)
        add("--")
        addAll(command)
    }
    val startupInfo = WindowsStartupInfo().apply {
        cb = size()
    }
    startupInfo.write()
    val processInformation = WindowsProcessInformation()
    val commandLine = windowsCreateProcessCommandLine(helperCommand)
    val created = WindowsKernel32.INSTANCE.CreateProcessW(
        null,
        commandLine,
        null,
        null,
        false,
        WindowsKernel32.CREATE_NEW_CONSOLE,
        null,
        workingDirectory.absolutePath,
        startupInfo,
        processInformation,
    )
    if (!created) {
        throw IllegalStateException(
            "CreateProcessW failed for STS2 Windows console helper launch. error=${WindowsKernel32.INSTANCE.GetLastError()}",
        )
    }
    val helperPid = processInformation.dwProcessId.toLong()
    WindowsKernel32.INSTANCE.CloseHandle(processInformation.hThread)
    WindowsKernel32.INSTANCE.CloseHandle(processInformation.hProcess)
    return waitForWindowsLaunchedPid(pidFile) ?: helperPid
}

private fun waitForWindowsLaunchedPid(pidFile: File): Long? {
    repeat(60) {
        val pid = pidFile.takeIf(File::isFile)
            ?.readText()
            ?.trim()
            ?.toLongOrNull()
        if (pid != null && pid > 0L) {
            return pid
        }
        Thread.sleep(50)
    }
    return null
}

private fun windowsCreateProcessCommandLine(command: List<String>): String {
    return command.joinToString(" ") { argument -> windowsCreateProcessArgument(argument) }
}

private fun windowsCreateProcessArgument(argument: String): String {
    if (argument.isEmpty()) {
        return "\"\""
    }
    if (argument.none { character -> character == ' ' || character == '\t' || character == '"' }) {
        return argument
    }
    val result = StringBuilder("\"")
    var backslashCount = 0
    argument.forEach { character ->
        when (character) {
            '\\' -> backslashCount += 1
            '"' -> {
                repeat(backslashCount * 2 + 1) { result.append('\\') }
                result.append('"')
                backslashCount = 0
            }

            else -> {
                repeat(backslashCount) { result.append('\\') }
                backslashCount = 0
                result.append(character)
            }
        }
    }
    repeat(backslashCount * 2) { result.append('\\') }
    result.append('"')
    return result.toString()
}

interface WindowsKernel32 : StdCallLibrary {
    fun CreateProcessW(
        applicationName: String?,
        commandLine: String?,
        processAttributes: Pointer?,
        threadAttributes: Pointer?,
        inheritHandles: Boolean,
        creationFlags: Int,
        environment: Pointer?,
        currentDirectory: String?,
        startupInfo: WindowsStartupInfo,
        processInformation: WindowsProcessInformation,
    ): Boolean

    fun CloseHandle(handle: Pointer?): Boolean

    fun GetLastError(): Int

    companion object {
        const val CREATE_NEW_CONSOLE: Int = 0x00000010

        val INSTANCE: WindowsKernel32 = Native.load(
            "kernel32",
            WindowsKernel32::class.java,
            W32APIOptions.UNICODE_OPTIONS,
        )
    }
}

private fun resolveWindowsJavaExecutable(): File {
    val javaHome = File(System.getProperty("java.home").orEmpty())
    val candidates = listOf(
        javaHome.resolve("bin/java.exe"),
        javaHome.resolve("bin/java"),
    )
    return candidates.firstOrNull(File::isFile)
        ?: error("Java executable was not found under ${javaHome.absolutePath}")
}

private fun resolveWindowsTemplateRuntimeJar(): File {
    val location = Sts2TemplateWindowsEntrypoint::class.java.protectionDomain.codeSource.location
        ?: error("Unable to resolve the Windows STS2 template runtime location.")
    return File(location.toURI())
}

@Structure.FieldOrder(
    "cb",
    "lpReserved",
    "lpDesktop",
    "lpTitle",
    "dwX",
    "dwY",
    "dwXSize",
    "dwYSize",
    "dwXCountChars",
    "dwYCountChars",
    "dwFillAttribute",
    "dwFlags",
    "wShowWindow",
    "cbReserved2",
    "lpReserved2",
    "hStdInput",
    "hStdOutput",
    "hStdError",
)
class WindowsStartupInfo : Structure() {
    @JvmField
    var cb: Int = 0

    @JvmField
    var lpReserved: Pointer? = null

    @JvmField
    var lpDesktop: Pointer? = null

    @JvmField
    var lpTitle: Pointer? = null

    @JvmField
    var dwX: Int = 0

    @JvmField
    var dwY: Int = 0

    @JvmField
    var dwXSize: Int = 0

    @JvmField
    var dwYSize: Int = 0

    @JvmField
    var dwXCountChars: Int = 0

    @JvmField
    var dwYCountChars: Int = 0

    @JvmField
    var dwFillAttribute: Int = 0

    @JvmField
    var dwFlags: Int = 0

    @JvmField
    var wShowWindow: Short = 0

    @JvmField
    var cbReserved2: Short = 0

    @JvmField
    var lpReserved2: Pointer? = null

    @JvmField
    var hStdInput: Pointer? = null

    @JvmField
    var hStdOutput: Pointer? = null

    @JvmField
    var hStdError: Pointer? = null
}

@Structure.FieldOrder(
    "hProcess",
    "hThread",
    "dwProcessId",
    "dwThreadId",
)
class WindowsProcessInformation : Structure() {
    @JvmField
    var hProcess: Pointer? = null

    @JvmField
    var hThread: Pointer? = null

    @JvmField
    var dwProcessId: Int = 0

    @JvmField
    var dwThreadId: Int = 0
}

private fun windowsRecordedLaunchClientId(launchOptions: WindowsResolvedLaunchOptions): Int {
    return when (launchOptions.launchMode) {
        WindowsLaunchMode.DEFAULT -> launchOptions.clientId ?: 1
        WindowsLaunchMode.STEAM -> 0
    }
}

private fun windowsRunningAutoRefreshRegistration(
    nodeId: String,
    pageId: String,
    sourceId: String,
    runningProcessStore: InMemoryWindowsRunningProcessStateStore,
    context: PageContext,
): PageWidgetRegistration {
    return PageWidgetRegistration(
        nodeId = nodeId,
        pageId = pageId,
        sourceId = sourceId,
        supportedTargets = setOf(PlatformTarget.WINDOWS),
        orderHint = Int.MAX_VALUE,
        widget = PageWidgetAutoRefresh(
            intervalMillis = 1200L,
            onRefresh = {
                if (runningProcessStore.pruneDeadRecords()) {
                    context.refreshPage()
                }
            },
        ),
    )
}

private fun windowsRunningProcessTitle(
    language: SupportedLanguage,
    record: WindowsRunningProcessRecord,
): String {
    return localized(language, "windows.entrypoint.0160", listOf(windowsRunningClientIdLabel(language, record.clientId)))
}

private fun windowsRunningProcessSubtitle(
    language: SupportedLanguage,
    instance: GameInstance?,
    version: Sts2VersionDefinition?,
    record: WindowsRunningProcessRecord,
): String {
    val versionPart = version?.let { value -> versionDisplayName(language, value) }
    val instancePart = instance?.displayName?.takeIf { value -> value.isNotBlank() }
    val fallbackVersionPart = localized(language, "windows.entrypoint.0161", listOf(record.versionId))
    return when {
        versionPart != null && instancePart != null -> localized(language, "windows.entrypoint.0162", listOf(instancePart, versionPart))
        versionPart == null && instancePart != null -> localized(language, "windows.entrypoint.0163", listOf(instancePart, fallbackVersionPart))
        versionPart != null -> localized(language, "windows.entrypoint.0164", listOf(versionPart))
        instancePart != null -> localized(language, "windows.entrypoint.0165", listOf(instancePart))
        else -> localized(language, "windows.entrypoint.0166")
    }
}

private fun windowsRunningProcessRows(
    language: SupportedLanguage,
    instance: GameInstance?,
    version: Sts2VersionDefinition?,
    record: WindowsRunningProcessRecord,
): List<PageValueItemRegistration> {
    return buildList {
        instance?.let { value ->
            add(row(language, "windows.entrypoint.row.0069", value.displayName))
        }
        version?.let { value ->
            add(row(language, "windows.entrypoint.row.0070", versionDisplayName(language, value)))
        } ?: add(
            row(language, "windows.entrypoint.row.0071", record.versionId.toString()),
        )
        add(row(language, "windows.entrypoint.row.0072", windowsRunningClientIdLabel(language, record.clientId)))
        add(row(language, "windows.entrypoint.row.0073", record.pid.toString()))
    }
}

private fun windowsRunningClientIdLabel(
    language: SupportedLanguage,
    clientId: Int,
): String {
    return if (clientId == 0) {
        localized(language, "windows.entrypoint.0167")
    } else {
        clientId.toString()
    }
}

private fun forceCloseWindowsRunningRecord(
    record: WindowsRunningProcessRecord,
    runningProcessStore: InMemoryWindowsRunningProcessStateStore,
) {
    if (!isWindowsProcessAlive(record.pid)) {
        runningProcessStore.removeRecord(record.pid)
        return
    }
    forceKillWindowsProcess(record.pid)
    runningProcessStore.removeRecord(record.pid)
}

private fun forceKillWindowsProcess(pid: Long) {
    val handle = ProcessHandle.of(pid).orElse(null) ?: return
    handle.descendants().forEach { child ->
        if (child.isAlive) {
            child.destroyForcibly()
        }
    }
    if (handle.isAlive) {
        handle.destroyForcibly()
    }
}

private fun isWindowsProcessAlive(pid: Long): Boolean {
    if (pid <= 0L) return false
    return ProcessHandle.of(pid)
        .map { handle -> handle.isAlive }
        .orElse(false)
}

private fun windowsRendererOption(
    value: String,
    selectedValue: String,
    onRendererChange: (String) -> Unit,
): PageChoiceOptionRegistration {
    return PageChoiceOptionRegistration(
        id = "sts2_windows_renderer_$value",
        label = PageTextDirect(windowsRendererLabel(value)),
        selected = normalizeWindowsRenderer(selectedValue) == value,
        onClick = { onRendererChange(value) },
    )
}

private fun normalizeWindowsRenderer(renderer: String): String {
    return when (renderer.trim().lowercase()) {
        "opengl3", "opengl" -> "opengl"
        "d3d12" -> "d3d12"
        else -> "vulkan"
    }
}

private fun windowsRendererDriverArgument(renderer: String): String {
    return when (normalizeWindowsRenderer(renderer)) {
        "opengl" -> "opengl3"
        "d3d12" -> "d3d12"
        else -> "vulkan"
    }
}

private fun windowsRendererLabel(renderer: String): String {
    return when (normalizeWindowsRenderer(renderer)) {
        "opengl" -> "OpenGL"
        "d3d12" -> "D3D12"
        else -> "Vulkan"
    }
}

private fun windowsFastMpSummaryLabel(
    language: SupportedLanguage,
    launchOptions: WindowsResolvedLaunchOptions,
): String {
    if (!launchOptions.fastMpEnabled) {
        return localized(language, "windows.entrypoint.0168")
    }
    return when (launchOptions.fastMpRole) {
        WindowsFastMpRole.HOST -> localized(language, "windows.entrypoint.0169")
        WindowsFastMpRole.CLIENT -> localized(language, "windows.entrypoint.0170")
    }
}

private fun windowsConsoleLogSummaryLabel(
    language: SupportedLanguage,
    launchOptions: WindowsResolvedLaunchOptions,
): String {
    return if (launchOptions.consoleLogEnabled) {
        localized(language, "windows.entrypoint.0171")
    } else {
        localized(language, "windows.entrypoint.0172")
    }
}

private fun windowsUserHomeDirectory(): String {
    return System.getenv("USERPROFILE")
        ?.takeIf { value -> value.isNotBlank() }
        ?: System.getProperty("user.home").orEmpty()
}

private fun windowsVersionDetailPageId(clientId: Int): String = "$WINDOWS_VERSION_MANAGER_PAGE_ID.detail.$clientId"

private fun versionDisplayName(language: SupportedLanguage, version: Sts2VersionDefinition): String {
    return version.versionName.trim().ifBlank {
        localized(language, "windows.entrypoint.version_display_fallback", listOf(version.versionId))
    }
}

private fun row(
    language: SupportedLanguage,
    labelKey: String,
    value: String,
): PageValueItemRegistration {
    return PageValueItemRegistration(
        label = PageTextDirect(localized(language, labelKey)),
        value = PageTextDirect(value.ifBlank { localized(language, "windows.entrypoint.0173") }),
    )
}
