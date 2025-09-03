package io.fotoapparat.view

import android.content.Context

import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.widget.FrameLayout
import io.fotoapparat.parameter.Resolution
import io.fotoapparat.parameter.ScaleType
import io.fotoapparat.util.projectCenterCrop
import io.fotoapparat.util.projectCenterInside
import io.fotoapparat.util.projectTopCrop
import kotlin.math.roundToInt

/**
 * Uses [android.view.TextureView] as an output for camera.
 */
class CameraView
@JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), CameraRenderer {

    private val surfaceView = CameraSurfaceView(context, attrs, defStyleAttr)

    private lateinit var previewResolution: Resolution
    private lateinit var scaleType: ScaleType
    private var aspectRatio = 0f


    companion object {
        private val TAG = CameraView::class.java.simpleName
    }

    init {
        addView(surfaceView)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    override fun setScaleType(scaleType: ScaleType) {
        this.scaleType = scaleType
    }

    override fun setPreviewResolution(resolution: Resolution) {
        post {
            setAspectRatio(resolution.width, resolution.height)
            previewResolution = resolution
            requestLayout()
        }
    }

    override fun getPreview(): Preview {
        return surfaceView.toPreview()
    }

    /**
     * Sets the aspect ratio for this view. The size of the view will be
     * measured based on the ratio calculated from the parameters.
     *
     * @param width  Camera resolution horizontal size
     * @param height Camera resolution vertical size
     */
    private fun setAspectRatio(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Size cannot be negative" }
        aspectRatio = width.toFloat() / height.toFloat()
        surfaceView.holder.setFixedSize(width, height)
        requestLayout()
    }

    inner class CameraSurfaceView(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) :
        SurfaceView(context, attrs, defStyleAttr) {

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            setMeasuredDimension(width, height)

//            if (aspectRatio == 0f) {
//                setMeasuredDimension(width, height)
//            } else {
//
//                // Performs center-crop transformation of the camera frames
//                val newWidth: Int
//                val newHeight: Int
//                val actualRatio = if (width > height) aspectRatio else 1f / aspectRatio
//                if (width < height * actualRatio) {
//                    newHeight = height
//                    newWidth = (height * actualRatio).roundToInt()
//                } else {
//                    newWidth = width
//                    newHeight = (width / actualRatio).roundToInt()
//                }
//
//                Log.d(TAG, "onMeasure dimensions set: $newWidth x $newHeight")
//                setMeasuredDimension(newWidth, newHeight)
//            }
        }
    }
}


