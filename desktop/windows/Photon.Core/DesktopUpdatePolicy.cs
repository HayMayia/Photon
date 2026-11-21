using System.Text.Json;
namespace Photon.Core;
public sealed record DesktopUpdate(string Version, string Page);
public static class DesktopUpdatePolicy
{
    public static DesktopUpdate? Parse(string json, Version installed)
    {
        using var doc = JsonDocument.Parse(json);
        return doc.RootElement.EnumerateArray().Where(r => !r.TryGetProperty("draft", out var d) || d.ValueKind != JsonValueKind.True)
            .Select(r => {
                var tag = r.TryGetProperty("tag_name", out var t) ? t.GetString() ?? "" : "";
                if (!tag.StartsWith("windows-v", StringComparison.Ordinal)) return null;
                var number = tag[9..].Split('-')[0];
                if (!Version.TryParse(number, out var parsed) || parsed <= installed) return null;
                var asset = r.TryGetProperty("assets", out var a) && a.EnumerateArray().Any(v => v.GetProperty("name").GetString() == "Photon-Windows-x64-Setup.exe" && v.GetProperty("browser_download_url").GetString() == $"https://github.com/HayMayia/Photon/releases/download/{tag}/Photon-Windows-x64-Setup.exe");
                return asset ? new DesktopUpdate(number, $"https://github.com/HayMayia/Photon/releases/tag/{tag}") : null;
            }).OfType<DesktopUpdate>().OrderByDescending(u => Version.Parse(u.Version)).FirstOrDefault();
    }
}
