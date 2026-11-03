using System.Text.Json;
using System.Text.RegularExpressions;

namespace Photon.Core;

public static partial class YouTubePolicy
{
    [GeneratedRegex("^[A-Za-z0-9_-]{11}$")] private static partial Regex VideoIdPattern();
    public static bool IsVideoId(string id) => id.Length == 11 && VideoIdPattern().IsMatch(id);
    public static string VideoUrl(string id) => IsVideoId(id) ? $"https://www.youtube.com/watch?v={id}" : throw new ArgumentException("Invalid video ID.");
    public static bool TrySource(string input, out string source)
    {
        source = "";
        if (!Uri.TryCreate(input.Trim(), UriKind.Absolute, out var uri) || uri.Scheme != "https" || !uri.IsDefaultPort || uri.UserInfo.Length != 0) return false;
        if (uri.Host == "youtu.be") { var id = uri.AbsolutePath.Trim('/'); if (!IsVideoId(id)) return false; source = VideoUrl(id); return true; }
        if (uri.Host is not ("youtube.com" or "www.youtube.com" or "m.youtube.com" or "music.youtube.com")) return false;
        if (uri.AbsolutePath != "/watch" && uri.AbsolutePath != "/playlist" && !uri.AbsolutePath.StartsWith("/shorts/") && !uri.AbsolutePath.StartsWith("/channel/") && !uri.AbsolutePath.StartsWith("/@")) return false;
        source = new UriBuilder(uri) { Host = "www.youtube.com", Fragment = "" }.Uri.AbsoluteUri;
        return true;
    }
    public static string SearchSource(string input)
    {
        var value = input.Trim();
        if (value.Length is 0 or > 2000 || value.Any(char.IsControl)) throw new ArgumentException("Enter a search or YouTube link.");
        if (TrySource(value, out var source)) return source;
        if (value.Contains("://") || value.StartsWith('-') || value.StartsWith("file:", StringComparison.OrdinalIgnoreCase)) throw new ArgumentException("Only HTTPS YouTube links are supported.");
        return "ytsearch20:" + value;
    }
    public static Uri MediaUri(string value)
    {
        if (!Uri.TryCreate(value, UriKind.Absolute, out var uri) || uri.Scheme != "https" || !uri.IsDefaultPort || uri.UserInfo.Length != 0 || !(uri.Host == "googlevideo.com" || uri.Host.EndsWith(".googlevideo.com", StringComparison.Ordinal)))
            throw new InvalidDataException("YouTube did not return an allowed media address.");
        return uri;
    }
    public static string? Artwork(string value) => Uri.TryCreate(value, UriKind.Absolute, out var uri) && uri.Scheme == "https" && uri.IsDefaultPort && uri.UserInfo.Length == 0 && uri.Host is "i.ytimg.com" or "yt3.ggpht.com" or "yt3.googleusercontent.com" ? value : null;
}

public sealed record VideoCard(string Id, string Title, string Channel, string? Thumbnail, bool IsShort)
{
    public string Url => YouTubePolicy.VideoUrl(Id);
}
public sealed record AudioChoice(string Id, string Url, string Codec, string Language, int Channels, double Bitrate, bool Original, bool Dubbed, double LanguagePreference)
{
    public string Label => $"{Language} · {(Channels > 0 ? Channels + " source channels" : "channels unknown")} · {Codec}" + (Original ? " · original" : Dubbed ? " · dubbed/descriptive" : " · source language unverified");
}
public sealed record CaptionChoice(string Label, string? Url);
public sealed record StreamChoice(string Label, string Url, string UserAgent, string Referer, bool VideoOnly = false, int Height = 0);
public sealed record VideoPlayback(VideoCard Video, IReadOnlyList<StreamChoice> Streams, IReadOnlyList<AudioChoice> Audio, IReadOnlyList<CaptionChoice> Captions);

public static class SourceAudioPolicy
{
    public static AudioChoice? Pick(IEnumerable<AudioChoice> tracks)
    {
        var safe = tracks.Where(a => !a.Dubbed).ToArray();
        var original = safe.Where(a => a.Original).ToArray();
        // Never trade the original language for extra channels or bitrate.
        var pool = original.Length > 0 ? original : safe.Where(a => a.LanguagePreference >= 0).ToArray();
        if (original.Length == 0 && pool.Length > 0)
        {
            var preference = pool.Max(a => a.LanguagePreference);
            pool = pool.Where(a => a.LanguagePreference == preference).ToArray();
        }
        return pool.OrderByDescending(a => a.Channels).ThenByDescending(a => a.Bitrate).FirstOrDefault();
    }
}

