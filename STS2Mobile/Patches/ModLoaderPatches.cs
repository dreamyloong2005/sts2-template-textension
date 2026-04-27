using System;
using System.Collections.Generic;
using System.Reflection;
using System.Reflection.Emit;
using Godot;
using HarmonyLib;
using MegaCrit.Sts2.Core.DevConsole;
using MegaCrit.Sts2.Core.Modding;
using MegaCrit.Sts2.Core.Nodes.Debug;
using MegaCrit.Sts2.Core.Saves;
using MegaCrit.Sts2.Core.TestSupport;

namespace STS2Mobile.Patches;

public static class ModLoaderPatches
{
    public static void Apply(Harmony harmony)
    {
        try
        {
            PatchHelper.Patch(
                harmony,
                typeof(ModManager),
                "Initialize",
                transpiler: PatchHelper.Method(
                    typeof(ModLoaderPatches),
                    nameof(InitializeTranspiler)
                )
            );
            PatchHelper.Patch(
                harmony,
                typeof(ModManager),
                "ReadSteamMods",
                prefix: PatchHelper.Method(typeof(ModLoaderPatches), nameof(ReadSteamModsPrefix))
            );
            PatchHelper.Log("[ModLoader] 成功注入 ModManager.Initialize 补丁！");
        }
        catch (Exception ex)
        {
            PatchHelper.Log("[ModLoader] 补丁注入失败: " + ex.ToString());
        }
    }

    public static IEnumerable<CodeInstruction> InitializeTranspiler(
        IEnumerable<CodeInstruction> instructions
    )
    {
        var matcher = new CodeMatcher(instructions).MatchStartForward(
            new CodeMatch(OpCodes.Ldstr, "mods")
        ); // 找到唯一一个 "mods" 字符串字面量

        if (matcher.IsValid)
        {
            // 当前位置在 ldstr "mods"
            // 前面一条指令是 ldloc directoryName
            // 后面是 call Path.Combine + stloc path
            matcher.Advance(-1); // 退到 ldloc directoryName
            matcher.RemoveInstructions(3); // 删除：ldloc directoryName + ldstr "mods" + call Path.Combine
            var getter = AccessTools.PropertyGetter(
                typeof(AppPaths),
                nameof(AppPaths.ModsDir)
            );
            matcher.InsertAndAdvance(new CodeInstruction(OpCodes.Call, getter));
        }
        // 如果没找到（理论上不可能），保持原指令（防止崩溃）

        return matcher.InstructionEnumeration();
    }

    public static bool ReadSteamModsPrefix()
    {
        return false;
    }
}

