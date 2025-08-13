@file:Suppress("DEPRECATION")

package io.fotoapparat.parameter.camera.convert

import android.hardware.camera2.CameraCharacteristics
import io.fotoapparat.parameter.AntiBandingMode

/**
 * Converts an anti banding mode code to a [AntiBandingMode].
 *
 * @receiver Code of the anti banding mode as in [Camera.Parameters].
 * @return The [io.fotoapparat.Fotoapparat]'s camera [AntiBandingMode]. `null` if camera code is not supported.
 */
fun Int.toAntiBandingMode(): AntiBandingMode? =
        when (this) {
            CameraCharacteristics.CONTROL_AE_ANTIBANDING_MODE_AUTO -> AntiBandingMode.Auto
            CameraCharacteristics.CONTROL_AE_ANTIBANDING_MODE_50HZ -> AntiBandingMode.HZ50
            CameraCharacteristics.CONTROL_AE_ANTIBANDING_MODE_60HZ -> AntiBandingMode.HZ60
            CameraCharacteristics.CONTROL_AE_ANTIBANDING_MODE_OFF -> AntiBandingMode.None
            else -> null
        }

/**
 * Converts a [AntiBandingMode] to a antiBandingMode mode code as in [Camera.Parameters].
 *
 * @receiver The [io.fotoapparat.Fotoapparat]'s camera [AntiBandingMode].
 * @return anti banding mode code as in [Camera.Parameters].
 */
fun AntiBandingMode.toCode(): Int =
        when (this) {
            AntiBandingMode.Auto -> CameraCharacteristics.CONTROL_AE_ANTIBANDING_MODE_AUTO
            AntiBandingMode.HZ50 -> CameraCharacteristics.CONTROL_AE_ANTIBANDING_MODE_50HZ
            AntiBandingMode.HZ60 -> CameraCharacteristics.CONTROL_AE_ANTIBANDING_MODE_60HZ
            AntiBandingMode.None -> CameraCharacteristics.CONTROL_AE_ANTIBANDING_MODE_OFF
        }