public static class ExtractorJson
{
    private static string Text(JsonElement item, string key) => item.ValueKind == JsonValueKind.Object && item.TryGetProperty(key, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() ?? "" : "";
    private static double Number(JsonElement item, string key) => item.TryGetProperty(key, out var v) && v.ValueKind == JsonValueKind.Number && v.TryGetDouble(out var result) && double.IsFinite(result) ? result : 0;
    private static VideoCard? Card(JsonElement item)
    {
        var id = Text(item, "id"); if (!YouTubePolicy.IsVideoId(id)) return null;
        var title = Text(item, "title"); if (title.Length == 0 || title is "[Deleted video]" or "[Private video]") return null;
        var image = Text(item, "thumbnail");
        if (image.Length == 0 && item.TryGetProperty("thumbnails", out var thumbs) && thumbs.ValueKind == JsonValueKind.Array)
            image = thumbs.EnumerateArray().Select(t => Text(t, "url")).FirstOrDefault(u => YouTubePolicy.Artwork(u) != null) ?? "";
        var page = Text(item, "webpage_url"); if (page.Length == 0) page = Text(item, "url");
        var shortProof = Uri.TryCreate(page, UriKind.Absolute, out var uri) && uri.Scheme == "https" && uri.Host is "www.youtube.com" or "youtube.com" && uri.AbsolutePath == $"/shorts/{id}";
        return new(id, title, Text(item, "channel") is { Length: > 0 } owner ? owner : Text(item, "uploader"), YouTubePolicy.Artwork(image), shortProof);
    }
    public static IReadOnlyList<VideoCard> Cards(string json)
    {
        using var doc = JsonDocument.Parse(json); var root = doc.RootElement;
        var values = root.TryGetProperty("entries", out var entries) && entries.ValueKind == JsonValueKind.Array ? entries.EnumerateArray().ToArray() : [root];
        return values.Where(v => v.ValueKind == JsonValueKind.Object).Select(Card).OfType<VideoCard>().DistinctBy(v => v.Id).ToArray();
    }
    public static VideoPlayback Playback(string json)
    {
        using var doc = JsonDocument.Parse(json); var root = doc.RootElement;
        var card = Card(root) ?? throw new InvalidDataException("Video unavailable.");
        var video = new List<(int Height, StreamChoice Choice)>(); var combined = new List<(int Height, StreamChoice Choice)>();
        var audio = new List<AudioChoice>(); var captions = new List<CaptionChoice> { new("Captions off", null) };
        var agent = "Mozilla/5.0"; var referer = "https://www.youtube.com/";
        if (root.TryGetProperty("http_headers", out var headers))
        {
            var ua = Text(headers, "User-Agent"); if (ua.Length > 0 && !ua.Any(char.IsControl)) agent = ua;
        }
        if (root.TryGetProperty("formats", out var formats) && formats.ValueKind == JsonValueKind.Array)
            foreach (var format in formats.EnumerateArray().Where(f => f.ValueKind == JsonValueKind.Object))
            {
                if (Text(format, "protocol") is not ("" or "https")) continue;
                var url = Text(format, "url");
                try { YouTubePolicy.MediaUri(url); } catch (InvalidDataException) { continue; }
                var hasVideo = Text(format, "vcodec") is not ("none" or "");
                var hasAudio = Text(format, "acodec") is not ("none" or "");
                if (!hasVideo && hasAudio)
                {
                    var note = Text(format, "format_note").ToLowerInvariant();
                    var language = Text(format, "language");
                    audio.Add(new(Text(format, "format_id"), url, Text(format, "acodec"), language.Length > 0 ? language : "Source default", (int)Number(format, "audio_channels"), Number(format, "abr"), note.Contains("original"), note.Contains("dubbed") || note.Contains("descriptive") || note.Contains("audio description"), Number(format, "language_preference")));
                }
                if (!hasVideo || Text(format, "ext") is not ("mp4" or "webm")) continue;
                var height = (int)Number(format, "height");
                var choice = new StreamChoice(height > 0 ? $"{height}p" : "Source", url, agent, referer, !hasAudio, height);
                (hasAudio ? combined : video).Add((height, choice));
            }
        // A video-only stream plus real source audio avoids silently keeping a muxed stereo soundtrack.
        var selected = SourceAudioPolicy.Pick(audio) != null && video.Count > 0 ? video : combined;
        if (selected.Count == 0) throw new InvalidDataException("No compatible video/original audio pair was returned.");
        foreach (var section in new[] { "subtitles", "automatic_captions" })
            if (root.TryGetProperty(section, out var languages) && languages.ValueKind == JsonValueKind.Object)
                foreach (var language in languages.EnumerateObject())
                {
                    if (language.Value.ValueKind != JsonValueKind.Array) continue;
                    foreach (var caption in language.Value.EnumerateArray())
                    {
                        var url = Text(caption, "url");
                        if (Text(caption, "ext") != "vtt" || !Uri.TryCreate(url, UriKind.Absolute, out var uri) || uri.Scheme != "https" || uri.Host != "www.youtube.com" || uri.AbsolutePath != "/api/timedtext" || !uri.IsDefaultPort || uri.UserInfo.Length != 0) continue;
                        captions.Add(new(language.Name + (section == "automatic_captions" ? " · auto" : ""), url)); break;
                    }
                }
        return new(card, selected.OrderByDescending(s => s.Height).DistinctBy(s => s.Height).Select(s => s.Choice).ToArray(), audio.DistinctBy(a => a.Id).ToArray(), captions);
    }
}
