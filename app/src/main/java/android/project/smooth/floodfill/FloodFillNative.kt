package android.project.smooth.floodfill

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.ceil

object FloodFillNative {

    const val DEFAULT_TOLERANCE = 112
    const val DEFAULT_ANIMATION_DURATION_MS = 200L
    const val DEFAULT_ANIMATION_INTERVAL_MS = 16L


    init {
        System.loadLibrary("floodfill")
    }

    private val fillLock = Mutex()


    fun fillInPlace(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        fillColor: Int,
        tolerance: Int = DEFAULT_TOLERANCE
    ): Boolean {
        if (bitmap.isRecycled) return false
        if (!bitmap.isMutable) return false
        if (bitmap.config != Bitmap.Config.ARGB_8888) return false
        if (x < 0 || x >= bitmap.width || y < 0 || y >= bitmap.height) return false
        if (tolerance !in 0..255) return false

        return nativeFloodFill(bitmap, x, y, fillColor, tolerance)
    }

    suspend fun fillInPlaceAsync(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        fillColor: Int,
        tolerance: Int = 0
    ): Boolean = withContext(Dispatchers.Default) {
        fillInPlace(bitmap, x, y, fillColor, tolerance)
    }

    suspend fun fillInPlaceAnimated(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        fillColor: Int,
        ignoreColors: List<Int> = listOf(Color.BLACK),

        tolerance: Int = DEFAULT_TOLERANCE,

        durationMs: Long = DEFAULT_ANIMATION_DURATION_MS,
        intervalMs: Long = DEFAULT_ANIMATION_INTERVAL_MS,

        onProgress: suspend (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.Default) {
        fillLock.withLock {
            if (bitmap.isRecycled) return@withContext false
            if (!bitmap.isMutable) return@withContext false
            if (bitmap.config != Bitmap.Config.ARGB_8888) return@withContext false
            if (x < 0 || x >= bitmap.width || y < 0 || y >= bitmap.height) return@withContext false
            if (tolerance !in 0..255) return@withContext false

            ignoreColors.forEach {
                if (!nativeIsColorInvalid(
                        color1 = bitmap[x, y],
                        color2 = it,
                        tolerance = tolerance
                    )
                ) return@withContext false
            }

            val start = System.currentTimeMillis()
            val sequenceId = nativePrepareFillSequence(bitmap, x, y, fillColor, tolerance)
            val end = System.currentTimeMillis()
            Log.d("thuongok", "fillInPlaceAnimated: ${end - start}")

            if (sequenceId < 0) {
                return@withContext when (sequenceId.toInt()) {
                    -2 -> true
                    else -> false
                }
            }

            try {
                val firstResult =
                    nativeFillNextNLayers(bitmap, sequenceId, 0) ?: return@withContext false
                val totalLayers = firstResult[3]

                if (totalLayers <= 0) {
                    return@withContext true
                }

                val totalIntervals = (durationMs.toFloat() / intervalMs).toInt().coerceAtLeast(1)
                val layersPerInterval =
                    ceil(totalLayers.toFloat() / totalIntervals).toInt().coerceAtLeast(1)

                var isComplete = false

                while (!isComplete && coroutineContext.isActive) {
                    if (bitmap.isRecycled) {
                        return@withContext false
                    }

                    val result =
                        nativeFillNextNLayers(bitmap, sequenceId, layersPerInterval) ?: break

                    val progress = result[0] / 10000f
                    isComplete = result[1] == 1

                    onProgress(progress)

                    if (!isComplete) {
                        delay(intervalMs)
                    }
                }

                if (isComplete) {
                    onProgress(1f)
                }

                return@withContext isComplete
            } finally {
                nativeReleaseSequence(sequenceId)
            }
        }
    }


    private external fun nativeFloodFill(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        fillColor: Int,
        tolerance: Int
    ): Boolean

    private external fun nativePrepareFillSequence(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        fillColor: Int,
        tolerance: Int
    ): Long

    private external fun nativeFillNextNLayers(
        bitmap: Bitmap,
        sequenceId: Long,
        layerCount: Int
    ): IntArray?

    private external fun nativeReleaseSequence(sequenceId: Long)

    private external fun nativeIsColorInvalid(
        color1: Int,
        color2: Int,
        tolerance: Int
    ): Boolean
}