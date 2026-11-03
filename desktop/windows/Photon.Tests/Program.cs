using Photon.Core;
var count = 0;
void Check(bool condition, string label) { if (!condition) throw new Exception(label); count++; }
foreach (var source in new[] { "https://youtu.be/abcdefghijk", "https://www.youtube.com/watch?v=abcdefghijk", "https://music.youtube.com/playlist?list=PL_example", "https://www.youtube.com/@creator/videos" }) Check(YouTubePolicy.TrySource(source, out _), "Valid source");
foreach (var source in new[] { "http://youtube.com/watch?v=abcdefghijk", "https://youtube.com.evil.example/watch?v=abcdefghijk", "https://user@youtube.com/watch?v=abcdefghijk", "https://youtube.com:9999/watch?v=abcdefghijk", "file:///C:/secrets", "https://evil.example/" }) Check(!YouTubePolicy.TrySource(source, out _), "Reject untrusted source");
Check(YouTubePolicy.SearchSource("cats") == "ytsearch20:cats", "Search namespace");
foreach (var source in new[] { "--exec=calc.exe", "file:///C:/secrets", "https://evil.example/", "bad\noption" }) { try { YouTubePolicy.SearchSource(source); throw new Exception("Unsafe source accepted"); } catch (ArgumentException) { count++; } }
Check(YouTubePolicy.MediaUri("https://rr1.googlevideo.com/videoplayback?x=1").Host == "rr1.googlevideo.com", "Media host");
foreach (var source in new[] { "https://evilgooglevideo.com/videoplayback", "http://rr1.googlevideo.com/videoplayback", "https://user@rr1.googlevideo.com/a", "https://youtube.com/a" }) { try { YouTubePolicy.MediaUri(source); throw new Exception("Unsafe media accepted"); } catch (InvalidDataException) { count++; } }
Check(YouTubePolicy.Artwork("https://evil.example/image.jpg") == null, "Artwork host");
var cards = ExtractorJson.Cards("""{"entries":[{"id":"abcdefghijk","title":"Actual title","channel":"Creator","url":"https://www.youtube.com/shorts/abcdefghijk"},{"id":"abcdefghijk","title":"Duplicate"},{"id":"otherVideo1","title":"Portrait 360 Short","height":1920,"width":1080},{"id":"invalid","title":"Bad"}]}""");
Check(cards.Count == 2, "Canonical deduplication"); Check(cards[0].IsShort, "Explicit Shorts route"); Check(!cards[1].IsShort, "No title/aspect guessing");
var playback = ExtractorJson.Playback("""{"id":"abcdefghijk","title":"Source","formats":[{"ext":"mp4","vcodec":"avc1","acodec":"aac","height":720,"url":"https://rr1.googlevideo.com/videoplayback"},{"ext":"mp4","vcodec":"avc1","acodec":"none","height":1080,"url":"https://rr1.googlevideo.com/video"},{"ext":"mp4","vcodec":"avc1","acodec":"aac","height":2160,"url":"https://evil.example/video"}]}""");
Check(playback.Streams.Count == 1 && playback.Streams[0].Label == "720p", "Only genuine playable combined formats");
Console.WriteLine($"PASS: {count} core checks. No device, live-account or real playback certification.");
Check(!YouTubePolicy.IsVideoId("abcdefghijk\n"), "Strict ID boundary");
AudioChoice Track(string id, int channels, bool original, bool dubbed = false, double preference = 0) => new(id, "https://rr1.googlevideo.com/" + id, "eac3", "en", channels, 384, original, dubbed, preference);
Check(SourceAudioPolicy.Pick([Track("stereo", 2, true), Track("six", 6, true)])?.Id == "six", "Original six channels preferred");
Check(SourceAudioPolicy.Pick([Track("six", 6, true), Track("eight", 8, true)])?.Channels == 8, "No six-channel cap");
Check(SourceAudioPolicy.Pick([Track("eight", 8, true), Track("twelve", 12, true)])?.Channels == 12, "No eight-channel app cap");
Check(SourceAudioPolicy.Pick([Track("original", 2, true), Track("dub", 8, false, true)])?.Id == "original", "No dubbed surround substitution");
Check(SourceAudioPolicy.Pick([Track("original", 2, true), Track("unknown", 12, false)])?.Id == "original", "Original language before extra channels");
Check(SourceAudioPolicy.Pick([Track("dub", 6, false, true)]) == null, "Do not invent safe soundtrack");
Check(SourceAudioPolicy.Pick([Track("default", 2, false, preference: 10), Track("other-language", 8, false)])?.Id == "default", "Default source language before channels if original unverified");
var spatial = ExtractorJson.Playback("""{"id":"abcdefghijk","title":"Actual source","formats":[{"format_id":"137","ext":"mp4","vcodec":"avc1","acodec":"none","height":1080,"url":"https://rr1.googlevideo.com/video"},{"format_id":"18","ext":"mp4","vcodec":"avc1","acodec":"aac","height":360,"url":"https://rr1.googlevideo.com/muxed"},{"format_id":"328","vcodec":"none","acodec":"eac3","format_note":"English original","language":"en","audio_channels":6,"abr":384,"url":"https://rr1.googlevideo.com/audio"}],"subtitles":{"en":[{"ext":"vtt","url":"https://www.youtube.com/api/timedtext?lang=en"}],"evil":[{"ext":"vtt","url":"https://evil.example/caption"}]}}""");
Check(spatial.Streams.Single().VideoOnly && spatial.Streams[0].Height == 1080, "Separate HD video selected, not stereo muxed fallback");
Check(SourceAudioPolicy.Pick(spatial.Audio)?.Channels == 6, "Actual source channels retained in parser");
Check(spatial.Captions.Count == 2, "Caption allowlist and explicit off");
var remux = MediaTools.CopyArguments("video.source", "audio.source", "out.partial");
Check(remux.Contains("copy") && !remux.Contains("-ac") && !remux.Contains("-af") && remux.Contains("1:a:0"), "Save selected source audio without transcoding/downmix");
Check(remux.Contains("matroska"), "Codec-preserving download container");
var versions = """[{"tag_name":"v9.9.9-beta","assets":[]},{"tag_name":"windows-v0.3.0-beta","assets":[{"name":"Photon-Windows-x64-Setup.exe","browser_download_url":"https://github.com/HayMayia/Photon/releases/download/windows-v0.3.0-beta/Photon-Windows-x64-Setup.exe"}]}]""";
Check(DesktopUpdatePolicy.Parse(versions, new Version(0, 2, 0))?.Version == "0.3.0", "Windows update not Android release");
Check(DesktopUpdatePolicy.Parse(versions, new Version(0, 3, 0)) == null, "No same-version updater");
Console.WriteLine($"PASS: {count} total core checks, including original multichannel/no-downmix selection. Output hardware remains unverified.");
