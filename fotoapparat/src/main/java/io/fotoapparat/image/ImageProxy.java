package io.fotoapparat.image;

import static io.fotoapparat.image.ImageUtil.createBitmapFromImageProxy;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.media.Image;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;

public interface ImageProxy extends AutoCloseable {
    /**
     * Closes the underlying {@link android.media.Image}.
     *
     * @see android.media.Image#close()
     */
    @Override
    void close();

    /**
     * Returns the crop rectangle.
     *
     * @see android.media.Image#getCropRect()
     */
    @NonNull
    Rect getCropRect();

    /**
     * Sets the crop rectangle.
     *
     * @see android.media.Image#setCropRect(Rect)
     */
    void setCropRect(@Nullable Rect rect);

    /**
     * Returns the image format.
     *
     * <p> The image format can be one of the {@link ImageFormat} or
     * {@link PixelFormat} constants.
     *
     * @see android.media.Image#getFormat()
     */
    int getFormat();

    /**
     * Returns the image height.
     *
     * @see android.media.Image#getHeight()
     */
    int getHeight();

    /**
     * Returns the image width.
     *
     * @see android.media.Image#getWidth()
     */
    int getWidth();

    /**
     * Returns the array of planes.
     *
     * @see android.media.Image#getPlanes()
     */
    @NonNull
    @SuppressLint("ArrayReturn")
    PlaneProxy[] getPlanes();

    /** A plane proxy which has an analogous interface as {@link android.media.Image.Plane}. */
    interface PlaneProxy {
        /**
         * Returns the row stride.
         *
         * @see android.media.Image.Plane#getRowStride()
         */
        int getRowStride();

        /**
         * Returns the pixel stride.
         *
         * @see android.media.Image.Plane#getPixelStride()
         */
        int getPixelStride();

        /**
         * Returns the pixels buffer.
         *
         * @see android.media.Image.Plane#getBuffer()
         */
        @NonNull
        ByteBuffer getBuffer();
    }

    /** Returns the {@link ImageInfo}. */
    @NonNull
    ImageInfo getImageInfo();

    /**
     * Returns the android {@link Image}.
     *
     * <p>If the ImageProxy is a wrapper for an android {@link Image}, it will return the
     * {@link Image}. It is possible for an ImageProxy to wrap something that isn't an
     * {@link Image}. If that's the case then it will return null.
     *
     * <p>The returned image should not be closed by the application. Instead it should be closed by
     * the ImageProxy, which happens, for example, on return from the {@link ImageAnalysis.Analyzer}
     * function.  Destroying the {@link ImageAnalysis} will close the underlying
     * {@link android.media.ImageReader}.  So an {@link Image} obtained with this method will behave
     * as such.
     *
     * @return the android image.
     * @see android.media.Image#close()
     */
    @Nullable
    Image getImage();

    /**
     * Converts {@link ImageProxy} to {@link Bitmap}.
     *
     * <p>The supported {@link ImageProxy} format is {@link ImageFormat#YUV_420_888},
     * {@link ImageFormat#JPEG} or {@link PixelFormat#RGBA_8888}. If format is invalid, an
     * {@link IllegalArgumentException} will be thrown. If the conversion to bimap failed, an
     * {@link UnsupportedOperationException} will be thrown.
     *
     * @return {@link Bitmap} instance.
     */
    @NonNull
    default Bitmap toBitmap() {
        return createBitmapFromImageProxy(this);
    }
}
