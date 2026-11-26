package dev.localremote.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvGestureMapperTest {
    @Test
    fun shortMovementDoesNotSendAKey() {
        assertNull(TvGestureMapper.toKeyCode(18f, -22f, thresholdPx = 56f))
    }

    @Test
    fun horizontalSwipesMapToLeftAndRight() {
        assertEquals(22, TvGestureMapper.toKeyCode(100f, 12f, thresholdPx = 56f))
        assertEquals(21, TvGestureMapper.toKeyCode(-100f, 12f, thresholdPx = 56f))
    }

    @Test
    fun verticalSwipesMapToUpAndDown() {
        assertEquals(19, TvGestureMapper.toKeyCode(10f, -100f, thresholdPx = 56f))
        assertEquals(20, TvGestureMapper.toKeyCode(10f, 100f, thresholdPx = 56f))
    }

    @Test
    fun dominantAxisWinsForDiagonalSwipes() {
        assertEquals(19, TvGestureMapper.toKeyCode(30f, -90f, thresholdPx = 56f))
        assertEquals(22, TvGestureMapper.toKeyCode(90f, -30f, thresholdPx = 56f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveThresholdIsRejected() {
        TvGestureMapper.toKeyCode(100f, 0f, thresholdPx = 0f)
    }
}