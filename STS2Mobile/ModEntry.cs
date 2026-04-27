using System;
using System.Runtime.InteropServices;
using Godot;
using Godot.Bridge;
using Godot.NativeInterop;
using HarmonyLib;
using STS2Mobile.Patches;

namespace STS2Mobile;

// Entry point for the mobile patcher. Bootstraps GodotSharp and applies Harmony
// patches inside a launcher-owned game process.
public static class ModEntry
{
    private static Harmony _harmony;
    private static bool _applied = false;

    // Bootstraps GodotSharp by setting up DLL import resolver, native interop,
    // and managed callbacks. Called from gd_mono.cpp before Apply().
    [UnmanagedCallersOnly]
    public static int InitializeGodotSharp(
        IntPtr godotDllHandle,
        IntPtr outManagedCallbacks,
        IntPtr unmanagedCallbacks,
        int unmanagedCallbacksSize
    )
    {
        try
        {
            DllImportResolver dllImportResolver = new GodotDllImportResolver(
                godotDllHandle
            ).OnResolveDllImport;
            var coreApiAssembly = typeof(GodotObject).Assembly;
            NativeLibrary.SetDllImportResolver(coreApiAssembly, dllImportResolver);

            NativeFuncs.Initialize(unmanagedCallbacks, unmanagedCallbacksSize);
            ManagedCallbacks.Create(outManagedCallbacks);

            Console.Error.WriteLine("[STS2Mobile] GodotSharp bootstrapped successfully");
            return 1;
        }
        catch (Exception e)
        {
            Console.Error.WriteLine($"[STS2Mobile] GodotSharp bootstrap failed: {e}");
            return 0;
        }
    }

    [UnmanagedCallersOnly]
    public static void Apply()
    {
        if (_applied)
            return;
        _applied = true;

        PatchHelper.Log("Initializing STS2Mobile...");
        if (!string.IsNullOrWhiteSpace(PatchHelper.CurrentLogPath))
            PatchHelper.Log($"File log path: {PatchHelper.CurrentLogPath}");

        _harmony = new Harmony("com.STS2Mobile");

        try
        {
            int coreFailures = ApplyPatchGroup(
                "Core",
                (nameof(ModelDbInitPatch), ModelDbInitPatch.Apply),
                (nameof(PlatformPatches), PlatformPatches.Apply),
                (nameof(SaveStorePatches), SaveStorePatches.Apply),
                (nameof(SettingsPatches), SettingsPatches.Apply),
                (nameof(UiScalePatches), UiScalePatches.Apply),
                (nameof(MobileLayoutPatches), MobileLayoutPatches.Apply),
                (nameof(EventLayoutPatches), EventLayoutPatches.Apply),
                (nameof(MerchantLayoutPatches), MerchantLayoutPatches.Apply),
                (nameof(AppLifecyclePatches), AppLifecyclePatches.Apply),
                (nameof(TouchInputPatches), TouchInputPatches.Apply)
            );

            int gameplayFailures = ApplyPatchGroup(
                "Gameplay",
                (nameof(CardRewardPatches), CardRewardPatches.Apply),
                (nameof(EarlyAccessDisclaimerPatches), EarlyAccessDisclaimerPatches.Apply),
                (nameof(CombatBackgroundPatches), CombatBackgroundPatches.Apply),
                (nameof(LanMultiplayerPatcher), LanMultiplayerPatcher.Apply),
                (nameof(ModLoaderPatches), ModLoaderPatches.Apply)
            );

            int startupFailures = ApplyPatchGroup(
                "Startup",
                (nameof(LauncherPatches), LauncherPatches.Apply)
            );

            int performanceFailures = ApplyPatchGroup(
                "Performance",
                (nameof(MobileGraphicsPatches), MobileGraphicsPatches.Apply),
                (nameof(SpinePerformancePatches), SpinePerformancePatches.Apply),
                (nameof(PreloadTrimPatch), PreloadTrimPatch.Apply),
                (nameof(AssetLoadingSessionPatch), AssetLoadingSessionPatch.Apply)
            );

            int diagnosticFailures = ApplyPatchGroup(
                "Diagnostic",
                (nameof(SaveDiagnosticPatches), SaveDiagnosticPatches.Apply)
            );

            PatchHelper.Log(
                $"Patch apply complete. Core failures={coreFailures}, "
                    + $"Gameplay failures={gameplayFailures}, "
                    + $"Startup failures={startupFailures}, "
                    + $"Performance failures={performanceFailures}, "
                    + $"Diagnostic failures={diagnosticFailures}"
            );
        }
        catch (Exception ex)
        {
            PatchHelper.Log($"Patch bootstrap failed: {ex}");
        }
    }

    private static int ApplyPatchGroup(
        string groupName,
        params (string Name, Action<Harmony> ApplyPatch)[] patches
    )
    {
        int failures = 0;

        PatchHelper.Log($"Applying {groupName} patches ({patches.Length})...");

        foreach (var patch in patches)
        {
            try
            {
                patch.ApplyPatch(_harmony);
            }
            catch (Exception ex)
            {
                failures++;
                PatchHelper.Log($"{groupName} patch {patch.Name} failed: {ex.Message}");
            }
        }

        PatchHelper.Log(
            $"{groupName} patches finished: {patches.Length - failures}/{patches.Length} applied"
        );

        return failures;
    }
}

