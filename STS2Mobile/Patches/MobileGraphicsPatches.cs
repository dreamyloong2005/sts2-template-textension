using System;
using System.Collections.Generic;
using Godot;
using HarmonyLib;
using MegaCrit.Sts2.Core.Nodes;
using STS2Mobile.Patches.Shaders;

namespace STS2Mobile.Patches;

// Applies mobile-oriented graphics tuning without relying on launcher warmup screens.
public static class MobileGraphicsPatches
{
    private static float _particleScale = 0.5f;
    private static string _glowMode = "reduced";
    private static bool _vfxLimitEnabled;
    private static int _activeVfxCount;

    private const int MaxSimultaneousVfx = 20;

    private static readonly HashSet<ulong> ScaledInstances = new();

    public static void Apply(Harmony harmony)
    {
        _particleScale = Math.Clamp(AppPaths.ParticleScalePercent, 0, 100) / 100f;
        _glowMode = AppPaths.GlowMode;
        _vfxLimitEnabled = AppPaths.VfxLimitEnabled;

        PatchHelper.Log(
            $"Mobile graphics config: shaders={AppPaths.MobileShadersEnabled}, "
                + $"particles={_particleScale:P0}, glow={_glowMode}, vfxLimit={_vfxLimitEnabled}"
        );

        ShaderPatchLoader.RegisterAll();

        PatchHelper.Patch(
            harmony,
            typeof(NGame),
            "GameStartup",
            postfix: PatchHelper.Method(typeof(MobileGraphicsPatches), nameof(GameStartupPostfix))
        );

        if (_particleScale < 1f || _vfxLimitEnabled)
        {
            var vfxParticleSystemType = typeof(NGame).Assembly.GetType(
                "MegaCrit.Sts2.Core.Nodes.Vfx.Utilities.NVfxParticleSystem"
            );
            if (vfxParticleSystemType == null)
            {
                PatchHelper.Log(
                    "Mobile graphics patch skipped VFX tuning: NVfxParticleSystem not found"
                );
                return;
            }

            PatchHelper.Patch(
                harmony,
                vfxParticleSystemType,
                "_Ready",
                prefix: PatchHelper.Method(
                    typeof(MobileGraphicsPatches),
                    nameof(VfxParticleReadyPrefix)
                )
            );
        }
    }

    public static bool VfxParticleReadyPrefix(object __instance)
    {
        if (__instance is not Node node)
            return true;

        if (_vfxLimitEnabled)
        {
            if (++_activeVfxCount > MaxSimultaneousVfx)
            {
                _activeVfxCount--;
                node.QueueFree();
                return false;
            }

            node.TreeExiting += OnVfxExiting;
        }

        if (_particleScale < 1f)
        {
            ulong instanceId = ((GodotObject)node).GetInstanceId();
            if (!ScaledInstances.Contains(instanceId))
            {
                ScaledInstances.Add(instanceId);
                node.TreeExiting += () => ScaledInstances.Remove(instanceId);

                int childCount = node.GetChildCount(false);
                for (int i = 0; i < childCount; i++)
                {
                    Node child = node.GetChild(i, false);
                    if (child is GpuParticles2D gpuParticles)
                        gpuParticles.Amount = Math.Max(
                            1,
                            (int)(gpuParticles.Amount * _particleScale)
                        );
                    else if (child is CpuParticles2D cpuParticles)
                        cpuParticles.Amount = Math.Max(
                            1,
                            (int)(cpuParticles.Amount * _particleScale)
                        );
                }
            }
        }

        return true;
    }

    public static void GameStartupPostfix(object __instance)
    {
        try
        {
            if (__instance is not Node game)
                return;

            ShaderPatchLoader.ApplyAll();
            if (ApplyGlowSettings(game))
                return;

            Callable
                .From(
                    () =>
                    {
                        try
                        {
                            if (!ApplyGlowSettings(game))
                                PatchHelper.Log(
                                    "Mobile graphics glow tuning skipped: WorldEnvironment not found"
                                );
                        }
                        catch (Exception ex)
                        {
                            PatchHelper.Log(
                                $"Mobile graphics deferred glow tuning failed: {ex.Message}"
                            );
                        }
                    }
                )
                .CallDeferred();
        }
        catch (Exception ex)
        {
            PatchHelper.Log($"Mobile graphics GameStartupPostfix failed: {ex.Message}");
        }
    }

    private static bool ApplyGlowSettings(Node game)
    {
        if (_glowMode == "full")
        {
            PatchHelper.Log("Mobile graphics glow mode FULL: no changes applied");
            return true;
        }

        WorldEnvironment worldEnvironment = game.GetNodeOrNull<WorldEnvironment>("%WorldEnvironment");
        if (worldEnvironment?.Environment == null)
            return false;

        Godot.Environment environment = worldEnvironment.Environment;
        switch (_glowMode)
        {
            case "off":
                environment.GlowEnabled = false;
                environment.AdjustmentEnabled = false;
                PatchHelper.Log("Mobile graphics glow mode OFF");
                break;
            case "minimal":
                environment.GlowEnabled = true;
                environment.GlowIntensity = 0.3f;
                environment.GlowStrength = 0.3f;
                environment.GlowBloom = 0f;
                environment.SetGlowLevel(2, 0f);
                environment.SetGlowLevel(3, 0f);
                environment.SetGlowLevel(4, 0f);
                environment.SetGlowLevel(5, 0f);
                environment.SetGlowLevel(6, 0f);
                PatchHelper.Log("Mobile graphics glow mode MINIMAL");
                break;
            case "low":
                environment.GlowIntensity = 0.4f;
                environment.GlowStrength = 0.4f;
                environment.GlowBloom = 0f;
                environment.SetGlowLevel(3, 0f);
                environment.SetGlowLevel(4, 0f);
                environment.SetGlowLevel(5, 0f);
                environment.SetGlowLevel(6, 0f);
                PatchHelper.Log("Mobile graphics glow mode LOW");
                break;
            default:
                environment.GlowIntensity = 0.5f;
                environment.GlowStrength = 0.5f;
                environment.GlowBloom = 0f;
                environment.SetGlowLevel(4, 0f);
                environment.SetGlowLevel(5, 0f);
                environment.SetGlowLevel(6, 0f);
                PatchHelper.Log("Mobile graphics glow mode REDUCED");
                break;
        }

        return true;
    }

    private static void OnVfxExiting()
    {
        if (_activeVfxCount > 0)
            _activeVfxCount--;
    }
}
