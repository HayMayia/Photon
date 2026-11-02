import SwiftUI
import AVKit
import YouTubeKit
import PhotonPolicy

@MainActor
final class PlaybackModel: ObservableObject {
    @Published var input = ""
    @Published var status = "Paste a public YouTube video link."
    @Published var player: AVPlayer?
    @Published var opening = false
    private var task: Task<Void, Never>?
    private var generation = UUID()
    private var itemStatus: NSKeyValueObservation?
    func open() {
        task?.cancel()
        let request = UUID(); generation = request
        guard let id = YouTubeInput.videoID(input) else { opening = false; status = "Enter an HTTPS YouTube video link."; return }
        opening = true; status = "Opening…"
        task = Task {
            do {
                // Local extraction only: never enable the library's remote WebSocket fallback.
                let streams = try await YouTube(videoID: id, useOAuth: false, allowOAuthCache: false, methods: [.local]).streams
                try Task.checkCancellation()
                guard let stream = streams.filterVideoAndAudio().filter({ $0.isNativelyPlayable && YouTubeInput.mediaAllowed($0.url) }).highestResolutionStream() else {
                    if generation == request { opening = false; status = "No supported combined audio/video stream. Separate-track HD and account-only playback are not ported yet." }
                    return
                }
                try Task.checkCancellation()
                guard generation == request else { return }
                try AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
                try AVAudioSession.sharedInstance().setActive(true)
                player?.pause()
                let item = AVPlayerItem(url: stream.url); item.preferredForwardBufferDuration = 10
                let next = AVPlayer(playerItem: item); next.automaticallyWaitsToMinimizeStalling = true
                itemStatus = item.observe(\.status, options: [.initial, .new]) { observed, _ in
                    Task { @MainActor in
                        guard self.generation == request else { return }
                        if observed.status == .failed { self.status = "Playback failed. Try reopening the video." }
                        else if observed.status == .readyToPlay { self.status = "Public source playback · \(id)" }
                    }
                }
                player = next; next.play(); opening = false; status = "Preparing player…"
            } catch is CancellationError {
                if generation == request { opening = false; status = "Cancelled." }
            } catch {
                if generation == request { opening = false; status = "Video unavailable or extraction failed. This may be restricted content or a YouTube change. Try again." }
            }
        }
    }
    func cancel() { task?.cancel(); generation = UUID(); opening = false; status = "Cancelled." }
    func stop() { cancel(); itemStatus = nil; player?.pause(); player = nil; try? AVAudioSession.sharedInstance().setActive(false) }
}

struct ContentView: View {
    @StateObject private var model = PlaybackModel()
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Photon").font(.largeTitle.bold()).foregroundStyle(Color(red: 0.74, green: 0.65, blue: 1))
                Text("NATIVE iOS PREVIEW · 0.1").font(.caption).foregroundStyle(.secondary)
                TextField("YouTube video link", text: $model.input).textContentType(.URL).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled().textFieldStyle(.roundedBorder).onSubmit { model.open() }
                HStack {
                    Button("Open video") { model.open() }.buttonStyle(.borderedProminent)
                    if model.opening { ProgressView(); Button("Cancel") { model.cancel() } }
                }
                if let player = model.player { VideoPlayer(player: player).aspectRatio(16 / 9, contentMode: .fit).clipShape(RoundedRectangle(cornerRadius: 14)) }
                Text(model.status).font(.callout).foregroundStyle(.secondary)
                Text("Public video-link playback only. Google account, search, playlists, downloads, cast, 360/VR and Android feature parity are not ported yet.").font(.footnote).foregroundStyle(.secondary)
                Text("Local extraction · no account history writes · no remote extraction service").font(.caption).foregroundStyle(.secondary)
            }.padding(24)
        }.background(Color(red: 0.06, green: 0.05, blue: 0.09)).preferredColorScheme(.dark)
    }
}
