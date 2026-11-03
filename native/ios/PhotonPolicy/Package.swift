// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "PhotonPolicy", platforms: [.iOS(.v17), .macOS(.v13)], products: [.library(name: "PhotonPolicy", targets: ["PhotonPolicy"])], targets: [.target(name: "PhotonPolicy"), .testTarget(name: "PhotonPolicyTests", dependencies: ["PhotonPolicy"])])
