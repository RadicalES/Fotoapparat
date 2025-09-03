package io.fotoapparat.image;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

public final class ImageProcessingUtil {

    static {
        System.loadLibrary("image_processing_util_jni");
    }

    public static void copyByteBufferToBitmap(@NonNull Bitmap bitmap,
                                              @NonNull ByteBuffer byteBuffer, int bufferStride) {
        int bitmapStride = bitmap.getRowBytes();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        nativeCopyBetweenByteBufferAndBitmap(bitmap, byteBuffer, bufferStride, bitmapStride, width,
                height, true);
    }

    @NonNull
    public static Bitmap convertYUVToBitmap(@NonNull ImageProxy imageProxy) {
        if (imageProxy.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Input image format must be YUV_420_888");
        }

        int imageWidth = imageProxy.getWidth();
        int imageHeight = imageProxy.getHeight();
        int srcStrideY = imageProxy.getPlanes()[0].getRowStride();
        int srcStrideU = imageProxy.getPlanes()[1].getRowStride();
        int srcStrideV = imageProxy.getPlanes()[2].getRowStride();
        int srcPixelStrideY = imageProxy.getPlanes()[0].getPixelStride();
        int srcPixelStrideUV = imageProxy.getPlanes()[1].getPixelStride();

        Bitmap bitmap = Bitmap.createBitmap(imageProxy.getWidth(),
                imageProxy.getHeight(), Bitmap.Config.ARGB_8888);
        int bitmapStride = bitmap.getRowBytes();

        int result = nativeConvertAndroid420ToBitmap(
                imageProxy.getPlanes()[0].getBuffer(),
                srcStrideY,
                imageProxy.getPlanes()[1].getBuffer(),
                srcStrideU,
                imageProxy.getPlanes()[2].getBuffer(),
                srcStrideV,
                srcPixelStrideY,
                srcPixelStrideUV,
                bitmap,
                bitmapStride,
                imageWidth,
                imageHeight);
        if (result != 0) {
            throw new UnsupportedOperationException("YUV to RGB conversion failed");
        }
        return bitmap;
    }

    private static native int nativeCopyBetweenByteBufferAndBitmap(Bitmap bitmap,
                                                                   ByteBuffer byteBuffer,
                                                                   int sourceStride, int destinationStride, int width, int height,
                                                                   boolean isCopyBufferToBitmap);



    private static native int nativeConvertAndroid420ToBitmap(
            @NonNull ByteBuffer srcByteBufferY,
            int srcStrideY,
            @NonNull ByteBuffer srcByteBufferU,
            int srcStrideU,
            @NonNull ByteBuffer srcByteBufferV,
            int srcStrideV,
            int srcPixelStrideY,
            int srcPixelStrideUV,
            @NonNull Bitmap bitmap,
            int bitmapStride,
            int width,
            int height);

}
