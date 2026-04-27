using System;
using System.Collections.Generic;
using System.Reflection;
using Godot;
using HarmonyLib;
using MegaCrit.Sts2.Core.Nodes;

namespace STS2Mobile.Patches;

// Splits large asset finalize bursts into smaller batches to reduce frame hitches.
public static class AssetLoadingSessionPatch
{
    private static FieldInfo _finalizingField;
    private static MethodInfo _addToCacheMethod;

    public static void Apply(Harmony harmony)
    {
        int batchSize = GetBatchSize();
        if (batchSize <= 0)
        {
            PatchHelper.Log("Asset loading session patch disabled (batch size <= 0)");
            return;
        }

        var sessionType = typeof(NGame).Assembly.GetType("MegaCrit.Sts2.Core.Assets.AssetLoadingSession");
        if (sessionType == null)
        {
            PatchHelper.Log("Asset loading session patch skipped: AssetLoadingSession type not found");
            return;
        }

        _finalizingField = sessionType.GetField(
            "_finalizing",
            BindingFlags.Instance | BindingFlags.NonPublic
        );
        _addToCacheMethod = sessionType.GetMethod(
            "AddToCache",
            BindingFlags.Instance | BindingFlags.NonPublic
        );

        if (_finalizingField == null || _addToCacheMethod == null)
        {
            PatchHelper.Log(
                "Asset loading session patch skipped: missing reflection members for FinalizeLoading"
            );
            return;
        }

        PatchHelper.Patch(
            harmony,
            sessionType,
            "FinalizeLoading",
            prefix: PatchHelper.Method(
                typeof(AssetLoadingSessionPatch),
                nameof(FinalizeLoadingPrefix)
            )
        );

        PatchHelper.Log($"Asset loading session patch enabled (batchSize={batchSize})");
    }

    public static bool FinalizeLoadingPrefix(object __instance)
    {
        int batchSize = GetBatchSize();
        if (batchSize <= 0 || _finalizingField == null || _addToCacheMethod == null)
            return true;

        if (_finalizingField.GetValue(__instance) is not Queue<string> queue)
            return true;

        if (queue.Count == 0)
            return true;

        try
        {
            int processed = 0;

            while (queue.Count > 0 && processed < batchSize)
            {
                string resourcePath = queue.Peek();
                if (string.IsNullOrWhiteSpace(resourcePath))
                {
                    queue.Dequeue();
                    continue;
                }

                Resource resource = ResourceLoader.LoadThreadedGet(resourcePath);
                _addToCacheMethod.Invoke(__instance, new object[] { resource, resourcePath });
                queue.Dequeue();
                processed++;
            }

            // When the queue is drained, let the original method run once to perform any final cleanup.
            return queue.Count == 0;
        }
        catch (Exception ex)
        {
            PatchHelper.Log($"Asset loading session patch failed: {ex.Message}");
            return true;
        }
    }

    private static int GetBatchSize()
    {
        int batchSize = AppPaths.AssetLoadingBatchSize;
        return batchSize < 0 ? 0 : batchSize;
    }
}
