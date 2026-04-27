using System;
using System.IO;

namespace STS2Mobile;

public static class AppPaths
{
    private static ILaunchContextProvider _provider = new JsonLaunchContextProvider();
    private static LaunchContext _context;

    private static LaunchContext Context => _context ??= LoadContext();

    public static string VersionId => Context.VersionId;
    public static string LaunchMode => Context.LaunchMode;
    public static bool IsShaderWarmupLaunch => Context.LaunchMode == LaunchContext.ShaderWarmupLaunchMode;
    public static string GameRoot => Context.GameRoot;
    public static string ModsDir => Context.ModsDir;
    public static string SavesDir => Context.SavesDir;
    public static string GameFilesDir => Context.GameFilesDir;
    public static string ConfigPath => Context.GameConfigPath;
    public static int SpineUpdateDivisor => Context.SpineUpdateDivisor;
    public static bool PreloadTrimEnabled => Context.PreloadTrimEnabled;
    public static int AssetLoadingBatchSize => Context.AssetLoadingBatchSize;
    public static bool MobileShadersEnabled => Context.MobileShadersEnabled;
    public static bool ShaderCacheEnabled => Context.ShaderCacheEnabled;
    public static int ParticleScalePercent => Context.ParticleScalePercent;
    public static string GlowMode => Context.GlowMode;
    public static bool VfxLimitEnabled => Context.VfxLimitEnabled;
    public static string Renderer => Context.Renderer;

    public static void SetProvider(ILaunchContextProvider provider)
    {
        _provider = provider ?? throw new ArgumentNullException(nameof(provider));
        _context = null;
    }

    public static void Reset()
    {
        _context = null;
    }

    public static bool HasStoragePermission()
    {
        return Directory.Exists(GameRoot) && Directory.Exists(GameFilesDir);
    }

    public static void RequestStoragePermission()
    {
        PatchHelper.Log("Storage permission is managed by TLauncher.");
    }

    public static void EnsureVersionDirectories()
    {
        TryCreateDirectory(ModsDir);
        TryCreateDirectory(SavesDir);
    }

    private static LaunchContext LoadContext()
    {
        if (_provider.TryLoad(out var context, out var source))
        {
            PatchHelper.Log($"Loaded launch context from {source}");
            return context;
        }

        PatchHelper.Log("Launch context was not found. Using default STS2 mobile paths.");
        return LaunchContext.CreateDefault();
    }

    private static void TryCreateDirectory(string path)
    {
        try
        {
            Directory.CreateDirectory(path);
        }
        catch
        {
        }
    }
}
