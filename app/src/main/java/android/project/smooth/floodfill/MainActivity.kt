package android.project.smooth.floodfill

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.project.smooth.floodfill.databinding.ActivityMainBinding
import android.util.Log
import android.view.MotionEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private lateinit var bitmap: Bitmap
    private var isFilling = false


    private var currentColor = Color.WHITE


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        setupBitmap()
        setupTouchListener()

        binding.vRed.setOnClickListener {
            currentColor = Color.RED
            binding.vCurrentColor.setBackgroundColor(currentColor)
        }
        binding.vGreen.setOnClickListener {
            currentColor = Color.GREEN
            binding.vCurrentColor.setBackgroundColor(currentColor)
        }
        binding.vYellow.setOnClickListener {
            currentColor = Color.YELLOW
            binding.vCurrentColor.setBackgroundColor(currentColor)
        }
        binding.vWhite.setOnClickListener {
            currentColor = Color.WHITE
            binding.vCurrentColor.setBackgroundColor(currentColor)
        }
    }

    private fun setupBitmap() {
        bitmap = BitmapScaler.scaleTo800Width(
            BitmapFactory
                .decodeResource(resources, R.drawable.image)
                .copy(Bitmap.Config.ARGB_8888, true)
        )
        binding.imageView.setImageBitmap(bitmap)
    }

    private fun setupTouchListener() {
        var isMultiTouch = false
        var downTime = 0L
        var downX = 0f
        var downY = 0f

        binding.imageView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isMultiTouch = false
                    downTime = event.eventTime
                    downX = event.x
                    downY = event.y
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2) {
                        isMultiTouch = true
                    }
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount <= 2) {
                        isMultiTouch = false
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (!isMultiTouch) {
                        val duration = event.eventTime - downTime
                        if (duration < 200) {
                            handleTouch(downX, downY)
                        }
                    }
                    isMultiTouch = false
                }

                MotionEvent.ACTION_CANCEL -> {
                    isMultiTouch = false
                }
            }

            true
        }
    }

    private fun handleTouch(touchX: Float, touchY: Float) {
        if (isFilling) return

        val matrix = binding.imageView.imageMatrix
        val inverse = Matrix()

        if (!matrix.invert(inverse)) {
            return
        }

        val points = floatArrayOf(touchX, touchY)
        inverse.mapPoints(points)

        val bitmapX = points[0].toInt()
        val bitmapY = points[1].toInt()

        if (bitmapX < 0 || bitmapX >= bitmap.width ||
            bitmapY < 0 || bitmapY >= bitmap.height
        ) {
            return
        }

        performAnimatedFloodFill(bitmapX, bitmapY)
    }

    private fun performAnimatedFloodFill(x: Int, y: Int) {
        isFilling = true

        val start = System.currentTimeMillis()
        lifecycleScope.launch {
            val success = FloodFillNative.fillInPlaceAnimated(
                bitmap = bitmap,
                x = x,
                y = y,
                fillColor = currentColor,
                durationMs = 300
            ) {
                binding.imageView.invalidate()
            }

            if (success) {
                val end = System.currentTimeMillis()
                Log.d("thuongok", "performAnimatedFloodFill: ${end - start}")
                binding.imageView.invalidate()
            }

            isFilling = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::bitmap.isInitialized && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}