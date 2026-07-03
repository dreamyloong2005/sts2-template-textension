package com.dreamyloong.template.sts2

import com.dreamyloong.tlauncher.sdk.extension.ExtensionStateStore
import com.dreamyloong.tlauncher.sdk.model.GameInstanceId
import java.util.Base64

private const val DefaultSpineUpdateDivisor = 2
private const val DefaultPreloadTrimEnabled = false
private const val DefaultAssetLoadingBatchSize = 8
private const val DefaultParticleScalePercent = 50
private const val DefaultGlowMode = "reduced"
private const val DefaultRenderer = "vulkan"

data class Sts2VersionDefinition(
    val clientId: Int,
    val versionId: String = clientId.toString(),
    val versionName: String,
    val gameDirectory: String,
    val saveDirectory: String,
    val modDirectory: String,
    val spineUpdateDivisor: Int = DefaultSpineUpdateDivisor,
    val preloadTrimEnabled: Boolean = DefaultPreloadTrimEnabled,
    val assetLoadingBatchSize: Int = DefaultAssetLoadingBatchSize,
    val mobileShadersEnabled: Boolean = true,
    val particleScalePercent: Int = DefaultParticleScalePercent,
    val glowMode: String = DefaultGlowMode,
    val vfxLimitEnabled: Boolean = false,
    val renderer: String = DefaultRenderer,
)

data class Sts2VersionDraft(
    val clientIdText: String = "",
    val versionName: String = "",
    val gameDirectory: String = "",
    val saveDirectory: String = "",
    val modDirectory: String = "",
    val spineUpdateDivisorText: String = DefaultSpineUpdateDivisor.toString(),
    val preloadTrimEnabled: Boolean = DefaultPreloadTrimEnabled,
    val assetLoadingBatchSizeText: String = DefaultAssetLoadingBatchSize.toString(),
    val mobileShadersEnabled: Boolean = true,
    val particleScalePercentText: String = DefaultParticleScalePercent.toString(),
    val glowMode: String = DefaultGlowMode,
    val vfxLimitEnabled: Boolean = false,
    val renderer: String = DefaultRenderer,
)

data class Sts2LaunchSettingsDraft(
    val spineUpdateDivisorText: String = DefaultSpineUpdateDivisor.toString(),
    val preloadTrimEnabled: Boolean = DefaultPreloadTrimEnabled,
    val assetLoadingBatchSizeText: String = DefaultAssetLoadingBatchSize.toString(),
    val mobileShadersEnabled: Boolean = true,
    val particleScalePercentText: String = DefaultParticleScalePercent.toString(),
    val glowMode: String = DefaultGlowMode,
    val vfxLimitEnabled: Boolean = false,
    val renderer: String = DefaultRenderer,
)

enum class Sts2VersionValidationError {
    INVALID_CLIENT_ID,
    DUPLICATE_CLIENT_ID,
    INVALID_SPINE_UPDATE_DIVISOR,
    INVALID_ASSET_LOADING_BATCH_SIZE,
    INVALID_PARTICLE_SCALE_PERCENT,
}

enum class Sts2LaunchSettingsValidationError {
    INVALID_SPINE_UPDATE_DIVISOR,
    INVALID_ASSET_LOADING_BATCH_SIZE,
    INVALID_PARTICLE_SCALE_PERCENT,
}

interface Sts2VersionStore {
    fun versions(instanceId: GameInstanceId): List<Sts2VersionDefinition>

    fun selectedClientId(instanceId: GameInstanceId): Int?

    fun selectedVersion(instanceId: GameInstanceId): Sts2VersionDefinition?

    fun createDraft(instanceId: GameInstanceId): Sts2VersionDraft

    fun suggestedCreateClientId(instanceId: GameInstanceId): Int

    fun updateCreateDraft(
        instanceId: GameInstanceId,
        transform: (Sts2VersionDraft) -> Sts2VersionDraft,
    )

    fun createValidationError(instanceId: GameInstanceId): Sts2VersionValidationError?

    fun saveCreateDraft(instanceId: GameInstanceId): Boolean

