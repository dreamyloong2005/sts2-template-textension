package com.dreamyloong.template.sts2.android

import android.os.Bundle
import android.util.Log
import com.dreamyloong.tlauncher.sdk.platform.AndroidExtensionHostController
import org.json.JSONObject
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotFragment
import java.io.File

class Sts2GodotHostFragment : GodotFragment() {
    override fun getCommandLine(): MutableList<String> {
        val commands = super.getCommandLine().toMutableList()
        val projectDirectory = File(
            requireArguments().getString(ARG_PROJECT_DIRECTORY_PATH).orEmpty(),
        )
        val packFile = File(requireArguments().getString(ARG_PACK_FILE_PATH).orEmpty())
        val launchContextJson = requireArguments().getString(ARG_LAUNCH_CONTEXT_JSON).orEmpty()
        val renderer = parseRenderer(launchContextJson)
        commands += listOf("--path", projectDirectory.absolutePath)
        if (renderer == "opengl") {
            commands += listOf(
                "--rendering-method",
                "gl_compatibility",
                "--rendering-driver",
                "opengl3",
            )
        } else {
            commands += listOf(
                "--rendering-method",
                "forward_plus",
                "--rendering-driver",
                "vulkan",
            )
        }
        Log.i(TAG, "Using Godot renderer $renderer")
        if (packFile.isFile) {
            commands += listOf("--main-pack", packFile.absolutePath)
            Log.i(TAG, "Loading Godot pack ${packFile.absolutePath}")
        } else {
            Log.e(TAG, "Godot pack is missing: ${packFile.absolutePath}")
        }
        return commands
    }

    override fun onBackPressed() {
        hostController()?.requestCloseHostedRuntime()
    }

    override fun onGodotSetupCompleted() {
        super.onGodotSetupCompleted()
        Log.i(TAG, "Godot setup completed")
    }

    override fun onGodotMainLoopStarted() {
        super.onGodotMainLoopStarted()
        Log.i(TAG, "Godot main loop started")
    }

    override fun onGodotForceQuit(instance: Godot) {
        super.onGodotForceQuit(instance)
        Log.i(TAG, "Godot requested force quit")
        hostController()?.closeHostedRuntimeAndReturnToLauncher()
    }

    override fun onGodotRestartRequested(instance: Godot) {
        super.onGodotRestartRequested(instance)
        Log.i(TAG, "Godot requested restart")
        hostController()?.restartHostedRuntime()
    }

    private fun hostController(): AndroidExtensionHostController? {
        return (activity as? AndroidExtensionHostController).also { controller ->
            if (controller == null) {
                Log.w(TAG, "Host activity does not implement AndroidExtensionHostController")
            }
        }
    }

    companion object {
        private const val TAG = "Sts2GodotHostFragment"
        private const val ARG_PROJECT_DIRECTORY_PATH =
            "com.dreamyloong.template.sts2.android.PROJECT_DIRECTORY_PATH"
        private const val ARG_PACK_FILE_PATH =
            "com.dreamyloong.template.sts2.android.PACK_FILE_PATH"
        private const val ARG_LAUNCH_CONTEXT_JSON =
            "com.dreamyloong.template.sts2.android.LAUNCH_CONTEXT_JSON"

        fun newInstance(
            projectDirectoryPath: String,
            packFilePath: String,
            launchContextJson: String,
        ): Sts2GodotHostFragment {
            return Sts2GodotHostFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROJECT_DIRECTORY_PATH, projectDirectoryPath)
                    putString(ARG_PACK_FILE_PATH, packFilePath)
                    putString(ARG_LAUNCH_CONTEXT_JSON, launchContextJson)
                }
            }
        }

        private fun parseRenderer(launchContextJson: String): String {
            return runCatching {
                normalizeRenderer(JSONObject(launchContextJson).optString("renderer", "vulkan"))
            }.getOrElse { failure ->
                Log.w(TAG, "Failed to parse launch context renderer: ${failure.message}")
                "vulkan"
            }
        }

        private fun normalizeRenderer(renderer: String): String {
            return when (renderer.trim().lowercase()) {
                "opengl" -> "opengl"
                else -> "vulkan"
            }
        }
    }
}
