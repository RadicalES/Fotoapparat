package io.fotoapparat.hardware.orientation

/**
 * Phone orientation states.
 */
data class OrientationState(
        /**
         * The current orientation the device is being hold.
         * This is clock wise rotation from natural
         */
        val deviceOrientation: DeviceOrientation,

        /**
         * The current orientation of the display.
         * This is counter clockwise rotation from natural
         */
        val displayOrientation: DisplayOrientation
)