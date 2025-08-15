package io.fotoapparat.view

import android.graphics.SurfaceTexture
import android.view.SurfaceHolder
import android.view.TextureView
import io.fotoapparat.view.Preview.Surface
import io.fotoapparat.view.Preview.Texture
import io.fotoapparat.view.Preview.TextureAsView

/**
 * A camera preview view.
 */
sealed class Preview {

    /**
     * A [SurfaceTexture] camera preview.
     */
    data class Texture(
            val surfaceTexture: SurfaceTexture
    ) : Preview()

    /**
     * A [Surface] camera preview.
     *
     * Wraps [SurfaceHolder].
     */
    data class Surface(
            val surfaceHolder: SurfaceHolder
    ) : Preview()


    data class TextureAsView(
        val view: TextureView
    ) : Preview()

}

/**
 * Creates a new [Texture].
 */
internal fun SurfaceTexture.toPreview() = Texture(surfaceTexture = this)

/**
 * Creates a new [Surface].
 */
internal fun SurfaceHolder.toPreview() = Surface(surfaceHolder = this)

internal fun TextureView.toPreview() = TextureAsView(view = this)

internal fun Preview.toTextureView(): TextureView {
    return when(this) {
        is TextureAsView -> this.view
        else -> throw IllegalStateException("Preview not a TextureView")
    }
}