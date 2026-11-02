using System.ComponentModel;
using System.IO;
using System.Net.Http;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Threading;
using LibVLCSharp.Shared;
using Microsoft.Win32;
using Photon.Core;

namespace Photon.Windows;
public partial class MainWindow : Window
{
    private readonly ExtractorProcess _extractor;
    private readonly LibVLC _vlc = new("--no-video-title-show", "--network-caching=3000");
    private readonly MediaPlayer _player;
    private readonly DispatcherTimer _clock;
    private CancellationTokenSource? _operation;
    private VideoPlayback? _playback;
    private Media? _media;
    private bool _changingQuality, _dragging, _closed, _full;
    private string? _listSource;
    private int _nextIndex = 1;
    private int _testChannels;
    private string? _updatePage;
    private long _pendingSeek = -1;
    private WindowState _previousState;
    public MainWindow(ExtractorProcess extractor)
    {
        _extractor = extractor; _player = new(_vlc); InitializeComponent(); Video.MediaPlayer = _player;
        Speed.ItemsSource = new[] { 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0 }; Speed.SelectedItem = 1.0;
        _player.Volume = 80;
        _player.Playing += (_, _) => Dispatcher.InvokeAsync(() => { if (!_closed) { var position = Interlocked.Exchange(ref _pendingSeek, -1); if (position >= 0) _player.Time = position; } });
        _player.EncounteredError += (_, _) => Dispatcher.InvokeAsync(() => { if (!_closed) Status.Text = "Playback failed. Select Retry or choose another available quality."; });
        _clock = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(500) };
        _clock.Tick += (_, _) => {
            if (!_closed && !_dragging && _player.Length > 0) Timeline.Value = 1000d * _player.Time / _player.Length;
            if (_testChannels > 0 && _player.IsPlaying) {
                var labels = _testChannels == 6 ? new[] { "Front left", "Front right", "Center", "LFE (low tone)", "Back left", "Back right" } : new[] { "Front left", "Front right", "Center", "LFE (low tone)", "Back left", "Back right", "Side left", "Side right" };
                AudioStatus.Text = $"{_testChannels}-channel PCM test · {labels[Math.Clamp((int)(_player.Time / 2000), 0, labels.Length - 1)]} · source channels, not a receiver certification";
            }
        }; _clock.Start();
        PreviewKeyDown += (_, e) => { if (e.Key == Key.Escape && _full) { FullscreenClick(this, new RoutedEventArgs()); e.Handled = true; } };
        Timeline.KeyUp += (_, _) => ApplySeek();
        Loaded += async (_, _) => {
            try {
                using var client = new HttpClient { Timeout = TimeSpan.FromSeconds(8) }; client.DefaultRequestHeaders.UserAgent.ParseAdd("Photon.Windows/0.2.0");
                var json = await client.GetStringAsync("https://api.github.com/repos/HayMayia/Photon/releases?per_page=30");
                var update = DesktopUpdatePolicy.Parse(json, new Version(0, 2, 0));
                if (!_closed && update != null) { _updatePage = update.Page; UpdateButton.Content = "Update to " + update.Version; UpdateButton.Visibility = Visibility.Visible; }
            } catch (Exception) { /* Optional release discovery never blocks playback. */ }
        };
    }
    private CancellationTokenSource BeginOperation()
    {
        _operation?.Cancel(); _operation?.Dispose(); return _operation = new();
    }
    private void Report(Exception error, CancellationTokenSource job)
    {
        if (_closed || !ReferenceEquals(_operation, job)) return;
        Status.Text = error is OperationCanceledException ? "Cancelled." : error is ArgumentException or InvalidDataException or TimeoutException ? error.Message : "Request failed. Check your connection and try again. Account-only or restricted videos are not supported in this beta.";
    }
    private async void SearchClick(object sender, RoutedEventArgs e)
    {
        var job = BeginOperation();
        try
        {
            var source = YouTubePolicy.SearchSource(SearchBox.Text); Status.Text = "Searching…";
            var json = await _extractor.RunAsync(source, false, job.Token); job.Token.ThrowIfCancellationRequested();
            var cards = ExtractorJson.Cards(json); Results.ItemsSource = cards;
            _listSource = source; _nextIndex = (source.StartsWith("ytsearch20:") ? 20 : 50) + 1;
            MoreButton.IsEnabled = cards.Count == _nextIndex - 1;
            Status.Text = cards.Count == 0 ? "No supported public videos returned." : $"{cards.Count} results · select a video.";
        }
        catch (Exception error) { Report(error, job); }
    }
    private void SearchKeyDown(object sender, KeyEventArgs e) { if (e.Key == Key.Enter) SearchClick(sender, e); }
    private async void ResultSelected(object sender, SelectionChangedEventArgs e) { if (Results.SelectedItem is VideoCard card) await OpenVideo(card); }
    private async Task OpenVideo(VideoCard card)
    {
        var job = BeginOperation();
        try
        {
            Status.Text = "Opening…"; var json = await _extractor.RunAsync(card.Url, true, job.Token); job.Token.ThrowIfCancellationRequested();
            _playback = ExtractorJson.Playback(json); VideoTitle.Text = _playback.Video.Title;
            _changingQuality = true; Quality.ItemsSource = _playback.Streams; Quality.SelectedItem = _playback.Streams.FirstOrDefault(q => q.Height is > 0 and <= 1080) ?? _playback.Streams.Last();
            Audio.ItemsSource = _playback.Audio; Audio.SelectedItem = SourceAudioPolicy.Pick(_playback.Audio);
            Captions.ItemsSource = _playback.Captions; Captions.SelectedIndex = 0; _changingQuality = false;
            PlayChoice((StreamChoice)Quality.SelectedItem, 0); Status.Text = "Source video/audio · no account history is written.";
        }
        catch (Exception error) { _changingQuality = false; Report(error, job); }
    }
    private void PlayChoice(StreamChoice choice, long position)
    {
        _testChannels = 0;
        _player.Stop(); _media?.Dispose(); _media = new(_vlc, YouTubePolicy.MediaUri(choice.Url));
        _media.AddOption(":http-user-agent=" + choice.UserAgent); _media.AddOption(":http-referrer=" + choice.Referer);
        if (choice.VideoOnly) {
            if (Audio.SelectedItem is not AudioChoice audio) throw new InvalidDataException("Select a source audio track.");
            if (!_media.AddSlave(MediaSlaveType.Audio, 4, YouTubePolicy.MediaUri(audio.Url))) throw new IOException("Could not attach source audio. No stereo fallback was substituted.");
            AudioStatus.Text = audio.Label + " · output depends on Windows/device configuration";
        } else AudioStatus.Text = "Combined source soundtrack · no app stereo cap; source channel count unverified";
        if (Captions.SelectedItem is CaptionChoice { Url: { } caption }) _media.AddSlave(MediaSlaveType.Subtitle, 4, caption);
        _pendingSeek = position; if (!_player.Play(_media)) throw new IOException("Playback could not start.");
        if (Speed.SelectedItem is double speed) _player.SetRate((float)speed);
    }
    private void QualityChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_changingQuality || Quality.SelectedItem is not StreamChoice choice) return;
        try { PlayChoice(choice, Math.Max(0, _player.Time)); } catch (Exception) { Status.Text = "Playback failed. Retry the video."; }
    }
    private void SpeedChanged(object sender, SelectionChangedEventArgs e) { if (_player != null && Speed.SelectedItem is double speed) _player.SetRate((float)speed); }
    private void VolumeChanged(object sender, RoutedPropertyChangedEventArgs<double> e) { if (_player != null) _player.Volume = (int)e.NewValue; }
    private void PlayPause(object sender, RoutedEventArgs e) { if (_player.IsPlaying) _player.Pause(); else if (_media != null) _player.Play(); }
    private async void RetryClick(object sender, RoutedEventArgs e) { if (_playback != null) await OpenVideo(_playback.Video); }
    private void SeekBack(object sender, RoutedEventArgs e) { if (_player.Length > 0) _player.Time = Math.Max(0, _player.Time - 10_000); }
    private void SeekForward(object sender, RoutedEventArgs e) { if (_player.Length > 0) _player.Time = Math.Min(_player.Length, _player.Time + 10_000); }
    private void SeekStart(object sender, MouseButtonEventArgs e) { _dragging = true; }
    private void SeekEnd(object sender, MouseButtonEventArgs e) { ApplySeek(); _dragging = false; }
    private void ApplySeek() { if (_player.Length > 0) _player.Time = (long)(_player.Length * Timeline.Value / 1000d); }
    private void CancelClick(object sender, RoutedEventArgs e) { _operation?.Cancel(); }
    private void FullscreenClick(object sender, RoutedEventArgs e)
    {
        if (!_full) { _previousState = WindowState; WindowStyle = WindowStyle.None; WindowState = WindowState.Maximized; }
        else { WindowStyle = WindowStyle.SingleBorderWindow; WindowState = _previousState; }
        _full = !_full; HeaderPanel.Visibility = NoticePanel.Visibility = FooterPanel.Visibility = _full ? Visibility.Collapsed : Visibility.Visible;
        BrowserGrid.ColumnDefinitions[0].Width = new GridLength(_full ? 0 : 330); BrowserGrid.ColumnDefinitions[1].Width = new GridLength(_full ? 0 : 18);
        RootPanel.Margin = new Thickness(_full ? 8 : 24);
    }
    private async void MoreClick(object sender, RoutedEventArgs e)
    {
        if (_listSource == null) return;
        var job = BeginOperation(); MoreButton.IsEnabled = false;
        try {
            var json = await _extractor.RunAsync(_listSource, false, job.Token, _nextIndex); job.Token.ThrowIfCancellationRequested();
            var items = ExtractorJson.Cards(json); var previous = (Results.ItemsSource as IEnumerable<VideoCard>)?.ToArray() ?? [];
            Results.ItemsSource = previous.Concat(items).DistinctBy(v => v.Id).ToArray();
            var pageSize = _listSource.StartsWith("ytsearch20:") ? 20 : 50; _nextIndex += pageSize;
            MoreButton.IsEnabled = items.Count == pageSize; Status.Text = "List updated.";
        } catch (Exception error) { Report(error, job); MoreButton.IsEnabled = true; }
    }
    private void AudioChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_changingQuality || Quality.SelectedItem is not StreamChoice choice) return;
        try { PlayChoice(choice, Math.Max(0, _player.Time)); } catch (Exception) { Status.Text = "Track could not be opened. Retry the video."; }
    }
    private void CaptionChanged(object sender, SelectionChangedEventArgs e) => AudioChanged(sender, e);
    private void UpdateClick(object sender, RoutedEventArgs e) {
        if (_updatePage != null) System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(_updatePage) { UseShellExecute = true });
    }
    private void SoundSettingsClick(object sender, RoutedEventArgs e) {
        try { System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo("ms-settings:sound") { UseShellExecute = true }); }
        catch (Exception) { Status.Text = "Open Windows Settings → System → Sound to select your output."; }
    }
    private void Surround6Click(object sender, RoutedEventArgs e) => PlaySurroundTest(6);
    private void Surround8Click(object sender, RoutedEventArgs e) => PlaySurroundTest(8);
    private void PlaySurroundTest(int channels)
    {
        try {
            var file = Path.Combine(AppContext.BaseDirectory, "tools", channels == 6 ? "test-5.1.wav" : "test-7.1.wav");
            new MediaTools(Path.Combine(AppContext.BaseDirectory, "tools")).Verify();
            if (!File.Exists(file)) throw new IOException("Test source missing.");
            _operation?.Cancel();
            _player.Stop(); _media?.Dispose(); _media = new(_vlc, new Uri(file)); _testChannels = channels; _pendingSeek = -1;
            VideoTitle.Text = channels == 6 ? "5.1 channel-order test" : "7.1 channel-order test";
            if (!_player.Play(_media)) throw new IOException("Test source could not start.");
            Status.Text = "Each channel sounds alone for 1.5 seconds, with a short gap. Use Windows Sound to select the receiver and configure its actual speaker layout. No upmix is enabled.";
        } catch (Exception) { Status.Text = "Surround test unavailable. Reinstall the complete official package."; }
    }
    private async Task FetchAsync(string url, string file, StreamChoice choice, CancellationToken token)
    {
        using var client = new HttpClient(new SocketsHttpHandler { AllowAutoRedirect = false, ConnectTimeout = TimeSpan.FromSeconds(10) }) { Timeout = Timeout.InfiniteTimeSpan };
        var uri = YouTubePolicy.MediaUri(url); HttpResponseMessage? response = null;
        try {
            for (var redirects = 0; redirects <= 5; redirects++) {
                using var request = new HttpRequestMessage(HttpMethod.Get, uri);
                request.Headers.TryAddWithoutValidation("User-Agent", choice.UserAgent); request.Headers.Referrer = new(choice.Referer);
                using var headerDeadline = CancellationTokenSource.CreateLinkedTokenSource(token); headerDeadline.CancelAfter(TimeSpan.FromSeconds(35));
                response = await client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, headerDeadline.Token);
                if ((int)response.StatusCode is >= 300 and < 400 && response.Headers.Location is { } location) {
                    uri = YouTubePolicy.MediaUri(new Uri(uri, location).AbsoluteUri); response.Dispose(); response = null; continue;
                } break;
            }
            if (response == null || !response.IsSuccessStatusCode) throw new IOException("Download failed.");
            var total = response.Content.Headers.ContentLength;
            using var output = new FileStream(file, FileMode.CreateNew, FileAccess.Write, FileShare.None, 65536, true);
            using var input = await response.Content.ReadAsStreamAsync(token);
            var buffer = new byte[65536]; long bytes = 0;
            while (true) {
                using var deadline = CancellationTokenSource.CreateLinkedTokenSource(token); deadline.CancelAfter(TimeSpan.FromSeconds(35));
                var n = await input.ReadAsync(buffer, deadline.Token); if (n == 0) break;
                await output.WriteAsync(buffer.AsMemory(0, n), token); bytes += n;
                if (!_closed) Status.Text = total > 0 ? $"Downloading source · {100 * bytes / total}%" : $"Downloading source · {bytes / 1048576} MB";
            }
            if (bytes == 0 || total is > 0 && bytes != total) throw new IOException("Incomplete download.");
        } finally { response?.Dispose(); }
    }
    private async void DownloadClick(object sender, RoutedEventArgs e)
    {
        if (_playback == null || Quality.SelectedItem is not StreamChoice choice || _testChannels > 0) { Status.Text = "Open a video first."; return; }
        var audio = choice.VideoOnly ? Audio.SelectedItem as AudioChoice : null;
        if (choice.VideoOnly && audio == null) { Status.Text = "Select source audio first."; return; }
        var dialog = new SaveFileDialog { Filter = "Matroska video (source channels preserved)|*.mkv", FileName = "Photon-" + _playback.Video.Id + ".mkv", AddExtension = true, DefaultExt = ".mkv", OverwritePrompt = true };
        if (dialog.ShowDialog(this) != true) return;
        if (!string.Equals(Path.GetExtension(dialog.FileName), ".mkv", StringComparison.OrdinalIgnoreCase)) { Status.Text = "Choose an MKV filename to preserve source codecs."; return; }
        var job = BeginOperation(); var temporary = dialog.FileName + "." + Guid.NewGuid().ToString("N") + ".partial";
        var workspace = Path.Combine(Path.GetTempPath(), "Photon-" + Guid.NewGuid().ToString("N")); Directory.CreateDirectory(workspace);
        try {
            var video = Path.Combine(workspace, "video.source"); var soundtrack = audio == null ? null : Path.Combine(workspace, "audio.source");
            await FetchAsync(choice.Url, video, choice, job.Token);
            if (audio != null) await FetchAsync(audio.Url, soundtrack!, choice, job.Token);
            Status.Text = "Saving original tracks · stream copy, no audio downmix";
            await new MediaTools(Path.Combine(AppContext.BaseDirectory, "tools")).RemuxAsync(video, soundtrack, temporary, job.Token);
            job.Token.ThrowIfCancellationRequested(); File.Move(temporary, dialog.FileName, true); Status.Text = "Saved · original selected channels/codecs retained.";
        } catch (Exception error) { Report(error, job); }
        finally {
            try { if (File.Exists(temporary)) File.Delete(temporary); if (Directory.Exists(workspace)) Directory.Delete(workspace, true); }
            catch (IOException) { if (!_closed && ReferenceEquals(_operation, job)) Status.Text = "Temporary download cleanup failed. Check free space/folder access."; }
            catch (UnauthorizedAccessException) { if (!_closed && ReferenceEquals(_operation, job)) Status.Text = "Temporary download cleanup failed. Check folder access."; }
        }
    }
    private void WindowClosing(object? sender, CancelEventArgs e)
    {
        _closed = true; _clock?.Stop(); _operation?.Cancel(); Video.MediaPlayer = null;
        _player.Stop(); _media?.Dispose(); _player.Dispose(); _vlc.Dispose();
    }
}
