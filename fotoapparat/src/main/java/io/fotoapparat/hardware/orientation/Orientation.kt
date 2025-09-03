package io.fotoapparat.hardware.orientation

import android.view.Surface

typealias DeviceOrientation = Orientation
typealias DisplayOrientation = Orientation

/**
 * The device orientation.
 */
sealed class Orientation(
        val degrees: Int
) {

    /**
     * A vertical device orientation.
     */
    sealed class Vertical(degrees: Int) : Orientation(degrees) {

        /**
         * A vertical, normal orientation.
         */
        object Portrait : Vertical(0) {
            override fun toString(): String = "Orientation.Vertical.Portrait"
        }

        /**
         * A reversed (flipped phone) orientation.
         */
        object ReversePortrait : Vertical(180) {
            override fun toString(): String = "Orientation.Vertical.ReversePortrait"
        }

    }

    /**
     * A horizontal device orientation.
     */
    sealed class Horizontal(degrees: Int) : Orientation(degrees) {

        /**
         * A 90 degrees clockwise from "normal", orientation.
         */
        object Landscape : Horizontal(90) {
            override fun toString(): String = "Orientation.Horizontal.Landscape"
        }

        /**
         * A 90 degrees counter-clockwise from "normal", orientation.
         */
        object ReverseLandscape : Horizontal(270) {
            override fun toString(): String = "Orientation.Horizontal.ReverseLandscape"
        }

    }
}

internal fun Orientation.toSurface(): Int {
    return when (this) {
        Orientation.Vertical.Portrait -> Surface.ROTATION_0
        Orientation.Horizontal.Landscape -> Surface.ROTATION_90
        Orientation.Vertical.ReversePortrait -> Surface.ROTATION_180
        Orientation.Horizontal.ReverseLandscape -> Surface.ROTATION_270
    }
}

internal fun Int.toOrientation(): Orientation {
    return when (this) {
        0, 360 -> Orientation.Vertical.Portrait
        90 -> Orientation.Horizontal.Landscape
        180 -> Orientation.Vertical.ReversePortrait
        270 -> Orientation.Horizontal.ReverseLandscape
        else -> throw IllegalArgumentException("Cannot convert $this to absolute Orientation.")
    }
}

/* device display is reported CCW */
internal fun Int.toCCWOrientation(): Orientation {
    return when (this) {
        0, 360 -> Orientation.Vertical.Portrait
        90 -> Orientation.Horizontal.Landscape
        180 -> Orientation.Vertical.ReversePortrait
        270 -> Orientation.Horizontal.ReverseLandscape
        else -> throw IllegalArgumentException("Cannot convert $this to absolute Orientation.")
    }
}

fun Int.toSurface(): Int {
    return when (this) {
        0, 360 -> Surface.ROTATION_0
        90 -> Surface.ROTATION_90
        180 -> Surface.ROTATION_180
        270 -> Surface.ROTATION_270
        else -> throw IllegalArgumentException("Cannot convert $this to absolute Orientation.")
    }
}

fun Int.fromSurfaceToDegrees(): Int {
    return when (this) {
        Surface.ROTATION_0 -> 0
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> throw IllegalArgumentException("Cannot convert $this to degrees, surface rotation invalid")
    }
}
