package io.fotoapparat.selector

import android.graphics.ImageFormat

typealias ImageFormatSelector = Iterable<Int>.() -> Int?

/**
 * @return Selector function which always provides the highest quality.
 */
fun jpegFormat(): ImageFormatSelector = single(ImageFormat.JPEG)

/**
 * @return Selector function which always provides the lowest quality.
 */
fun yuv420_888(): ImageFormatSelector = single(ImageFormat.YUV_420_888)