package io.fotoapparat.util

import android.hardware.camera2.CameraCharacteristics

object CameraUtils {

    private fun computeRelativeRotation(
        characteristics: CameraCharacteristics,
        deviceOrientationDegrees: Int
    ): Int {
        val sensorOrientationDegrees =
            characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

        // Reverse device orientation for front-facing cameras
        val sign = if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
            CameraCharacteristics.LENS_FACING_FRONT
        ) 1 else -1

        return (sensorOrientationDegrees - (deviceOrientationDegrees * sign) + 360) % 360
    }

}