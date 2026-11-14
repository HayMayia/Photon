using System.Diagnostics;
using System.Security.Cryptography;
using System.Text.Json;

namespace Photon.Core;
public sealed class MediaTools(string root)
{
    public void Verify()
    {
        var manifest = JsonSerializer.Deserialize<Dictionary<string, string>>(File.ReadAllText(Path.Combine(root, "SHA256-FFMPEG.json"))) ?? throw new InvalidDataException("Missing media tool manifest.");
        foreach (var pair in manifest)
        {
            if (Path.GetFileName(pair.Key) != pair.Key) throw new InvalidDataException("Invalid media tool path.");
            using var file = File.OpenRead(Path.Combine(root, pair.Key));
            if (!CryptographicOperations.FixedTimeEquals(SHA256.HashData(file), Convert.FromHexString(pair.Value))) throw new InvalidDataException("Media tool integrity check failed.");
        }
        if (!manifest.ContainsKey("ffmpeg.exe") || !manifest.ContainsKey("ffprobe.exe")) throw new InvalidDataException("Media tools missing.");
    }
    public static string[] CopyArguments(string video, string? audio, string output) => audio == null
        ? ["-nostdin", "-hide_banner", "-loglevel", "error", "-y", "-i", video, "-map", "0:v:0", "-map", "0:a:0", "-c", "copy", "-f", "matroska", output]
        : ["-nostdin", "-hide_banner", "-loglevel", "error", "-y", "-i", video, "-i", audio, "-map", "0:v:0", "-map", "1:a:0", "-c", "copy", "-f", "matroska", output];
    public async Task RemuxAsync(string video, string? audio, string output, CancellationToken token)
    {
        Verify();
        var info = new ProcessStartInfo(Path.Combine(root, "ffmpeg.exe")) { UseShellExecute = false, CreateNoWindow = true, RedirectStandardError = true, RedirectStandardOutput = true, WorkingDirectory = root };
        foreach (var arg in CopyArguments(video, audio, output)) info.ArgumentList.Add(arg);
        using var process = Process.Start(info) ?? throw new IOException("Could not start remuxer.");
        using var cancellation = token.Register(() => { try { if (!process.HasExited) process.Kill(entireProcessTree: true); } catch (InvalidOperationException) { } });
        var error = process.StandardError.ReadToEndAsync(token); var outputLog = process.StandardOutput.ReadToEndAsync(token);
        await process.WaitForExitAsync(token); await error; await outputLog;
        if (process.ExitCode != 0 || !File.Exists(output) || new FileInfo(output).Length == 0) throw new IOException("Source-preserving remux failed. No stereo conversion was attempted.");
    }
}
