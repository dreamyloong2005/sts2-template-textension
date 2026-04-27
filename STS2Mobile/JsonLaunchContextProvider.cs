using System;
using System.IO;
using System.Text.Json;

namespace STS2Mobile;

public sealed class JsonLaunchContextProvider : ILaunchContextProvider
{
    public const string LaunchContextPathEnvVar = "TLAUNCHER_LAUNCH_CONTEXT_PATH";
    public const string LaunchContextJsonEnvVar = "TLAUNCHER_LAUNCH_CONTEXT_JSON";
    public const string LauncherPackageEnvVar = "TLAUNCHER_PACKAGE_NAME";

    public bool TryLoad(out LaunchContext context, out string source)
    {
        var inlineJson = Environment.GetEnvironmentVariable(LaunchContextJsonEnvVar);
        if (TryLoadFromJson(inlineJson, $"{LaunchContextJsonEnvVar} environment variable", out context))
        {
            source = $"{LaunchContextJsonEnvVar} environment variable";
            return true;
        }

        var explicitPath = Environment.GetEnvironmentVariable(LaunchContextPathEnvVar);
        if (TryLoadFromPath(explicitPath, out context))
        {
            source = explicitPath;
            return true;
        }

        context = null;
        source = string.Empty;
        return false;
    }

    private static bool TryLoadFromPath(string path, out LaunchContext context)
    {
        if (string.IsNullOrWhiteSpace(path) || !File.Exists(path))
        {
            context = null;
            return false;
        }

        try
        {
            return TryLoadFromJson(File.ReadAllText(path), path, out context);
        }
        catch (Exception ex)
        {
            PatchHelper.Log($"Failed to read launch context at {path}: {ex.Message}");
            context = null;
            return false;
        }
    }

    private static bool TryLoadFromJson(string json, string source, out LaunchContext context)
    {
        if (string.IsNullOrWhiteSpace(json))
        {
            context = null;
            return false;
        }

        try
        {
            using var document = JsonDocument.Parse(json);
            context = ParseLaunchContext(document.RootElement).Normalize();
            return true;
        }
        catch (Exception ex)
        {
            PatchHelper.Log($"Failed to parse launch context from {source}: {ex.Message}");
            context = null;
            return false;
        }
    }

    private static LaunchContext ParseLaunchContext(JsonElement root)
    {
        var versionId = ReadString(root, "versionId", "1");
        var packageName = ReadPackageName();
        var defaultConfigPath = LaunchContext.BuildDefaultConfigPath(versionId, packageName);
        var gameRoot = ReadString(root, "gameRoot", LaunchContext.DefaultGameRoot);

        return new LaunchContext
        {
            VersionId = versionId,
            LaunchMode = ReadString(root, "launchMode", LaunchContext.GameLaunchMode),
            GameConfigPath = ReadString(root, "gameConfigPath", defaultConfigPath),
            GameRoot = gameRoot,
            GameFilesDir = ReadString(root, "gameFilesDir", gameRoot),
            ModsDir = ReadString(root, "modsDir", $"{gameRoot}/mods"),
            SavesDir = ReadString(root, "savesDir", $"{gameRoot}/saves"),
            SpineUpdateDivisor = ReadInt(root, "spineUpdateDivisor", 2),
            PreloadTrimEnabled = ReadBool(root, "preloadTrimEnabled", true),
            AssetLoadingBatchSize = ReadInt(root, "assetLoadingBatchSize", 8),
            MobileShadersEnabled = ReadBool(root, "mobileShadersEnabled", true),
            ShaderCacheEnabled = ReadBool(root, "shaderCacheEnabled", false),
            ParticleScalePercent = ReadInt(root, "particleScalePercent", 50),
            GlowMode = ReadString(root, "glowMode", "reduced"),
            VfxLimitEnabled = ReadBool(root, "vfxLimitEnabled", false),
            Renderer = ReadString(root, "renderer", "vulkan"),
        };
    }

    private static string ReadPackageName()
    {
        var packageName = Environment.GetEnvironmentVariable(LauncherPackageEnvVar);
        return string.IsNullOrWhiteSpace(packageName)
            ? LaunchContext.DefaultPackageName
            : packageName.Trim();
    }

    private static string ReadString(JsonElement root, string name, string defaultValue)
    {
        if (root.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String)
        {
            var text = value.GetString();
            if (!string.IsNullOrWhiteSpace(text))
            {
                return text.Trim();
            }
        }

        return defaultValue;
    }

    private static int ReadInt(JsonElement root, string name, int defaultValue)
    {
        if (root.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.Number)
        {
            try
            {
                return value.GetInt32();
            }
            catch
            {
            }
        }

        return defaultValue;
    }

    private static bool ReadBool(JsonElement root, string name, bool defaultValue)
    {
        if (root.TryGetProperty(name, out var value))
        {
            if (value.ValueKind == JsonValueKind.True)
                return true;

            if (value.ValueKind == JsonValueKind.False)
                return false;
        }

        return defaultValue;
    }
}
