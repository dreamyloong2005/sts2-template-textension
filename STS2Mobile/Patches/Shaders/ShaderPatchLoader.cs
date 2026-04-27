using System;
using System.Collections.Generic;
using Godot;

namespace STS2Mobile.Patches.Shaders;

public static class ShaderPatchLoader
{
    public const int PatchSetVersion = 1;

    private static readonly List<(string Path, string Code, string Name)> Patches = new();
    private static readonly HashSet<string> RegisteredPaths = new(StringComparer.Ordinal);

    private static bool _registered;
    private static bool _applied;

    public static void Register(string resourcePath, string patchedCode, string name)
    {
        if (string.IsNullOrWhiteSpace(resourcePath) || string.IsNullOrWhiteSpace(patchedCode))
            return;

        if (!RegisteredPaths.Add(resourcePath))
            return;

        Patches.Add((resourcePath, patchedCode, name));
    }

    public static void RegisterAll()
    {
        if (_registered)
            return;

        _registered = true;

        BlurPatch.Register();
        CanvasGroupMaskBlurPatch.Register();
        HsvPatch.Register();
        RadialBlurPatch.Register();
        DarkBlurPatch.Register();
        DoomOverlayPatch.Register();
        WigglePatch.Register();
        OverlayBlendPatch.Register();
        ScryRevealPatch.Register();
        ScreenDistortionOutwardPatch.Register();
        ScreamDistortionPolarPatch.Register();
        InsatiableSandFallPatch.Register();
        WaterReflectionPostPatch.Register();
        PotionLiquidOverlayPatch.Register();

        PatchHelper.Log($"Shader patch registry initialized with {Patches.Count} patch(es)");
    }

    public static void ApplyAll()
    {
        if (_applied)
            return;

        _applied = true;

        if (!AppPaths.MobileShadersEnabled)
        {
            PatchHelper.Log("Mobile shaders disabled by launch context");
            return;
        }

        int appliedCount = 0;
        int failureCount = 0;

        foreach (var patch in Patches)
        {
            try
            {
                if (!ResourceLoader.Exists(patch.Path))
                {
                    PatchHelper.Log($"Shader patch not found: {patch.Path}");
                    failureCount++;
                    continue;
                }

                Shader shader = ResourceLoader.Load<Shader>(
                    patch.Path,
                    null,
                    ResourceLoader.CacheMode.Reuse
                );
                if (shader == null)
                {
                    PatchHelper.Log($"Shader patch load failed: {patch.Path}");
                    failureCount++;
                    continue;
                }

                shader.Code = patch.Code;
                appliedCount++;
            }
            catch (Exception ex)
            {
                PatchHelper.Log($"Shader patch {patch.Name} failed: {ex.Message}");
                failureCount++;
            }
        }

        PatchHelper.Log(
            $"Shader patches applied: success={appliedCount}, failures={failureCount}"
        );
    }
}
