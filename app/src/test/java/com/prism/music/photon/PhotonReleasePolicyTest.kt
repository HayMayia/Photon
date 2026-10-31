package com.prism.music.photon

import org.junit.Assert.*
import org.junit.Test

class PhotonReleasePolicyTest {
    @Test fun recognizesBetaUpgradeAndDoesNotOfferSameVersion() {
        assertTrue(PhotonReleasePolicy.newer("v1.0.0-beta", "0.13.0-alpha"))
        assertFalse(PhotonReleasePolicy.newer("v1.0.0-beta", "1.0.0 Beta"))
        assertTrue(PhotonReleasePolicy.newer("v1.0.1-beta", "1.0.0 Beta"))
        assertTrue(PhotonReleasePolicy.newer("v1.0.0", "1.0.0 Beta"))
        assertFalse(PhotonReleasePolicy.newer("v1.0.0-beta", "1.0.0"))
        assertTrue(PhotonReleasePolicy.newer("v1.0.0-beta2", "1.0.0 Beta1"))
    }
    @Test fun onlyOfficialPhotonReleaseApkUrlsAreAccepted() {
        assertTrue(PhotonReleasePolicy.trusted("https://github.com/HayMayia/Photon/releases/download/v1.0.0-beta/Photon.apk"))
        assertFalse(PhotonReleasePolicy.trusted("https://github.com/Yigder/Prism/releases/download/v1/Photon.apk"))
        assertFalse(PhotonReleasePolicy.trusted("http://github.com/HayMayia/Photon/releases/download/v1/Photon.apk"))
        assertFalse(PhotonReleasePolicy.trusted("https://github.com.evil.example/HayMayia/Photon/releases/download/v1/Photon.apk"))
        assertFalse(PhotonReleasePolicy.trusted("https://user@github.com/HayMayia/Photon/releases/download/v1/Photon.apk"))
    }
}
