using System;
using System.Collections.Concurrent;
using System.IO;
using System.Text.Json;
using System.Threading;

namespace STS2Mobile;

internal static class FileLogger
{
    private const string LogPrefix = "sts2mobile_";
    private const string LogExtension = ".log";
    private const int MaxLogFiles = 3;
    private const int QueueCapacity = 1024;

    private static readonly object InitLock = new();
    private static readonly BlockingCollection<string> Queue = new(QueueCapacity);

    private static bool _initialized;
    private static bool _enabled;
    private static string _logPath = string.Empty;
    private static int _droppedMessageCount;

    public static string CurrentLogPath => _logPath;

    public static void Write(string message)
    {
        EnsureInitialized();
        if (!_enabled)
            return;

        FlushDroppedMessages();
        Enqueue(FormatLine(ClassifyLevel(message), message));
    }

    private static void EnsureInitialized()
    {
        if (_initialized)
            return;

        lock (InitLock)
        {
            if (_initialized)
                return;

            _initialized = true;

            try
            {
                var context = ResolveLaunchContext();
                var logsDir = Path.Combine(context.GameRoot, "logs");
                Directory.CreateDirectory(logsDir);
                RotateLogs(logsDir);

                var versionSuffix = SanitizeFileNamePart(context.VersionId);
                _logPath = Path.Combine(
                    logsDir,
                    $"{LogPrefix}{DateTime.Now:yyyyMMdd_HHmmss}_v{versionSuffix}{LogExtension}"
                );

                var writerThread = new Thread(ProcessQueue)
                {
                    IsBackground = true,
                    Name = "STS2MobileFileLogger",
                };
                writerThread.Start();

                _enabled = true;
                Enqueue(FormatLine("Info", $"File logging initialized at {_logPath}"));
            }
            catch
            {
                _enabled = false;
                _logPath = string.Empty;
            }
        }
    }

    private static void RotateLogs(string logsDir)
    {
        try
        {
            var oldLogs = Directory.GetFiles(logsDir, $"{LogPrefix}*{LogExtension}");
            Array.Sort(oldLogs, StringComparer.OrdinalIgnoreCase);

            for (int i = 0; i < oldLogs.Length - (MaxLogFiles - 1); i++)
            {
                try
                {
                    File.Delete(oldLogs[i]);
                }
                catch
                {
                }
            }
        }
        catch
        {
        }
    }

    private static void FlushDroppedMessages()
    {
        int dropped = Interlocked.Exchange(ref _droppedMessageCount, 0);
        if (dropped <= 0)
            return;

        Enqueue(FormatLine("Warn", $"Dropped {dropped} log messages due to queue overflow"));
    }

    private static void Enqueue(string line)
    {
        if (!Queue.TryAdd(line))
            Interlocked.Increment(ref _droppedMessageCount);
    }

    private static void ProcessQueue()
    {
        foreach (var line in Queue.GetConsumingEnumerable())
        {
            try
            {
                File.AppendAllText(_logPath, line + Environment.NewLine);
            }
            catch
            {
            }
        }
    }

    private static LaunchContext ResolveLaunchContext()
    {
        if (TryLoadLaunchContext(out var context))
            return context.Normalize();

        return LaunchContext.CreateDefault();
    }

    private static bool TryLoadLaunchContext(out LaunchContext context)
    {
        var inlineJson = Environment.GetEnvironmentVariable(
            JsonLaunchContextProvider.LaunchContextJsonEnvVar
        );
        if (TryParseLaunchContext(inlineJson, out context))
            return true;

        var explicitPath = Environment.GetEnvironmentVariable(
            JsonLaunchContextProvider.LaunchContextPathEnvVar
        );
        if (!string.IsNullOrWhiteSpace(explicitPath) && File.Exists(explicitPath))
        {
            try
            {
                return TryParseLaunchContext(File.ReadAllText(explicitPath), out context);
            }
            catch
            {
            }
        }

        context = null;
        return false;
    }

    private static bool TryParseLaunchContext(string json, out LaunchContext context)
    {
        if (string.IsNullOrWhiteSpace(json))
        {
            context = null;
            return false;
        }

        try
        {
            using var document = JsonDocument.Parse(json);
            var root = document.RootElement;
            var versionId = ReadString(root, "versionId", "1");
            var gameRoot = ReadString(root, "gameRoot", LaunchContext.DefaultGameRoot);
            var packageName =
                Environment.GetEnvironmentVariable(JsonLaunchContextProvider.LauncherPackageEnvVar)
                ?? LaunchContext.DefaultPackageName;

            context = new LaunchContext
            {
                VersionId = versionId,
                LaunchMode = ReadString(root, "launchMode", LaunchContext.GameLaunchMode),
                GameRoot = gameRoot,
                GameFilesDir = ReadString(root, "gameFilesDir", gameRoot),
                ModsDir = ReadString(root, "modsDir", gameRoot + "/mods"),
                SavesDir = ReadString(root, "savesDir", gameRoot + "/saves"),
                GameConfigPath = ReadString(
                    root,
                    "gameConfigPath",
                    LaunchContext.BuildDefaultConfigPath(versionId, packageName)
                ),
            };
            return true;
        }
        catch
        {
            context = null;
            return false;
        }
    }

    private static string ReadString(JsonElement root, string name, string defaultValue)
    {
        if (root.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String)
        {
            var text = value.GetString();
            if (!string.IsNullOrWhiteSpace(text))
                return text.Trim();
        }

        return defaultValue;
    }

    private static string FormatLine(string level, string message) =>
        $"{DateTime.Now:yyyy-MM-dd HH:mm:ss.fff} [{level}] STS2Mobile: {message}";

    private static string ClassifyLevel(string message)
    {
        if (message.StartsWith("FAILED", StringComparison.Ordinal))
            return "Error";

        if (message.Contains("WARNING", StringComparison.OrdinalIgnoreCase))
            return "Warn";

        return "Info";
    }

    private static string SanitizeFileNamePart(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
            return "1";

        var sanitized = value.Trim();
        foreach (var invalidChar in Path.GetInvalidFileNameChars())
            sanitized = sanitized.Replace(invalidChar, '_');

        return sanitized;
    }
}
