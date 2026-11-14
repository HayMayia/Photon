import XCTest
@testable import PhotonPolicy
final class YouTubeInputTests: XCTestCase {
    func testRealVideoLinks() {
        for source in ["https://youtu.be/abcdefghijk", "https://www.youtube.com/watch?v=abcdefghijk", "https://www.youtube.com/shorts/abcdefghijk", "https://music.youtube.com/watch?v=abcdefghijk"] { XCTAssertEqual(YouTubeInput.videoID(source), "abcdefghijk") }
    }
    func testUntrustedMalformedAndNonVideoLinksAreRejected() {
        for source in ["http://youtu.be/abcdefghijk", "https://youtube.com.evil.example/watch?v=abcdefghijk", "https://user@youtube.com/watch?v=abcdefghijk", "https://youtube.com:9000/watch?v=abcdefghijk", "https://youtu.be/short", "file:///video", "https://www.youtube.com/playlist?list=PL_example"] { XCTAssertNil(YouTubeInput.videoID(source)) }
    }
    func testMediaHostBoundary() {
        XCTAssertTrue(YouTubeInput.mediaAllowed(URL(string: "https://rr1.googlevideo.com/videoplayback")!))
        for source in ["https://evilgooglevideo.com/a", "http://rr1.googlevideo.com/a", "https://user@rr1.googlevideo.com/a", "https://youtube.com/a"] { XCTAssertFalse(YouTubeInput.mediaAllowed(URL(string: source)!)) }
    }
}
