// Compiled by the independent installer workflow against the INSTALLED assembly.
// Dummy outputs exercise LibVLC parsing/playback, not actual speakers or GPU output.
using System;
using System.IO;
using System.Linq;
using System.Threading;
using LibVLCSharp.Shared;
public static class NativeMediaCheck
{
    public static string Run(string root, string nativePath, string video)
    {
        Core.Initialize(nativePath);
        using (var vlc = new LibVLC("--vout=dummy", "--aout=dummy", "--no-video-title-show"))
        {
            foreach (var count in new[] { 6, 8 })
            {
                var file = Path.Combine(root, "tools", count == 6 ? "test-5.1.wav" : "test-7.1.wav");
                using (var audio = new Media(vlc, new Uri(file)))
                {
                    var parsed = audio.Parse(MediaParseOptions.ParseLocal, 10000, CancellationToken.None).GetAwaiter().GetResult();
                    if (parsed != MediaParsedStatus.Done || !audio.Tracks.Any(t => t.TrackType == TrackType.Audio && t.Data.Audio.Channels == count))
                        throw new Exception("LibVLC could not parse the expected source channels: " + count);
                }
                using (var media = new Media(vlc, new Uri(video)))
                using (var player = new MediaPlayer(vlc))
                {
                    if (!media.AddSlave(MediaSlaveType.Audio, 4, new Uri(file)) || !player.Play(media))
                        throw new Exception("Native separate-track attachment/play failed");
                    var deadline = DateTime.UtcNow.AddSeconds(12);
                    while (DateTime.UtcNow < deadline && !(player.IsPlaying && player.AudioTrack >= 0 && player.Time > 100)) Thread.Sleep(50);
                    if (!player.IsPlaying || player.AudioTrack < 0 || player.Time <= 100)
                        throw new Exception("Native separate source audio did not become active: " + count);
                    player.Stop();
                }
            }
        }
        return "Installed LibVLC: 6/8-channel source parsing and separate-track dummy-output playback PASS. No physical output certification.";
    }
}
