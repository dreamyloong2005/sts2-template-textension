using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using STS2Mobile.Patches.Shaders;

namespace STS2Mobile.Game.Warmup;

internal static class ShaderWarmupMarker
{
    public const int WarmupVersion = 1;

    private const string MarkerDirectoryName = ".tlauncher";
    private const string MarkerFileName = "shader_warmup.json";
    private const string ReleaseInfoFileName = "release_info.json";
    private const string PackFileName = "SlayTheSpire2.pck";

    public static string MarkerPath =>
        Path.Combine(AppPaths.GameFilesDir, MarkerDirectoryName, MarkerFileName);

    public static string ReleaseVersion => ReadReleaseVersion();

    public static bool IsCurrent(out string reason)
    {
        reason = "current";

        if (!File.Exists(MarkerPath))
        {
            reason = "marker_missing";
            return false;
        }

        try
        {
            using JsonDocument document = JsonDocument.Parse(File.ReadAllText(MarkerPath));
            JsonElement root = document.RootElement;
            if (!ReadBool(root, "success", false))
            {
                reason = "last_warmup_failed";
                return false;
            }

            if (ReadInt(root, "warmupVersion", 0) != WarmupVersion)
            {
                reason = "warmup_version_changed";
                return false;
            }

            if (ReadInt(root, "shaderPatchSetVersion", 0) != ShaderPatchLoader.PatchSetVersion)
            {
                reason = "shader_patch_set_changed";
                return false;
            }

            if (!StringEquals(ReadString(root, "releaseVersion"), ReleaseVersion))
            {
                reason = "release_version_changed";
                DeleteMarker(reason);
                return false;
            }

            if (!StringEquals(ReadString(root, "renderer"), AppPaths.Renderer))
            {
                reason = "renderer_changed";
                return false;
            }

            if (ReadBool(root, "mobileShadersEnabled", true) != AppPaths.MobileShadersEnabled)
            {
                reason = "mobile_shader_setting_changed";
                return false;
            }

            return true;
        }
        catch (Exception ex)
        {
            reason = "marker_read_failed";
            PatchHelper.Log($"Shader warmup marker read failed: {ex.Message}");
            return false;
        }
    }

    public static void WriteSuccess(ShaderWarmupResult result)
    {
        try
        {
            string markerDirectory = Path.GetDirectoryName(MarkerPath);
            if (!string.IsNullOrWhiteSpace(markerDirectory))
                Directory.CreateDirectory(markerDirectory);

            var marker = new Dictionary<string, object>
            {
                ["success"] = true,
                ["warmupVersion"] = WarmupVersion,
                ["releaseVersion"] = ReleaseVersion,
                ["renderer"] = AppPaths.Renderer,
                ["mobileShadersEnabled"] = AppPaths.MobileShadersEnabled,
                ["shaderPatchSetVersion"] = ShaderPatchLoader.PatchSetVersion,
                ["totalMaterials"] = result.TotalMaterials,
                ["compiledMaterials"] = result.CompiledMaterials,
                ["failedMaterials"] = result.FailedMaterials,
                ["elapsedMs"] = result.ElapsedMilliseconds,
                ["createdAtUtc"] = DateTime.UtcNow.ToString("O"),
            };

            File.WriteAllText(
                MarkerPath,
                JsonSerializer.Serialize(marker, new JsonSerializerOptions { WriteIndented = true })
            );
        }
        catch (Exception ex)
        {
            PatchHelper.Log($"Shader warmup marker write failed: {ex.Message}");
        }
    }

    private static string ReadReleaseVersion()
    {
        string releaseInfoPath = Path.Combine(AppPaths.GameFilesDir, ReleaseInfoFileName);
        try
        {
            if (File.Exists(releaseInfoPath))
            {
                using JsonDocument document = JsonDocument.Parse(File.ReadAllText(releaseInfoPath));
                string version = ReadString(document.RootElement, "version");
                if (!string.IsNullOrWhiteSpace(version))
                    return version.Trim();
            }
        }
        catch (Exception ex)
        {
            PatchHelper.Log($"Shader warmup release_info read failed: {ex.Message}");
        }

        string packPath = Path.Combine(AppPaths.GameFilesDir, PackFileName);
        try
        {
            if (File.Exists(packPath))
            {
                var info = new FileInfo(packPath);
                return $"unknown-{info.Length}-{info.LastWriteTimeUtc.Ticks}";
            }
        }
        catch
        {
        }

        return "unknown";
    }

    private static string ReadString(JsonElement root, string name)
    {
        return root.TryGetProperty(name, out JsonElement value)
            && value.ValueKind == JsonValueKind.String
            ? value.GetString() ?? string.Empty
            : string.Empty;
    }

    private static int ReadInt(JsonElement root, string name, int defaultValue)
    {
        if (
            root.TryGetProperty(name, out JsonElement value)
            && value.ValueKind == JsonValueKind.Number
            && value.TryGetInt32(out int result)
        )
        {
            return result;
        }

        return defaultValue;
    }

    private static bool ReadBool(JsonElement root, string name, bool defaultValue)
    {
        if (!root.TryGetProperty(name, out JsonElement value))
            return defaultValue;

        return value.ValueKind switch
        {
            JsonValueKind.True => true,
            JsonValueKind.False => false,
            _ => defaultValue,
        };
    }

    private static bool StringEquals(string left, string right)
    {
        return string.Equals(left?.Trim(), right?.Trim(), StringComparison.OrdinalIgnoreCase);
    }

    private static void DeleteMarker(string reason)
    {
        try
        {
            if (File.Exists(MarkerPath))
            {
                File.Delete(MarkerPath);
                PatchHelper.Log($"Deleted stale shader warmup marker: {reason}");
            }
        }
        catch (Exception ex)
        {
            PatchHelper.Log($"Shader warmup marker delete failed: {ex.Message}");
        }
    }
}
