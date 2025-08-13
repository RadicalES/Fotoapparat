package io.fotoapparat.characteristic

import io.fotoapparat.hardware.orientation.Orientation

/**
 * A set of information about the camera.
 */
internal data class Characteristics(
        val cameraId: String,
        val lensPosition: LensPosition,
        val lensRotation: Int,
        val cameraOrientation: Orientation,
        val isMirrored: Boolean
)
