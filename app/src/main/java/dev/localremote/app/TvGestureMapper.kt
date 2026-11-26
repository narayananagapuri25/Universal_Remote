package dev.localremote.app

import kotlin.math.abs

internal object TvGestureMapper {
    fun toKeyCode(deltaX: Float, deltaY: Float, thresholdPx: Float = 56f): Int? {
        require(thresholdPx > 0f) { "Swipe threshold must be positive." }
        if (abs(deltaX) < thresholdPx && abs(deltaY) < thresholdPx) return null
        return if (abs(deltaX) >= abs(deltaY)) {
            if (deltaX >= 0f) KEY_RIGHT else KEY_LEFT
        } else {
            if (deltaY >= 0f) KEY_DOWN else KEY_UP
        }
    }

    private const val KEY_UP = 19
    private const val KEY_DOWN = 20
    private const val KEY_LEFT = 21
    private const val KEY_RIGHT = 22
}