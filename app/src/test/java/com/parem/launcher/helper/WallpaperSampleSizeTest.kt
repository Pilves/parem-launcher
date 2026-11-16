package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperSampleSizeTest {

    @Test
    fun fallbackWallpapersOnTabletAndPhone() {
        // Dark fallback: the crop needs 0.59x, so any sampling would have to be upscaled back
        assertEquals(1, wallpaperSampleSize(3456, 4320, 1600, 2560))
        assertEquals(1, wallpaperSampleSize(3456, 4320, 1080, 2400))
        // Light fallback: 1688x3000 still covers both screens
        assertEquals(2, wallpaperSampleSize(3376, 6000, 1600, 2560))
        assertEquals(2, wallpaperSampleSize(3376, 6000, 1080, 2400))
        // Daily wallpapers are served 2000 wide
        assertEquals(1, wallpaperSampleSize(2000, 3000, 1600, 2560))
    }

    @Test
    fun exactMultiplesSampleAllTheWayDown() {
        assertEquals(2, wallpaperSampleSize(3200, 5120, 1600, 2560))
        assertEquals(4, wallpaperSampleSize(6400, 10240, 1600, 2560))
    }

    @Test
    fun landscapeScreenUsesItsOwnAxes() {
        assertEquals(1, wallpaperSampleSize(3376, 6000, 2560, 1600))
        assertEquals(2, wallpaperSampleSize(6000, 3376, 2560, 1600))
    }

    @Test
    fun smallOrUnreadableImagesAreNotSampled() {
        assertEquals(1, wallpaperSampleSize(4, 4, 1600, 2560))
        // A failed bounds pass reports -1
        assertEquals(1, wallpaperSampleSize(-1, -1, 1600, 2560))
        assertEquals(1, wallpaperSampleSize(3376, 6000, 0, 0))
    }

    @Test
    fun sampleIsTheLargestThatNeverUpscalesTheCrop() {
        val sources = listOf(1000, 1599, 1600, 2000, 2667, 3199, 3200, 3376, 3456, 4320, 6000, 8000, 12000)
        val screens = listOf(720 to 1600, 1080 to 2400, 1600 to 2560, 2560 to 1600, 1440 to 3120)
        for (w in sources) for (h in sources) for ((sw, sh) in screens) {
            val k = wallpaperSampleSize(w, h, sw, sh)
            val case = "${w}x$h on ${sw}x$sh -> $k"
            assertEquals(case, 0, k and (k - 1))
            // Both axes covering the screen is exactly "the centre-crop scale stays <= 1"
            if (k > 1) assertTrue(case, w / k >= sw && h / k >= sh)
            assertFalse(case, w / (2 * k) >= sw && h / (2 * k) >= sh)
        }
    }
}
