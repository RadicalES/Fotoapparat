@file:Suppress("DEPRECATION")

package io.fotoapparat.parameter.camera.convert

import io.fotoapparat.parameter.Resolution
import android.util.Size

/**
 * Converts [Camera.Size] to [Resolution].
 */
fun Size.toResolution(): Resolution = Resolution(width, height)
