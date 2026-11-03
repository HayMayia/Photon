using System.Diagnostics;
using System.Security.Cryptography;
using System.Text.Json;

namespace Photon.Core;

public sealed class ExtractorProcess(string toolsDirectory)
{
    public void VerifyTools()
    {
        var hashes = JsonSerializer.Deserialize<Dictionary<string, string>>(File.ReadAllText(Path.Combine(toolsDirectory, "SHA256.json"))) ?? throw new InvalidDataException("Missing tool manifest.");
        foreach (var name in new[] { "yt-dlp.exe", "node.exe" })
        {
            if (!hashes.TryGetValue(name, out var expected)) throw new InvalidDataException("Missing tool checksum.");
            using var file = File.OpenRead(Path.Combine(toolsDirectory, name));
            if (!CryptographicOperations.FixedTimeEquals(SHA256.HashData(file), Convert.FromHexString(expected))) throw new InvalidDataException("Bundled tool integrity check failed. Reinstall from the official release.");
        }
    }
    public async Task<string> RunAsync(string source, bool details, CancellationToken token, int startIndex = 1)
    {
        VerifyTools();
        var start = new ProcessStartInfo(Path.Combine(toolsDirectory, "yt-dlp.exe")) { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true, WorkingDirectory = toolsDirectory };
        foreach (var key in new[] { "NODE_OPTIONS", "NODE_PATH", "PYTHONPATH", "PYTHONHOME" }) start.Environment.Remove(key);
        foreach (var arg in new[] { "--ignore-config", "--no-plugin-dirs", "--no-cache-dir", "--no-update", "--no-warnings", "--no-progress", "--socket-timeout", "15", "--retries", "1", "--extractor-retries", "1", "--js-runtimes", "node:" + Path.Combine(toolsDirectory, "node.exe"), "--dump-single-json", "--skip-download" }) start.ArgumentList.Add(arg);
        if (details) { start.ArgumentList.Add("--no-playlist"); start.ArgumentList.Add("--format"); start.ArgumentList.Add("bestvideo+bestaudio/best"); }
        else {
            if (startIndex < 1 || startIndex > 10000) throw new ArgumentOutOfRangeException(nameof(startIndex));
            var pageSize = source.StartsWith("ytsearch20:", StringComparison.Ordinal) ? 20 : 50;
            if (pageSize == 20) source = $"ytsearch{startIndex + pageSize - 1}:" + source[(source.IndexOf(':') + 1)..];
            start.ArgumentList.Add("--flat-playlist"); start.ArgumentList.Add("--playlist-items"); start.ArgumentList.Add($"{startIndex}:{startIndex + pageSize - 1}");
        }
        start.ArgumentList.Add("--"); start.ArgumentList.Add(source);
        using var process = Process.Start(start) ?? throw new IOException("Could not start the bundled extractor.");
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token); timeout.CancelAfter(TimeSpan.FromSeconds(120));
        using var cancel = timeout.Token.Register(() => { try { if (!process.HasExited) process.Kill(entireProcessTree: true); } catch (InvalidOperationException) { } });
        var output = process.StandardOutput.ReadToEndAsync(timeout.Token); var error = process.StandardError.ReadToEndAsync(timeout.Token);
        try { await process.WaitForExitAsync(timeout.Token); }
        catch (OperationCanceledException) { if (token.IsCancellationRequested) throw; throw new TimeoutException("YouTube lookup timed out. Try again."); }
        var json = await output; var diagnostic = await error;
        if (process.ExitCode != 0) throw new IOException(diagnostic.Contains("Sign in", StringComparison.OrdinalIgnoreCase) ? "This video requires sign-in. Google account playback is not yet available in the Windows beta." : "YouTube lookup failed. The video may be unavailable, restricted or temporarily unsupported. Try again.");
        if (json.Length > 32_000_000) throw new InvalidDataException("YouTube response exceeded the limit.");
        return json;
    }
}
