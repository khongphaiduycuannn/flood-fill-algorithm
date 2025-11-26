package android.project.smooth.floodfill

import android.graphics.Bitmap

object BitmapScaler {

    fun scaleTo800Width(bitmap: Bitmap): Bitmap {
        return scaleToWidth(bitmap, 800)
    }

    fun scaleToWidth(bitmap: Bitmap, targetWidth: Int): Bitmap {
        require(targetWidth > 0) { "Target width must be positive" }
        require(!bitmap.isRecycled) { "Bitmap is recycled" }
        require(bitmap.width > 0 && bitmap.height > 0) { "Invalid bitmap dimensions" }

        if (bitmap.width == targetWidth) {
            return bitmap
        }

        val scale = targetWidth.toFloat() / bitmap.width
        val newHeight = (bitmap.height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, targetWidth, newHeight, true)
    }
}