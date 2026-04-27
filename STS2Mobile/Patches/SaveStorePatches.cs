using System;
using System.IO;
using HarmonyLib;
using MegaCrit.Sts2.Core.Saves;

namespace STS2Mobile.Patches;

// Routes the game's local save store to the selected version's saves folder.
public static class SaveStorePatches
{
    public static void Apply(Harmony harmony)
    {
        PatchHelper.Patch(
            harmony,
            typeof(SaveManager),
            "ConstructDefault",
            prefix: PatchHelper.Method(typeof(SaveStorePatches), nameof(ConstructDefaultPrefix))
        );
    }

    public static bool ConstructDefaultPrefix(ref SaveManager __result)
    {
        try
        {
            AppPaths.EnsureVersionDirectories();
            var savesDir = AppPaths.SavesDir;
            Directory.CreateDirectory(savesDir);

            __result = new SaveManager(new DirectorySaveStore(savesDir));
            PatchHelper.Log($"SaveManager redirected to saves dir: {savesDir}");
            return false;
        }
        catch (Exception ex)
        {
            PatchHelper.Log($"SaveManager injection failed, falling back to default: {ex}");
            return true;
        }
    }
}

