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
