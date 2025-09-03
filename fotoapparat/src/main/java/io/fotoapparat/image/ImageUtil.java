package io.fotoapparat.image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.media.Image;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

public final class ImageUtil {

    /**
     * Creates {@link Bitmap} from {@link ImageProxy}.
     *
     * <p> Currently only {@link ImageFormat#YUV_420_888}, {@link ImageFormat#JPEG},
     * {@link ImageFormat#JPEG_R} and {@link PixelFormat#RGBA_8888} are supported. If the format
     * is invalid, an {@link IllegalArgumentException} will be thrown. If the conversion to bimap
     * failed, an {@link UnsupportedOperationException} will be thrown.
     *
     * @param imageProxy The input {@link ImageProxy} instance.
     * @return {@link Bitmap} instance.
     */
    @NonNull
    public static Bitmap createBitmapFromImageProxy(@NonNull ImageProxy imageProxy) {
        switch (imageProxy.getFormat()) {
            case ImageFormat.YUV_420_888:
                return ImageProcessingUtil.convertYUVToBitmap(imageProxy);
            case ImageFormat.JPEG:
            case ImageFormat.JPEG_R:
                return createBitmapFromJpegImage(imageProxy);
            case PixelFormat.RGBA_8888:
                return createBitmapFromRgbaImage(imageProxy);
            default:
                throw new IllegalArgumentException(
                        "Incorrect image format of the input image proxy: "
                                + imageProxy.getFormat() + ", only ImageFormat.YUV_420_888 and "
                                + "PixelFormat.RGBA_8888 are supported");
        }
    }

    public static boolean isJpegFormats(int imageFormat) {
        return imageFormat == ImageFormat.JPEG || imageFormat == ImageFormat.JPEG_R;
    }

        @NonNull
    public static byte[] jpegImageToJpegByteArray(@NonNull ImageProxy image) {
        if (!isJpegFormats(image.getFormat())) {
            throw new IllegalArgumentException(
                    "Incorrect image format of the input image proxy: " + image.getFormat());
        }

        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] data = new byte[buffer.capacity()];
        buffer.rewind();
        buffer.get(data);

        return data;
    }


    @NonNull
    private static Bitmap createBitmapFromJpegImage(@NonNull ImageProxy imageProxy) {
        byte[] bytes = jpegImageToJpegByteArray(imageProxy);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
        if (bitmap == null) {
            throw new UnsupportedOperationException("Decode jpeg byte array failed");
        }
        return bitmap;
    }

    @NonNull
    private static Bitmap createBitmapFromRgbaImage(@NonNull ImageProxy imageProxy) {
        Bitmap bitmap =
                Bitmap.createBitmap(imageProxy.getWidth(),
                        imageProxy.getHeight(),
                        Bitmap.Config.ARGB_8888);
        // Rewind the buffer just to be safe.
        imageProxy.getPlanes()[0].getBuffer().rewind();
        ImageProcessingUtil.copyByteBufferToBitmap(bitmap, imageProxy.getPlanes()[0].getBuffer(),
                imageProxy.getPlanes()[0].getRowStride());
        return bitmap;
    }


}
