using System;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using MegaCrit.Sts2.Core.Saves;

namespace STS2Mobile;

// Plain System.IO save store. It keeps the game's normal relative save paths
// inside the selected version's saves directory.
public sealed class DirectorySaveStore : ISaveStore
{
    private readonly string _dir;

    public DirectorySaveStore(string dir)
    {
        _dir = Path.GetFullPath(dir);
        Directory.CreateDirectory(_dir);
    }

    public string ReadFile(string path)
    {
        var fullPath = GetFullPath(path);
        return File.Exists(fullPath) ? File.ReadAllText(fullPath) : null;
    }

    public Task<string> ReadFileAsync(string path)
    {
        var fullPath = GetFullPath(path);
        return File.Exists(fullPath)
            ? File.ReadAllTextAsync(fullPath)
            : Task.FromResult<string>(null);
    }

    public void WriteFile(string path, string content)
    {
        WriteAtomically(path, temp => File.WriteAllText(temp, content));
    }

    public void WriteFile(string path, byte[] bytes)
    {
        WriteAtomically(path, temp => File.WriteAllBytes(temp, bytes));
    }

    public Task WriteFileAsync(string path, string content)
    {
        WriteFile(path, content);
        return Task.CompletedTask;
    }

    public Task WriteFileAsync(string path, byte[] bytes)
    {
        WriteFile(path, bytes);
        return Task.CompletedTask;
    }

    public bool FileExists(string path)
    {
        return File.Exists(GetFullPath(path));
    }

    public bool DirectoryExists(string path)
    {
        return Directory.Exists(GetFullPath(path));
    }

    public void DeleteFile(string path)
    {
        var fullPath = GetFullPath(path);
        if (File.Exists(fullPath))
            File.Delete(fullPath);
    }

    public void RenameFile(string sourcePath, string destinationPath)
    {
        var source = GetFullPath(sourcePath);
        var destination = GetFullPath(destinationPath);
        Directory.CreateDirectory(Path.GetDirectoryName(destination)!);

        if (File.Exists(destination))
            File.Delete(destination);
        File.Move(source, destination);
    }

    public string[] GetFilesInDirectory(string directoryPath)
    {
        var fullPath = GetFullPath(directoryPath);
        if (!Directory.Exists(fullPath))
            return Array.Empty<string>();

        return Directory
            .GetFiles(fullPath)
            .Select(Path.GetFileName)
            .Where(name => !string.IsNullOrEmpty(name))
            .ToArray()!;
    }

    public string[] GetDirectoriesInDirectory(string directoryPath)
    {
        var fullPath = GetFullPath(directoryPath);
        if (!Directory.Exists(fullPath))
            return Array.Empty<string>();

        return Directory
            .GetDirectories(fullPath)
            .Select(Path.GetFileName)
            .Where(name => !string.IsNullOrEmpty(name))
            .ToArray()!;
    }

    public void CreateDirectory(string directoryPath)
    {
        Directory.CreateDirectory(GetFullPath(directoryPath));
    }

    public void DeleteDirectory(string directoryPath)
    {
        var fullPath = GetFullPath(directoryPath);
        if (Directory.Exists(fullPath))
            Directory.Delete(fullPath, recursive: true);
    }

    public void DeleteTemporaryFiles(string directoryPath)
    {
        var fullPath = GetFullPath(directoryPath);
        if (!Directory.Exists(fullPath))
            return;

        foreach (var file in Directory.GetFiles(fullPath, "*.tmp", SearchOption.TopDirectoryOnly))
        {
            try
            {
                File.Delete(file);
            }
            catch { }
        }
    }

    public DateTimeOffset GetLastModifiedTime(string path)
    {
        var fullPath = GetFullPath(path);
        return File.Exists(fullPath)
            ? new DateTimeOffset(File.GetLastWriteTimeUtc(fullPath), TimeSpan.Zero)
            : DateTimeOffset.UnixEpoch;
    }

    public int GetFileSize(string path)
    {
        var fullPath = GetFullPath(path);
        if (!File.Exists(fullPath))
            return 0;

        var length = new FileInfo(fullPath).Length;
        return length > int.MaxValue ? int.MaxValue : (int)length;
    }

    public void SetLastModifiedTime(string path, DateTimeOffset time)
    {
        var fullPath = GetFullPath(path);
        if (File.Exists(fullPath))
            File.SetLastWriteTimeUtc(fullPath, time.UtcDateTime);
    }

    public string GetFullPath(string path)
    {
        var cleanPath = NormalizeRelativePath(path);
        var fullPath = Path.GetFullPath(Path.Combine(_dir, cleanPath));
        if (!fullPath.StartsWith(_dir, StringComparison.Ordinal))
            throw new InvalidOperationException($"Save path escaped saves dir: {path}");
        return fullPath;
    }

    private void WriteAtomically(string path, Action<string> writeTemp)
    {
        var fullPath = GetFullPath(path);
        Directory.CreateDirectory(Path.GetDirectoryName(fullPath)!);

        var tempPath = fullPath + ".tmp";
        writeTemp(tempPath);

        if (File.Exists(fullPath))
            File.Delete(fullPath);
        File.Move(tempPath, fullPath);
    }

    private static string NormalizeRelativePath(string path)
    {
        if (string.IsNullOrWhiteSpace(path))
            return string.Empty;

        var cleanPath = path.Replace('\\', '/');
        if (cleanPath.StartsWith("user://", StringComparison.OrdinalIgnoreCase))
            cleanPath = cleanPath["user://".Length..];

        while (cleanPath.StartsWith("/"))
            cleanPath = cleanPath[1..];

        return cleanPath;
    }
}