    fun editDraft(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Sts2VersionDraft?

    fun updateEditDraft(
        instanceId: GameInstanceId,
        clientId: Int,
        transform: (Sts2VersionDraft) -> Sts2VersionDraft,
    )

    fun editValidationError(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Sts2VersionValidationError?

    fun saveEditDraft(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Boolean

    fun launchSettingsDraft(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Sts2LaunchSettingsDraft?

    fun updateLaunchSettingsDraft(
        instanceId: GameInstanceId,
        clientId: Int,
        transform: (Sts2LaunchSettingsDraft) -> Sts2LaunchSettingsDraft,
    )

    fun launchSettingsValidationError(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Sts2LaunchSettingsValidationError?

    fun saveLaunchSettingsDraft(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Boolean

    fun selectVersion(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Boolean

    fun deleteVersion(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Boolean
}

class PersistentSts2VersionStore(
    private val stateStore: ExtensionStateStore,
) : Sts2VersionStore {
    private val createDrafts = mutableMapOf<GameInstanceId, Sts2VersionDraft>()
    private val editDrafts = mutableMapOf<VersionDraftKey, Sts2VersionDraft>()
    private val launchSettingsDrafts = mutableMapOf<VersionDraftKey, Sts2LaunchSettingsDraft>()

    override fun versions(instanceId: GameInstanceId): List<Sts2VersionDefinition> {
        return persistedState(instanceId).versions.sortedBy { version -> version.clientId }
    }

    override fun selectedClientId(instanceId: GameInstanceId): Int? = persistedState(instanceId).selectedClientId

    override fun selectedVersion(instanceId: GameInstanceId): Sts2VersionDefinition? {
        val selectedClientId = selectedClientId(instanceId) ?: return null
        return version(instanceId, selectedClientId)
    }

    override fun createDraft(instanceId: GameInstanceId): Sts2VersionDraft {
        return createDrafts.getOrPut(instanceId) { Sts2VersionDraft() }
    }

    override fun suggestedCreateClientId(instanceId: GameInstanceId): Int {
        return nextAvailableClientId(versions(instanceId))
    }

    override fun updateCreateDraft(
        instanceId: GameInstanceId,
        transform: (Sts2VersionDraft) -> Sts2VersionDraft,
    ) {
        createDrafts[instanceId] = transform(createDraft(instanceId))
    }

    override fun createValidationError(instanceId: GameInstanceId): Sts2VersionValidationError? {
        return validationError(
            draft = createDraft(instanceId),
            existingVersions = versions(instanceId),
            originalClientId = null,
            allowBlankClientId = true,
        )
    }

    override fun saveCreateDraft(instanceId: GameInstanceId): Boolean {
        val current = persistedState(instanceId)
        val candidate = toCandidate(
            draft = createDraft(instanceId),
            existingVersions = current.versions,
            originalClientId = null,
            allowBlankClientId = true,
        ) ?: return false
        persistState(
            instanceId = instanceId,
            state = PersistedSts2InstanceState(
                selectedClientId = current.selectedClientId ?: candidate.clientId,
                versions = current.versions
                    .filterNot { version -> version.clientId == candidate.clientId } + candidate,
            ),
        )
        createDrafts.remove(instanceId)
        return true
    }

    override fun editDraft(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Sts2VersionDraft? {
        val version = version(instanceId, clientId) ?: return null
        val key = VersionDraftKey(instanceId, clientId)
        return editDrafts.getOrPut(key) { version.toDraft() }
    }

    override fun updateEditDraft(
        instanceId: GameInstanceId,
        clientId: Int,
        transform: (Sts2VersionDraft) -> Sts2VersionDraft,
    ) {
        val current = editDraft(instanceId, clientId) ?: return
        editDrafts[VersionDraftKey(instanceId, clientId)] = transform(current)
    }

    override fun editValidationError(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Sts2VersionValidationError? {
        val draft = editDraft(instanceId, clientId) ?: return null
        return validationError(
            draft = draft,
            existingVersions = versions(instanceId),
            originalClientId = clientId,
            allowBlankClientId = false,
        )
    }

    override fun saveEditDraft(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Boolean {
        val current = persistedState(instanceId)
        if (current.versions.none { version -> version.clientId == clientId }) {
            return false
        }
        val candidate = toCandidate(
            draft = editDraft(instanceId, clientId) ?: return false,
            existingVersions = current.versions,
            originalClientId = clientId,
            allowBlankClientId = false,
        ) ?: return false
        persistState(
            instanceId = instanceId,
            state = PersistedSts2InstanceState(
                selectedClientId = if (current.selectedClientId == clientId) candidate.clientId else current.selectedClientId,
                versions = current.versions.map { version ->
                    if (version.clientId == clientId) candidate else version
                },
            ),
        )
        editDrafts.remove(VersionDraftKey(instanceId, clientId))
        launchSettingsDrafts.remove(VersionDraftKey(instanceId, clientId))
        return true
    }

    override fun launchSettingsDraft(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Sts2LaunchSettingsDraft? {
        val version = version(instanceId, clientId) ?: return null
        val key = VersionDraftKey(instanceId, clientId)
        return launchSettingsDrafts.getOrPut(key) { version.toLaunchSettingsDraft() }
    }

    override fun updateLaunchSettingsDraft(
        instanceId: GameInstanceId,
        clientId: Int,
        transform: (Sts2LaunchSettingsDraft) -> Sts2LaunchSettingsDraft,
    ) {
        val current = launchSettingsDraft(instanceId, clientId) ?: return
        launchSettingsDrafts[VersionDraftKey(instanceId, clientId)] = transform(current)
    }

    override fun launchSettingsValidationError(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Sts2LaunchSettingsValidationError? {
        val draft = launchSettingsDraft(instanceId, clientId) ?: return null
        return launchSettingsValidationError(draft)
    }

    override fun saveLaunchSettingsDraft(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Boolean {
        val current = persistedState(instanceId)
        if (current.versions.none { version -> version.clientId == clientId }) {
            return false
        }
        val resolvedLaunchSettings = resolveLaunchSettings(
            launchSettingsDraft(instanceId, clientId) ?: return false,
        ) ?: return false
        persistState(
            instanceId = instanceId,
            state = current.copy(
                versions = current.versions.map { version ->
                    if (version.clientId == clientId) {
                        version.copy(
                            spineUpdateDivisor = resolvedLaunchSettings.spineUpdateDivisor,
                            preloadTrimEnabled = resolvedLaunchSettings.preloadTrimEnabled,
                            assetLoadingBatchSize = resolvedLaunchSettings.assetLoadingBatchSize,
                            mobileShadersEnabled = resolvedLaunchSettings.mobileShadersEnabled,
                            particleScalePercent = resolvedLaunchSettings.particleScalePercent,
                            glowMode = resolvedLaunchSettings.glowMode,
                            vfxLimitEnabled = resolvedLaunchSettings.vfxLimitEnabled,
                            renderer = resolvedLaunchSettings.renderer,
                        )
                    } else {
                        version
                    }
                },
            ),
        )
        launchSettingsDrafts.remove(VersionDraftKey(instanceId, clientId))
        return true
    }

    override fun selectVersion(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Boolean {
        val current = persistedState(instanceId)
        if (current.versions.none { version -> version.clientId == clientId }) return false
        persistState(instanceId, current.copy(selectedClientId = clientId))
        return true
    }

    override fun deleteVersion(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Boolean {
        val current = persistedState(instanceId)
        if (current.versions.none { version -> version.clientId == clientId }) return false
        val remainingVersions = current.versions.filterNot { version -> version.clientId == clientId }
        persistState(
            instanceId = instanceId,
            state = PersistedSts2InstanceState(
                selectedClientId = if (current.selectedClientId == clientId) {
                    remainingVersions.firstOrNull()?.clientId
                } else {
                    current.selectedClientId
                },
                versions = remainingVersions,
            ),
        )
        editDrafts.remove(VersionDraftKey(instanceId, clientId))
        launchSettingsDrafts.remove(VersionDraftKey(instanceId, clientId))
        return true
    }

    private fun persistedState(instanceId: GameInstanceId): PersistedSts2InstanceState {
        val lines = stateStore.read(stateKey(instanceId))
            ?.lineSequence()
            ?.filter { line -> line.isNotBlank() }
            ?.toList()
            .orEmpty()
        val selectedClientId = lines.firstOrNull { line -> line.startsWith("selected\t") }
            ?.substringAfter('\t')
            ?.toIntOrNull()
        val versions = lines
            .filter { line -> line.startsWith("version\t") }
            .mapNotNull(::decodeVersionLine)
        return PersistedSts2InstanceState(
            selectedClientId = selectedClientId,
            versions = versions,
        )
    }

    private fun persistState(
        instanceId: GameInstanceId,
        state: PersistedSts2InstanceState,
    ) {
        if (state.versions.isEmpty()) {
            stateStore.write(stateKey(instanceId), null)
            return
        }
        val lines = buildList {
            state.selectedClientId?.let { clientId -> add("selected\t$clientId") }
            state.versions.sortedBy { version -> version.clientId }.forEach { version ->
                add(version.toLine())
            }
        }
        stateStore.write(stateKey(instanceId), lines.joinToString("\n"))
    }

    private fun version(
        instanceId: GameInstanceId,
        clientId: Int,
    ): Sts2VersionDefinition? {
        return versions(instanceId).firstOrNull { version -> version.clientId == clientId }
    }

    private fun validationError(
        draft: Sts2VersionDraft,
        existingVersions: List<Sts2VersionDefinition>,
        originalClientId: Int?,
        allowBlankClientId: Boolean,
    ): Sts2VersionValidationError? {
        val versionId = resolveVersionId(
            versionIdText = draft.clientIdText,
            existingVersions = existingVersions,
            allowBlankClientId = allowBlankClientId,
        ) ?: return Sts2VersionValidationError.INVALID_CLIENT_ID
        val duplicate = existingVersions.any { version ->
            version.versionId == versionId && version.clientId != originalClientId
        }
        if (duplicate) {
            return Sts2VersionValidationError.DUPLICATE_CLIENT_ID
        }
        when (launchSettingsValidationError(draft.toLaunchSettingsDraft())) {
            Sts2LaunchSettingsValidationError.INVALID_SPINE_UPDATE_DIVISOR -> {
                return Sts2VersionValidationError.INVALID_SPINE_UPDATE_DIVISOR
            }

            Sts2LaunchSettingsValidationError.INVALID_ASSET_LOADING_BATCH_SIZE -> {
                return Sts2VersionValidationError.INVALID_ASSET_LOADING_BATCH_SIZE
            }

            Sts2LaunchSettingsValidationError.INVALID_PARTICLE_SCALE_PERCENT -> {
                return Sts2VersionValidationError.INVALID_PARTICLE_SCALE_PERCENT
            }

            null -> Unit
        }
        return null
    }

    private fun toCandidate(
        draft: Sts2VersionDraft,
        existingVersions: List<Sts2VersionDefinition>,
        originalClientId: Int?,
        allowBlankClientId: Boolean,
    ): Sts2VersionDefinition? {
        if (validationError(draft, existingVersions, originalClientId, allowBlankClientId) != null) {
            return null
        }
        val resolvedVersionId = resolveVersionId(
            versionIdText = draft.clientIdText,
            existingVersions = existingVersions,
            allowBlankClientId = allowBlankClientId,
        ) ?: return null
        val clientId = originalClientId ?: nextAvailableClientId(existingVersions)
        val resolvedLaunchSettings = resolveLaunchSettings(draft.toLaunchSettingsDraft()) ?: return null
        val normalizedDraft = if (originalClientId == null) {
            draft.withAndroidDefaultDirectories(resolvedVersionId)
        } else {
            draft
        }
        return Sts2VersionDefinition(
            clientId = clientId,
            versionId = resolvedVersionId,
            versionName = normalizedDraft.versionName.trim(),
            gameDirectory = normalizedDraft.gameDirectory.trim(),
            saveDirectory = normalizedDraft.saveDirectory.trim(),
            modDirectory = normalizedDraft.modDirectory.trim(),
            spineUpdateDivisor = resolvedLaunchSettings.spineUpdateDivisor,
            preloadTrimEnabled = resolvedLaunchSettings.preloadTrimEnabled,
            assetLoadingBatchSize = resolvedLaunchSettings.assetLoadingBatchSize,
            mobileShadersEnabled = resolvedLaunchSettings.mobileShadersEnabled,
            particleScalePercent = resolvedLaunchSettings.particleScalePercent,
            glowMode = resolvedLaunchSettings.glowMode,
            vfxLimitEnabled = resolvedLaunchSettings.vfxLimitEnabled,
            renderer = resolvedLaunchSettings.renderer,
        )
    }

    private fun stateKey(instanceId: GameInstanceId): String = "sts2.versions.${instanceId.value}"

    private fun resolveVersionId(
        versionIdText: String,
        existingVersions: List<Sts2VersionDefinition>,
        allowBlankClientId: Boolean,
    ): String? {
        val normalizedVersionId = versionIdText.trim()
        if (normalizedVersionId.isBlank()) {
            return if (allowBlankClientId) nextAvailableClientId(existingVersions).toString() else null
        }
        return normalizedVersionId
    }

    private fun nextAvailableClientId(existingVersions: List<Sts2VersionDefinition>): Int {
        val usedClientIds = existingVersions
            .map { version -> version.clientId }
            .filter { clientId -> clientId > 0 }
            .toSet()
        return generateSequence(1) { clientId -> clientId + 1 }
            .first { clientId -> clientId !in usedClientIds }
    }
}

private data class PersistedSts2InstanceState(
    val selectedClientId: Int? = null,
    val versions: List<Sts2VersionDefinition> = emptyList(),
)

private data class VersionDraftKey(
    val instanceId: GameInstanceId,
    val clientId: Int,
)

private data class ResolvedSts2LaunchSettings(
    val spineUpdateDivisor: Int,
    val preloadTrimEnabled: Boolean,
    val assetLoadingBatchSize: Int,
    val mobileShadersEnabled: Boolean,
    val particleScalePercent: Int,
    val glowMode: String,
    val vfxLimitEnabled: Boolean,
    val renderer: String,
)

private fun Sts2VersionDraft.withAndroidDefaultDirectories(versionId: String): Sts2VersionDraft {
    val normalizedVersionPath = versionId.toAndroidPathSegment()
    return copy(
        gameDirectory = gameDirectory.ifBlank { "/storage/emulated/0/TLauncher/.sts2/versions/$normalizedVersionPath" },
        saveDirectory = saveDirectory.ifBlank { "/storage/emulated/0/TLauncher/.sts2/saves/$normalizedVersionPath" },
        modDirectory = modDirectory.ifBlank { "/storage/emulated/0/TLauncher/.sts2/mods/$normalizedVersionPath" },
    )
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
        renderer = renderer,
    )
}

private fun Sts2VersionDraft.toLaunchSettingsDraft(): Sts2LaunchSettingsDraft {
    return Sts2LaunchSettingsDraft(
        spineUpdateDivisorText = spineUpdateDivisorText,
        preloadTrimEnabled = preloadTrimEnabled,
        assetLoadingBatchSizeText = assetLoadingBatchSizeText,
        mobileShadersEnabled = mobileShadersEnabled,
        particleScalePercentText = particleScalePercentText,
        glowMode = glowMode,
        vfxLimitEnabled = vfxLimitEnabled,
        renderer = renderer,
    )
}

private fun Sts2VersionDefinition.toLine(): String {
    return listOf(
        "version",
        clientId.toString(),
        encode(versionId),
        encode(versionName),
        encode(gameDirectory),
        encode(saveDirectory),
        encode(modDirectory),
        encode(spineUpdateDivisor.toString()),
        encode(preloadTrimEnabled.toString()),
        encode(assetLoadingBatchSize.toString()),
        encode(mobileShadersEnabled.toString()),
        encode(particleScalePercent.toString()),
        encode(glowMode),
        encode(vfxLimitEnabled.toString()),
        encode(renderer),
    ).joinToString("\t")
}

private fun decodeVersionLine(line: String): Sts2VersionDefinition? {
    val parts = line.split('\t')
    if (parts.size != 15) return null
    val clientId = parts[1].toIntOrNull() ?: return null
    return Sts2VersionDefinition(
        clientId = clientId,
        versionId = decode(parts[2]),
        versionName = decode(parts[3]),
        gameDirectory = decode(parts[4]),
        saveDirectory = decode(parts[5]),
        modDirectory = decode(parts[6]),
        spineUpdateDivisor = decode(parts[7]).toIntOrNull()?.takeIf { it >= 1 } ?: return null,
        preloadTrimEnabled = decode(parts[8]).toBooleanStrictOrNull() ?: return null,
        assetLoadingBatchSize = decode(parts[9]).toIntOrNull()?.takeIf { it >= 0 } ?: return null,
        mobileShadersEnabled = decode(parts[10]).toBooleanStrictOrNull() ?: return null,
        particleScalePercent = decode(parts[11]).toIntOrNull()?.takeIf { it in 0..100 } ?: return null,
        glowMode = normalizeGlowMode(decode(parts[12])),
        vfxLimitEnabled = decode(parts[13]).toBooleanStrictOrNull() ?: return null,
        renderer = normalizeRenderer(decode(parts[14])),
    )
}

private fun String.toAndroidPathSegment(): String {
    val normalized = trim().replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
    return normalized.ifBlank { "1" }
}

private fun launchSettingsValidationError(draft: Sts2LaunchSettingsDraft): Sts2LaunchSettingsValidationError? {
    if (parseLaunchSettingsInt(draft.spineUpdateDivisorText, DefaultSpineUpdateDivisor, minValue = 1) == null) {
        return Sts2LaunchSettingsValidationError.INVALID_SPINE_UPDATE_DIVISOR
    }
    if (parseLaunchSettingsInt(draft.assetLoadingBatchSizeText, DefaultAssetLoadingBatchSize, minValue = 0) == null) {
        return Sts2LaunchSettingsValidationError.INVALID_ASSET_LOADING_BATCH_SIZE
    }
    if (parseLaunchSettingsInt(draft.particleScalePercentText, DefaultParticleScalePercent, minValue = 0, maxValue = 100) == null) {
        return Sts2LaunchSettingsValidationError.INVALID_PARTICLE_SCALE_PERCENT
    }
    return null
}

private fun resolveLaunchSettings(draft: Sts2LaunchSettingsDraft): ResolvedSts2LaunchSettings? {
    if (launchSettingsValidationError(draft) != null) {
        return null
    }
    return ResolvedSts2LaunchSettings(
        spineUpdateDivisor = parseLaunchSettingsInt(draft.spineUpdateDivisorText, DefaultSpineUpdateDivisor, minValue = 1)
            ?: return null,
        preloadTrimEnabled = draft.preloadTrimEnabled,
        assetLoadingBatchSize = parseLaunchSettingsInt(draft.assetLoadingBatchSizeText, DefaultAssetLoadingBatchSize, minValue = 0)
            ?: return null,
        mobileShadersEnabled = draft.mobileShadersEnabled,
        particleScalePercent = parseLaunchSettingsInt(
            draft.particleScalePercentText,
            DefaultParticleScalePercent,
            minValue = 0,
            maxValue = 100,
        ) ?: return null,
        glowMode = normalizeGlowMode(draft.glowMode),
        vfxLimitEnabled = draft.vfxLimitEnabled,
        renderer = normalizeRenderer(draft.renderer),
    )
}

private fun parseLaunchSettingsInt(
    text: String,
    defaultValue: Int,
    minValue: Int,
    maxValue: Int? = null,
): Int? {
    val normalized = text.trim()
    if (normalized.isBlank()) {
        return defaultValue
    }
    val value = normalized.toIntOrNull() ?: return null
    if (value < minValue) {
        return null
    }
    if (maxValue != null && value > maxValue) {
        return null
    }
    return value
}

private fun normalizeGlowMode(glowMode: String): String {
    return when (glowMode.trim().lowercase()) {
        "full" -> "full"
        "off" -> "off"
        "minimal" -> "minimal"
        "reduced" -> "reduced"
        else -> DefaultGlowMode
    }
}

private fun normalizeRenderer(renderer: String): String {
    return when (renderer.trim().lowercase()) {
        "opengl" -> "opengl"
        "d3d12" -> "d3d12"
        else -> DefaultRenderer
    }
}

private fun encode(value: String): String {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))
}

private fun decode(value: String): String {
    return String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}
