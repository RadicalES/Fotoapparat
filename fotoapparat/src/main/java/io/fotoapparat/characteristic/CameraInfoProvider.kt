@file:Suppress("DEPRECATION")

package io.fotoapparat.characteristic

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import io.fotoapparat.hardware.orientation.toOrientation

/**
 * Returns the [Characteristics] for the given `cameraId`.
 */
internal fun getCharacteristics(cameraManager: CameraManager, cameraId: String): Characteristics {
    val camCharacteristic = cameraManager.getCameraCharacteristics(cameraId)
    val lensPosition = camCharacteristic.get(CameraCharacteristics.LENS_FACING)!!.toLensPosition()
    val lensRotation = camCharacteristic.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
    val orientation = lensRotation.toOrientation()

    return Characteristics(
            cameraId = cameraId,
            lensPosition = lensPosition,
            lensRotation = lensRotation,
            cameraOrientation = orientation,
            isMirrored = lensPosition == LensPosition.Front
    )
}
