using System;
using Godot;
using HarmonyLib;
using MegaCrit.Sts2.Core.Nodes;

namespace STS2Mobile.Patches;

// Lowers SpineSprite update frequency on mobile to reduce CPU cost in combat.
public static class SpinePerformancePatches
{
    public static void Apply(Harmony harmony)
    {
        int divisor = GetDivisor();
        if (divisor <= 1)
        {
            PatchHelper.Log("Spine performance patch disabled (divisor <= 1)");
            return;
        }

        var creatureVisualsType = typeof(NGame).Assembly.GetType(
            "MegaCrit.Sts2.Core.Nodes.Combat.NCreatureVisuals"
        );
        if (creatureVisualsType == null)
        {
            PatchHelper.Log("Spine performance patch skipped: NCreatureVisuals not found");
            return;
        }

        PatchHelper.Patch(
            harmony,
            creatureVisualsType,
            "_Ready",
            postfix: PatchHelper.Method(
                typeof(SpinePerformancePatches),
                nameof(CreatureVisualsReadyPostfix)
            )
        );

        PatchHelper.Log($"Spine performance patch enabled (divisor={divisor})");
    }

    public static void CreatureVisualsReadyPostfix(object __instance)
    {
        int divisor = GetDivisor();
        if (divisor <= 1)
            return;

        try
        {
            if (__instance is Node node)
            {
                int patchedCount = SetDivisorRecursive(node, divisor);
                if (patchedCount > 0)
                    PatchHelper.Log(
                        $"Spine visuals throttled: patched {patchedCount} node(s) with divisor={divisor}"
                    );
            }
        }
        catch (Exception ex)
        {
            PatchHelper.Log($"Spine performance patch failed: {ex.Message}");
        }
    }

    private static int SetDivisorRecursive(Node node, int divisor)
    {
        int patchedCount = 0;

        if (string.Equals(node.GetClass(), "SpineSprite", StringComparison.Ordinal))
        {
            node.Call("set_update_rate_divisor", divisor);
            patchedCount++;
        }

        foreach (Node child in node.GetChildren())
            patchedCount += SetDivisorRecursive(child, divisor);

        return patchedCount;
    }

    private static int GetDivisor()
    {
        int divisor = AppPaths.SpineUpdateDivisor;
        return divisor < 1 ? 1 : divisor;
    }
}
