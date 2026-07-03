using System;

namespace STS2Mobile;

public sealed class LaunchContext
{
    public const string DefaultPackageName = "com.dreamyloong.tlauncher_app";
    public const string DefaultGameRoot = "/storage/emulated/0/TLauncher/STS2";
    public const string GameLaunchMode = "game";
    public const string ShaderWarmupLaunchMode = "shaderWarmup";

    public string VersionId { get; init; } = "1";
    public string LaunchMode { get; init; } = GameLaunchMode;
    public string GameConfigPath { get; init; } = BuildDefaultConfigPath("1", DefaultPackageName);
    public string GameRoot { get; init; } = DefaultGameRoot;
    public string GameFilesDir { get; init; } = DefaultGameRoot;
    public string ModsDir { get; init; } = DefaultGameRoot + "/mods";
    public string SavesDir { get; init; } = DefaultGameRoot + "/saves";
    public int SpineUpdateDivisor { get; init; } = 2;
    public bool PreloadTrimEnabled { get; init; }
    public int AssetLoadingBatchSize { get; init; } = 8;
    public bool MobileShadersEnabled { get; init; } = true;
    public bool ShaderCacheEnabled { get; init; }
    public int ParticleScalePercent { get; init; } = 50;
    public string GlowMode { get; init; } = "reduced";
    public bool VfxLimitEnabled { get; init; }
    public string Renderer { get; init; } = "vulkan";

    public LaunchContext Normalize()
    {
        var versionId = string.IsNullOrWhiteSpace(VersionId) ? "1" : VersionId.Trim();
        var launchMode = NormalizeLaunchMode(LaunchMode);
        var gameRoot = string.IsNullOrWhiteSpace(GameRoot) ? DefaultGameRoot : GameRoot.Trim();
        var gameFilesDir = string.IsNullOrWhiteSpace(GameFilesDir) ? gameRoot : GameFilesDir.Trim();
        var modsDir = string.IsNullOrWhiteSpace(ModsDir) ? $"{gameRoot}/mods" : ModsDir.Trim();
        var savesDir = string.IsNullOrWhiteSpace(SavesDir) ? $"{gameRoot}/saves" : SavesDir.Trim();
        var spineUpdateDivisor = SpineUpdateDivisor < 1 ? 1 : SpineUpdateDivisor;
        var preloadTrimEnabled = PreloadTrimEnabled;
        var assetLoadingBatchSize = AssetLoadingBatchSize < 0 ? 0 : AssetLoadingBatchSize;
        var particleScalePercent = Math.Clamp(ParticleScalePercent, 0, 100);
        var glowMode = NormalizeGlowMode(GlowMode);
        var vfxLimitEnabled = VfxLimitEnabled;
        var renderer = NormalizeRenderer(Renderer);
        var gameConfigPath = string.IsNullOrWhiteSpace(GameConfigPath)
            ? BuildDefaultConfigPath(versionId, DefaultPackageName)
            : GameConfigPath.Trim();

        return new LaunchContext
        {
            VersionId = versionId,
            LaunchMode = launchMode,
            GameConfigPath = gameConfigPath,
            GameRoot = gameRoot,
            GameFilesDir = gameFilesDir,
            ModsDir = modsDir,
            SavesDir = savesDir,
            SpineUpdateDivisor = spineUpdateDivisor,
            PreloadTrimEnabled = preloadTrimEnabled,
            AssetLoadingBatchSize = assetLoadingBatchSize,
            MobileShadersEnabled = MobileShadersEnabled,
            ShaderCacheEnabled = ShaderCacheEnabled,
            ParticleScalePercent = particleScalePercent,
            GlowMode = glowMode,
            VfxLimitEnabled = vfxLimitEnabled,
            Renderer = renderer,
        };
    }

    public static LaunchContext CreateDefault(string versionId = "1")
    {
        return new LaunchContext
        {
            VersionId = versionId,
            LaunchMode = GameLaunchMode,
            GameConfigPath = BuildDefaultConfigPath(versionId, DefaultPackageName),
            GameRoot = DefaultGameRoot,
            GameFilesDir = DefaultGameRoot,
            ModsDir = DefaultGameRoot + "/mods",
            SavesDir = DefaultGameRoot + "/saves",
            SpineUpdateDivisor = 2,
            PreloadTrimEnabled = false,
            AssetLoadingBatchSize = 8,
            MobileShadersEnabled = true,
            ShaderCacheEnabled = false,
            ParticleScalePercent = 50,
            GlowMode = "reduced",
            VfxLimitEnabled = false,
            Renderer = "vulkan",
        };
    }

    private static string NormalizeLaunchMode(string launchMode)
    {
        if (string.IsNullOrWhiteSpace(launchMode))
            return GameLaunchMode;

        return launchMode.Trim() == ShaderWarmupLaunchMode
            ? ShaderWarmupLaunchMode
            : GameLaunchMode;
    }

    public static string BuildDefaultConfigPath(string versionId, string packageName)
    {
        var normalizedVersionId = string.IsNullOrWhiteSpace(versionId) ? "1" : versionId.Trim();
        var normalizedPackageName = string.IsNullOrWhiteSpace(packageName)
            ? DefaultPackageName
            : packageName.Trim();
        return $"/data/data/{normalizedPackageName}/files/tlauncher.sts2config_{normalizedVersionId}.json";
    }

    private static string NormalizeGlowMode(string glowMode)
    {
        if (string.IsNullOrWhiteSpace(glowMode))
            return "reduced";

        return glowMode.Trim().ToLowerInvariant() switch
        {
            "full" => "full",
            "off" => "off",
            "minimal" => "minimal",
            "low" => "low",
            _ => "reduced",
        };
    }

    private static string NormalizeRenderer(string renderer)
    {
        if (string.IsNullOrWhiteSpace(renderer))
            return "vulkan";

        return renderer.Trim().ToLowerInvariant() switch
        {
            "opengl" => "opengl",
            _ => "vulkan",
        };
    }
}
