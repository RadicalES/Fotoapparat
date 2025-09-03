package io.fotoapparat.preview

import android.util.Size


/**
 * Frame of the preview stream.
 */
data class Frame(

    /**
     * Image in NV21 format.
     */
    val image: ByteArray,

    /**
     * Clockwise rotation of the image in degrees
     */
    val rotation: Int,

    /**
     * Width of image
     */
    val width: Int,

    /**
     * height of image
     */
    val height: Int

) {

    override fun toString(): String {
        val size = Size(width, height)
        return "Frame{" +
                "size=" + size +
                ", image= array(" + image + ")" +
                ", rotation=" + rotation +
                '}'
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Frame

        val size = Size(width, height)
        val sizeother = Size(other.width, other.height)

        if (size != sizeother) return false
//        if (!Arrays.equals(image, other.image)) return false
        if (rotation != other.rotation) return false

        return true
    }

    override fun hashCode(): Int {
        val size = Size(width, height)
        var result = size.hashCode()
//        result = 31 * result + Arrays.hashCode(image)
        result = 31 * result + rotation
        return result
    }

}
