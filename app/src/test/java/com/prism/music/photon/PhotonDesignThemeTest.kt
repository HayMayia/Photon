package com.prism.music.photon

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class PhotonDesignThemeTest {
    @Test fun suppliedAmoledThemeUsesFlatBlackAndTeal() {
        assertEquals(Color.Black, PhotonAmoledColorScheme.background)
        assertEquals(Color.Black, PhotonAmoledColorScheme.surface)
        assertEquals(Color(0xFF2DD4BF), PhotonAmoledColorScheme.primary)
        assertEquals(Color(0xFF0F0F0F), PhotonAmoledColorScheme.surfaceContainer)
        assertEquals(Color.White, PhotonAmoledColorScheme.onSurface)
    }
    @Test fun suppliedTypeScaleKeepsCompactReadableHierarchy() {
        assertEquals(20f, PhotonTypography.titleLarge.fontSize.value)
        assertEquals(14f, PhotonTypography.bodyMedium.fontSize.value)
        assertEquals(12f, PhotonTypography.labelSmall.fontSize.value)
        assertEquals(PhotonTitleStyle.fontFamily, PhotonTypography.bodyMedium.fontFamily)
    }
}
