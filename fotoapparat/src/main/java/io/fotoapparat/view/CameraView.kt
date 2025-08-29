package io.fotoapparat.view

import android.content.Context
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import io.fotoapparat.exception.camera.UnavailableSurfaceException
import io.fotoapparat.parameter.Resolution
import io.fotoapparat.parameter.ScaleType
import java.util.concurrent.CountDownLatch

/**
 * Uses [android.view.TextureView] as an output for camera.
 */
class CameraView
@JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), CameraRenderer {

    private val textureLatch = CountDownLatch(1)
    private val textureView = TextureView(context)

    private lateinit var previewResolution: Resolution
    private lateinit var scaleType: ScaleType
    private var surfaceTexture: SurfaceTexture? = textureView.tryInitialize()

    init {
        addView(textureView)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        textureLatch.countDown()
    }


    override fun setScaleType(scaleType: ScaleType) {
        this.scaleType = scaleType
    }

    override fun setPreviewResolution(resolution: Resolution) {
        post {
            previewResolution = resolution
            requestLayout()
        }
    }

    override fun getPreview(): Preview {
        val pv = surfaceTexture?.toPreview() ?: getPreviewAfterLatch()
        return textureView.toPreview()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (isInEditMode || !::previewResolution.isInitialized || !::scaleType.isInitialized) {
            super.onLayout(changed, left, top, right, bottom)
        } else {
            layoutTextureView(previewResolution, scaleType)
        }
    }

    private fun getPreviewAfterLatch(): Preview.Texture {
        textureLatch.await()
        return surfaceTexture?.toPreview() ?: throw UnavailableSurfaceException()
    }

    private fun TextureView.tryInitialize() = surfaceTexture ?: null.also {
        surfaceTextureListener = TextureAvailabilityListener {
            this@CameraView.surfaceTexture = this
            textureLatch.countDown()
        }
    }

}

private fun ViewGroup.layoutTextureView(
        previewResolution: Resolution?,
        scaleType: ScaleType?
) = when (scaleType) {
    ScaleType.CenterInside -> previewResolution?.centerInside(this)
    ScaleType.CenterCrop -> previewResolution?.centerCrop(this)
    ScaleType.TopCrop -> previewResolution?.topCrop(this)
    else -> null
}

private fun Resolution.centerInside(view: ViewGroup) {
    val vw = view.measuredWidth
    val vh = view.measuredHeight
    val scale = Math.min(
            vw / width.toFloat(),
            vh / height.toFloat()
    )

    val w = (width * scale).toInt()
    val h = (height * scale).toInt()

    val extraX = Math.max(0, vw - w)
    val extraY = Math.max(0, vh - h)

    val rect = Rect(
            extraX / 2,
            extraY / 2,
            w + extraX / 2,
            h + extraY / 2
    )

    view.layoutChildrenAt(rect)
}

private fun Resolution.centerCrop(view: ViewGroup) {
    val vw = view.measuredWidth
    val vh = view.measuredHeight
    val scale = Math.max(
        vw / width.toFloat(),
        vh / height.toFloat()
    )

    val w = (width * scale).toInt()
    val h = (height * scale).toInt()

    val extraX = Math.max(0, w - vw)
    val extraY = Math.max(0, h - vh)

    val rect = Rect(
            -extraX / 2,
            -extraY / 2,
            w + extraX / 2,
            h + extraY / 2
    )

    view.layoutChildrenAt(rect)
}

private fun Resolution.topCrop(view: ViewGroup) {
    val vw = view.measuredWidth
    val vh = view.measuredHeight
    val scale = Math.max(
            vw / width.toFloat(),
            vh / height.toFloat()
    )

    val w = (width * scale).toInt()
    val h = (height * scale).toInt()

    val extraX = Math.max(0, w - vw)

    val rect = Rect(
            -extraX / 2,
            0,
            w + extraX / 2,
            h
    )

    view.layoutChildrenAt(rect)
}


private fun ViewGroup.layoutChildrenAt(rect: Rect) {
    (0 until childCount).forEach {
        getChildAt(it).layout(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom
        )
    }
}
