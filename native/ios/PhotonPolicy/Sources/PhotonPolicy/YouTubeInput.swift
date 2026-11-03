import Foundation
public enum YouTubeInput {
    public static func videoID(_ text: String) -> String? {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let parts = URLComponents(string: value), parts.scheme == "https", parts.user == nil, parts.password == nil, parts.port == nil || parts.port == 443, let host = parts.host else { return nil }
        let id: String?
        if host == "youtu.be" { id = String(parts.path.dropFirst()) }
        else if ["youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com"].contains(host) {
            if parts.path == "/watch" { id = parts.queryItems?.first(where: { $0.name == "v" })?.value }
            else if parts.path.hasPrefix("/shorts/") { id = String(parts.path.dropFirst(8)) }
            else { return nil }
        } else { return nil }
        guard let id, id.range(of: "^[A-Za-z0-9_-]{11}$", options: .regularExpression) != nil else { return nil }
        return id
    }
    public static func mediaAllowed(_ url: URL) -> Bool {
        guard let parts = URLComponents(url: url, resolvingAgainstBaseURL: false), parts.scheme == "https", parts.user == nil, parts.password == nil, parts.port == nil || parts.port == 443, let host = parts.host else { return false }
        return host == "googlevideo.com" || host.hasSuffix(".googlevideo.com")
    }
}
