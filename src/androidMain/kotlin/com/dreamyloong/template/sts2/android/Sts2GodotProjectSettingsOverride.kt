package com.dreamyloong.template.sts2.android

import android.util.Log
import org.json.JSONObject
import java.io.File

private const val TAG = "Sts2GodotProjectSettingsOverride"
private const val GODOT_OVERRIDE_FILE_NAME = "override.cfg"
private const val SHADER_CACHE_OVERRIDE_BEGIN =
    "; BEGIN TLauncher shader cache override"
private const val SHADER_CACHE_OVERRIDE_END =
    "; END TLauncher shader cache override"
private const val GODOT_CUSTOM_USER_DIR_PREFIX = "TLauncher_STS2_"

private val shaderCacheDisabledOverride = """
    |$SHADER_CACHE_OVERRIDE_BEGIN
    |[rendering]
    |shader_compiler/shader_cache/enabled=false
    |rendering_device/pipeline_cache/enable=false
    |$SHADER_CACHE_OVERRIDE_END
""".trimMargin()

internal fun parseShaderCacheEnabled(launchContextJson: String): Boolean {
    return runCatching {
        JSONObject(launchContextJson).optBoolean("shaderCacheEnabled", false)
    }.getOrElse { failure ->
        Log.w(TAG, "Failed to parse shader cache launch option: ${failure.message}")
        false
    }
}

internal fun syncShaderCacheProjectSettingsOverride(
    projectDirectory: File,
    shaderCacheEnabled: Boolean,
) {
    val overrideFile = projectDirectory.resolve(GODOT_OVERRIDE_FILE_NAME)
    runCatching {
        val existing = if (overrideFile.isFile) overrideFile.readText() else ""
        val withoutManagedBlock = removeShaderCacheOverrideBlock(existing).trimEnd()
        val managedBlock = if (shaderCacheEnabled) {
            shaderCacheEnabledOverride(projectDirectory)
        } else {
            shaderCacheDisabledOverride
        }
        val next = buildString {
            if (withoutManagedBlock.isNotBlank()) {
                append(withoutManagedBlock)
                append("\n\n")
            }
            append(managedBlock)
            append('\n')
        }
        if (next.trimEnd() == existing.trimEnd()) {
            Log.i(TAG, "Shader cache ProjectSettings override is already current at ${overrideFile.absolutePath}")
        } else {
            overrideFile.parentFile?.mkdirs()
            overrideFile.writeText(next)
            if (shaderCacheEnabled) {
                Log.i(TAG, "Pinned Godot shader cache user dir through ${overrideFile.absolutePath}")
            } else {
                Log.i(TAG, "Disabled Godot shader and pipeline caches through ${overrideFile.absolutePath}")
            }
        }
    }.onFailure { failure ->
        Log.w(TAG, "Failed to sync shader cache ProjectSettings override at ${overrideFile.absolutePath}: ${failure.message}")
    }
}

private fun shaderCacheEnabledOverride(projectDirectory: File): String {
    val userDirName = "$GODOT_CUSTOM_USER_DIR_PREFIX${projectDirectory.name.toSafeGodotUserDirName()}"
    return """
        |$SHADER_CACHE_OVERRIDE_BEGIN
        |[application]
        |config/use_custom_user_dir=true
        |config/custom_user_dir_name="${userDirName.toGodotConfigStringContent()}"
        |$SHADER_CACHE_OVERRIDE_END
    """.trimMargin()
}

private fun removeShaderCacheOverrideBlock(text: String): String {
    val begin = text.indexOf(SHADER_CACHE_OVERRIDE_BEGIN)
    if (begin < 0) return text
    val end = text.indexOf(SHADER_CACHE_OVERRIDE_END, begin)
    if (end < 0) return text
    val afterEnd = end + SHADER_CACHE_OVERRIDE_END.length
    return text.removeRange(begin, afterEnd).replace(Regex("\\n{3,}"), "\n\n")
}

private fun String.toSafeGodotUserDirName(): String {
    return map { character ->
        if (character.isLetterOrDigit() || character == '.' || character == '-' || character == '_') {
            character
        } else {
            '_'
        }
    }.joinToString("").ifBlank { "shader_cache" }
}

private fun String.toGodotConfigStringContent(): String {
    return buildString {
        this@toGodotConfigStringContent.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(character)
            }
        }
    }
}
