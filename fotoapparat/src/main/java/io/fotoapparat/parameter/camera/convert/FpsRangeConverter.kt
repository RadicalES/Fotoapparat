@file:Suppress("DEPRECATION")

package io.fotoapparat.parameter.camera.convert

import io.fotoapparat.parameter.FpsRange

/**
 * Converts a [IntArray] into a [FpsRange].
 */
internal fun IntArray.toFpsRange(): FpsRange =
        FpsRange(
                this[0],
                this[1]
        )
