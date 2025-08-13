@file:Suppress("DEPRECATION")

package io.fotoapparat.parameter.camera.convert

import android.hardware.Camera
import io.fotoapparat.parameter.FocusMode


/**
 * Maps between [FocusMode] and Camera v1 focus codes.
 *
 * @receiver Code of focus mode as in [Camera.Parameters].
 * @return [FocusMode] from given camera code. `null` if camera code is not supported.
 *
 */
internal fun String.toFocusMode(): FocusMode? =
        when (this) {
            "EDOF" -> FocusMode.Edof
            "AUTO" -> FocusMode.Auto
            "MACRO" -> FocusMode.Macro
            "FIXED" -> FocusMode.Fixed
            "OFF" -> FocusMode.Off
            "CONTINUOUS" -> FocusMode.ContinuousFocusVideo
            else -> null
        }

/**
 * Maps between [FocusMode] and Camera v1 focus codes.
 *
 * @receiver FocusMode mode.
 * @return code of the focus mode as in [Camera.Parameters].
 */
internal fun FocusMode.toCode(): String =
        when (this) {
            FocusMode.Edof -> "EDOF"
            FocusMode.Auto -> "AUTO"
            FocusMode.Macro -> "MACRO"
            FocusMode.Fixed -> "FIXED"
            FocusMode.ContinuousFocusVideo -> "CONTINUOUS"
            FocusMode.Off -> "OFF"
            FocusMode.ContinuousFocusPicture -> "UNKNOWN"
            FocusMode.Infinity -> "UNKNOWN"
        }