using System;
using System.Reflection;
using System.Threading.Tasks;
using Godot;
using HarmonyLib;
using MegaCrit.Sts2.Core.Nodes;
using STS2Mobile.Game.Warmup;
using STS2Mobile.Patches.Shaders;

namespace STS2Mobile.Patches;

// Lightweight startup hook. It fixes shader patch timing without reimplementing
// launcher-side readiness checks or warmup UI flows.
public static class LauncherPatches
{
    public static void Apply(Harmony harmony)
    {
        PatchHelper.PatchCritical(
            harmony,
            typeof(NGame),
            "GameStartupWrapper",
            prefix: PatchHelper.Method(typeof(LauncherPatches), nameof(GameStartupWrapperPrefix))
        );
    }

    public static bool GameStartupWrapperPrefix(object __instance, ref Task __result)
    {
        __result = RunStartupPipeline(__instance);
        return false;
    }

    private static async Task RunStartupPipeline(object game)
    {
        if (AppPaths.IsShaderWarmupLaunch)
        {
            await RunShaderWarmupPipeline(game);
            return;
        }

        try
        {
            ShaderPatchLoader.ApplyAll();
        }
        catch (Exception ex)
        {
            PatchHelper.Log($"Launcher startup shader apply failed: {ex.Message}");
        }

        MethodInfo gameStartupMethod = game
            .GetType()
            .GetMethod("GameStartup", BindingFlags.Instance | BindingFlags.NonPublic);
        if (gameStartupMethod == null)
            throw new InvalidOperationException("Launcher startup hook could not find GameStartup");

        try
        {
            object result = gameStartupMethod.Invoke(game, null);
            if (result is Task startupTask)
            {
                await startupTask;
                return;
            }

            PatchHelper.Log("Launcher startup hook: GameStartup did not return Task");
        }
        catch (TargetInvocationException ex)
        {
            Exception inner = ex.InnerException ?? ex;
            PatchHelper.Log($"Launcher startup failed: {inner.Message}");
            throw inner;
        }
    }

    private static async Task RunShaderWarmupPipeline(object game)
    {
        if (game is not Node gameNode)
        {
            PatchHelper.Log("Shader warmup launch skipped: NGame instance is not a Godot Node");
            return;
        }

        var warmupScreen = new ShaderWarmupScreen();
        gameNode.AddChild(warmupScreen, forceReadableName: false, Node.InternalMode.Disabled);
        warmupScreen.Initialize();
        await warmupScreen.WaitForCompletion();
        warmupScreen.QueueFree();
    }
}
