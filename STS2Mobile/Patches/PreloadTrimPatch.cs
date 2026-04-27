using System;
using System.Collections.Generic;
using System.Reflection;
using HarmonyLib;
using MegaCrit.Sts2.Core.Nodes;

namespace STS2Mobile.Patches;

// Defers heavy collection and profile screen assets out of the main menu preload set.
public static class PreloadTrimPatch
{
    private static readonly string[] DeferredProviderNames =
    {
        "MegaCrit.Sts2.Core.Nodes.Screens.Timeline.NTimelineScreen",
        "MegaCrit.Sts2.Core.Nodes.Screens.CardLibrary.NCardLibrary",
        "MegaCrit.Sts2.Core.Nodes.Screens.Bestiary.NBestiary",
        "MegaCrit.Sts2.Core.Nodes.Screens.Bestiary.NBestiaryEntry",
        "MegaCrit.Sts2.Core.Nodes.Screens.RelicCollection.NRelicCollection",
        "MegaCrit.Sts2.Core.Nodes.Screens.PotionLab.NPotionLab",
        "MegaCrit.Sts2.Core.Nodes.Screens.StatsScreen.NStatsScreen",
        "MegaCrit.Sts2.Core.Nodes.Screens.RunHistoryScreen.NRunHistory",
        "MegaCrit.Sts2.Core.Nodes.Screens.ProfileScreen.NProfileScreen",
        "MegaCrit.Sts2.Core.Nodes.Screens.ProfileScreen.NAchievementsGrid",
    };

    private static IReadOnlySet<string> _trimmedMainMenuSet;
    private static bool _trimAttempted;

    public static void Apply(Harmony harmony)
    {
        if (!AppPaths.PreloadTrimEnabled)
        {
            PatchHelper.Log("Preload trim patch disabled by launch context");
            return;
        }

        var assetSetsType = typeof(NGame).Assembly.GetType("MegaCrit.Sts2.Core.Assets.AssetSets");
        if (assetSetsType == null)
        {
            PatchHelper.Log("Preload trim patch skipped: AssetSets type not found");
            return;
        }

        PatchHelper.Patch(
            harmony,
            assetSetsType,
            "get_MainMenuSet",
            postfix: PatchHelper.Method(typeof(PreloadTrimPatch), nameof(MainMenuSetGetterPostfix))
        );

        PatchHelper.Log("Preload trim patch enabled");
    }

    public static void MainMenuSetGetterPostfix(ref IReadOnlySet<string> __result)
    {
        if (__result == null || !AppPaths.PreloadTrimEnabled)
            return;

        if (_trimmedMainMenuSet != null)
        {
            __result = _trimmedMainMenuSet;
            return;
        }

        if (_trimAttempted)
            return;

        _trimAttempted = true;

        try
        {
            var deferredPaths = CollectDeferredPaths(typeof(NGame).Assembly);
            if (deferredPaths.Count == 0)
            {
                PatchHelper.Log("Preload trim skipped: no deferred asset providers resolved");
                return;
            }

            var trimmed = new HashSet<string>(__result, StringComparer.Ordinal);
            int originalCount = trimmed.Count;
            trimmed.ExceptWith(deferredPaths);

            int removedCount = originalCount - trimmed.Count;
            if (removedCount <= 0)
            {
                PatchHelper.Log("Preload trim found no overlapping main menu assets to defer");
                return;
            }

            _trimmedMainMenuSet = trimmed;
            __result = _trimmedMainMenuSet;

            PatchHelper.Log(
                $"Preload trim applied: MainMenuSet {originalCount} -> {trimmed.Count} "
                    + $"({removedCount} deferred)"
            );
        }
        catch (Exception ex)
        {
            PatchHelper.Log($"Preload trim failed: {ex.Message}");
        }
    }

    private static HashSet<string> CollectDeferredPaths(Assembly sts2Assembly)
    {
        var deferredPaths = new HashSet<string>(StringComparer.Ordinal);

        foreach (var providerName in DeferredProviderNames)
        {
            var providerType = sts2Assembly.GetType(providerName);
            if (providerType == null)
            {
                PatchHelper.Log($"Preload trim provider not found: {providerName}");
                continue;
            }

            var assetPathsProperty = providerType.GetProperty(
                "AssetPaths",
                BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic
            );
            if (assetPathsProperty == null)
            {
                PatchHelper.Log($"Preload trim {providerType.Name}.AssetPaths not found");
                continue;
            }

            try
            {
                if (assetPathsProperty.GetValue(null) is not IEnumerable<string> assetPaths)
                    continue;

                int countBefore = deferredPaths.Count;
                foreach (var path in assetPaths)
                {
                    if (!string.IsNullOrWhiteSpace(path))
                        deferredPaths.Add(path);
                }

                int addedCount = deferredPaths.Count - countBefore;
                if (addedCount > 0)
                    PatchHelper.Log(
                        $"Preload trim {providerType.Name}.AssetPaths contributed {addedCount} paths"
                    );
            }
            catch (Exception ex)
            {
                PatchHelper.Log($"Preload trim {providerType.Name}.AssetPaths threw: {ex.Message}");
            }
        }

        return deferredPaths;
    }
}
