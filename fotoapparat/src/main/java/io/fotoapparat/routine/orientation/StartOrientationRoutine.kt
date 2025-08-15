package io.fotoapparat.routine.orientation

import android.util.Log
import io.fotoapparat.concurrent.CameraExecutor.Operation
import io.fotoapparat.hardware.Device
import io.fotoapparat.hardware.orientation.OrientationSensor
import io.fotoapparat.hardware.orientation.OrientationState
import io.fotoapparat.util.lineSeparator

/**
 * Starts orientation monitoring routine.
 */
internal fun Device.startOrientationMonitoring(
        orientationSensor: OrientationSensor
) {
    orientationSensor.start { orientationState: OrientationState ->
        executor.execute(Operation(cancellable = true) {
            val cameraDevice = getSelectedCamera()
            cameraDevice.setDisplayOrientation(orientationState)
        })
    }
}
