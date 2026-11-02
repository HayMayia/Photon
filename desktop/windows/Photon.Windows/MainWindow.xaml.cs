using System.ComponentModel;
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
        _clock.Tick += (_, _) => { if (!_closed && !_dragging && _player.Length > 0) Timeline.Value = 1000d * _player.Time / _player.Length; }; _clock.Start();
        PreviewKeyDown += (_, e) => { if (e.Key == Key.Escape && _full) { FullscreenClick(this, new RoutedEventArgs()); e.Handled = true; } };
        Timeline.KeyUp += (_, _) => ApplySeek();
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
            Status.Text = cards.Count == 0 ? "No supported public videos returned." : $"{cards.Count} results · first page only in this beta. Select a video.";
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
            _changingQuality = true; Quality.ItemsSource = _playback.Streams; Quality.SelectedIndex = 0; _changingQuality = false;
            PlayChoice(_playback.Streams[0], 0); Status.Text = "Source audio/video · combined MP4 formats only. No account history is written.";
        }
        catch (Exception error) { _changingQuality = false; Report(error, job); }
    }
    private void PlayChoice(StreamChoice choice, long position)
    {
        _player.Stop(); _media?.Dispose(); _media = new(_vlc, YouTubePolicy.MediaUri(choice.Url));
        _media.AddOption(":http-user-agent=" + choice.UserAgent); _media.AddOption(":http-referrer=" + choice.Referer);
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
    private async void DownloadClick(object sender, RoutedEventArgs e)
    {
        if (_playback == null || Quality.SelectedItem is not StreamChoice choice) { Status.Text = "Open a video first."; return; }
        var dialog = new SaveFileDialog { Filter = "MP4 video|*.mp4", FileName = "Photon-" + _playback.Video.Id + ".mp4", AddExtension = true, DefaultExt = ".mp4", OverwritePrompt = true };
        if (dialog.ShowDialog(this) != true) return;
        var job = BeginOperation(); var temporary = dialog.FileName + "." + Guid.NewGuid().ToString("N") + ".partial";
        try
        {
            using var client = new HttpClient(new HttpClientHandler { AllowAutoRedirect = false }) { Timeout = Timeout.InfiniteTimeSpan };
            var uri = YouTubePolicy.MediaUri(choice.Url); HttpResponseMessage? response = null;
            try
            {
                for (var redirects = 0; redirects <= 5; redirects++)
                {
                    using var request = new HttpRequestMessage(HttpMethod.Get, uri);
                    request.Headers.TryAddWithoutValidation("User-Agent", choice.UserAgent); request.Headers.Referrer = new(choice.Referer);
                    response = await client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, job.Token);
                    if ((int)response.StatusCode is >= 300 and < 400 && response.Headers.Location is { } location)
                    { uri = YouTubePolicy.MediaUri(new Uri(uri, location).AbsoluteUri); response.Dispose(); response = null; continue; }
                    break;
                }
                if (response == null || !response.IsSuccessStatusCode) throw new IOException("Download failed.");
                var total = response.Content.Headers.ContentLength;
                using (var output = new FileStream(temporary, FileMode.CreateNew, FileAccess.Write, FileShare.None, 65536, useAsync: true))
                using (var input = await response.Content.ReadAsStreamAsync(job.Token))
                {
                    var buffer = new byte[65536]; long bytes = 0;
                    while (true)
                    {
                        using var readDeadline = CancellationTokenSource.CreateLinkedTokenSource(job.Token); readDeadline.CancelAfter(TimeSpan.FromSeconds(35));
                        var count = await input.ReadAsync(buffer, readDeadline.Token); if (count == 0) break;
                        await output.WriteAsync(buffer.AsMemory(0, count), job.Token); bytes += count;
                        Status.Text = total > 0 ? $"Downloading · {100 * bytes / total}%" : $"Downloading · {bytes / 1048576} MB";
                    }
                    if (bytes == 0 || total is > 0 && bytes != total) throw new IOException("Incomplete download.");
                }
                job.Token.ThrowIfCancellationRequested(); File.Move(temporary, dialog.FileName, overwrite: true); Status.Text = "Download saved.";
            }
            finally { response?.Dispose(); }
        }
        catch (Exception error) { Report(error, job); }
        finally { if (File.Exists(temporary)) File.Delete(temporary); }
    }
    private void WindowClosing(object? sender, CancelEventArgs e)
    {
        _closed = true; _clock?.Stop(); _operation?.Cancel(); Video.MediaPlayer = null;
        _player.Stop(); _media?.Dispose(); _player.Dispose(); _vlc.Dispose();
    }
}
