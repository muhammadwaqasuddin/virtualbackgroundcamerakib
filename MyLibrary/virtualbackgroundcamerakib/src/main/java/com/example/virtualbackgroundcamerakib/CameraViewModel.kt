package com.example.virtualbackgroundcamerakib

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.runtime.mutableStateOf
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.math.exp


@HiltViewModel
class CameraViewModel @Inject constructor() : ViewModel() {
    var lensFacing = mutableStateOf(CameraSelector.LENS_FACING_FRONT)
    var selectedBackgroundIndex = mutableStateOf(0)

    private val backgroundCache = mutableMapOf<String, Bitmap>()
    private val cacheMutex = Mutex()

    fun getThumbnailBackground(thumbSizeDp: Int, thumbSizePx: Int, context: Context): List<Bitmap> {
        val blurThumb = createBitmap(thumbSizePx, thumbSizePx)
        val canvas = Canvas(blurThumb)
        canvas.drawColor("#40000000".toColorInt())

        val bmp = createBitmap(thumbSizePx, thumbSizePx)
        val c = Canvas(bmp)
        val paint = Paint()
        val cell = thumbSizePx / 4f
        for (y in 0..3) {
            for (x in 0..3) {
                paint.color = if ((x + y) % 2 == 0) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY
                c.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, paint)
            }
        }

        val gradientPresets = listOf(
            bmp,
            blurThumb,
            createCustomBackground(context, thumbSizePx, thumbSizePx,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(74, 144, 226),
                    android.graphics.Color.rgb(229, 115, 115)
                ), overlayResId = R.drawable.bg1),
            createCustomBackground(context, thumbSizePx, thumbSizePx,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(74, 144, 226),
                    android.graphics.Color.rgb(229, 115, 115)
                ), overlayResId = R.drawable.bg2),
            createCustomBackground(context, thumbSizePx, thumbSizePx,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(74, 144, 226),
                    android.graphics.Color.rgb(229, 115, 115)
                ), overlayResId = R.drawable.bg3),
            createCustomBackground(context, thumbSizePx, thumbSizePx,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(74, 144, 226),
                    android.graphics.Color.rgb(229, 115, 115)
                ), overlayResId = R.drawable.bg4),
            createCustomBackground(context, thumbSizePx, thumbSizePx,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(74, 144, 226),
                    android.graphics.Color.rgb(229, 115, 115)
                ), overlayResId = R.drawable.bg5),
            createCustomBackground(context, thumbSizePx, thumbSizePx,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(74, 144, 226),
                    android.graphics.Color.rgb(229, 115, 115)
                ), overlayResId = R.drawable.bg6),
            createCustomBackground(context, thumbSizePx, thumbSizePx,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(74, 144, 226),
                    android.graphics.Color.rgb(229, 115, 115)
                ), overlayResId = R.drawable.bg7),
            createCustomBackground(context, thumbSizePx, thumbSizePx,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(156, 39, 176),
                    android.graphics.Color.rgb(233, 30, 99)
                )),
            createCustomBackground(context, thumbSizePx, thumbSizePx,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(135, 206, 235),
                    android.graphics.Color.rgb(152, 251, 152),
                    android.graphics.Color.rgb(34, 139, 34)
                )),
            createCustomBackground(context, thumbSizePx, thumbSizePx,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(255, 182, 193),
                    android.graphics.Color.rgb(255, 160, 122),
                    android.graphics.Color.rgb(160, 82, 45)
                )),
            createCustomBackground(context, thumbSizePx, thumbSizePx,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(173, 216, 230),
                    android.graphics.Color.rgb(144, 238, 144),
                    android.graphics.Color.rgb(107, 142, 35)
                )),
            createCustomBackground(context, thumbSizePx, thumbSizePx,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(255, 228, 181),
                    android.graphics.Color.rgb(255, 165, 0),
                    android.graphics.Color.rgb(128, 128, 0)
                )),
            createCustomBackground(context, thumbSizePx, thumbSizePx,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(240, 248, 255),
                    android.graphics.Color.rgb(152, 251, 152),
                    android.graphics.Color.rgb(46, 125, 50)
                )),
            createCustomBackground(context, thumbSizePx, thumbSizePx,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(255, 240, 245),
                    android.graphics.Color.rgb(255, 182, 193),
                    android.graphics.Color.rgb(139, 69, 19)
                ))
        )
        return gradientPresets
    }

    // REMOVED: Deprecated RenderScript blur - use alternative approach
    private fun applyBackgroundBlur(bitmap: Bitmap): Bitmap {
        // Simple fast blur alternative
        val scale = 0.25f
        val width = (bitmap.width * scale).toInt()
        val height = (bitmap.height * scale).toInt()

        val small = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val blurred = Bitmap.createScaledBitmap(small, bitmap.width, bitmap.height, true)
        small.recycle()

        // Apply darkening
        val canvas = Canvas(blurred)
        canvas.drawColor(0x40000000)

        return blurred
    }

    fun createCustomBackground(
        context: Context,
        width: Int,
        height: Int,
        gradientColors: IntArray,
        overlayResId: Int? = null,
        overlayAlpha: Int = 180
    ): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        val gradientPaint = Paint()
        val shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            gradientColors, null, Shader.TileMode.CLAMP
        )
        gradientPaint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)

        if (overlayResId != null) {
            try {
                val overlayBitmap = BitmapFactory.decodeResource(context.resources, overlayResId)
                if (overlayBitmap != null) {
                    val scaled = Bitmap.createScaledBitmap(overlayBitmap, width, height, true)
                    val overlayPaint = Paint().apply { alpha = overlayAlpha }
                    canvas.drawBitmap(scaled, 0f, 0f, overlayPaint)

                    if (scaled != overlayBitmap) scaled.recycle()
                    overlayBitmap.recycle()
                }
            } catch (e: Exception) {
                // Handle silently
            }
        }
        return bitmap
    }

    @OptIn(ExperimentalGetImage::class)
    fun processSegmentationEnhanced(
        frameBitmap: Bitmap,
        maskArray: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        context: Context,
        gradientIndex: Int = 0,
        isFrontCamera: Boolean,
    ): Bitmap {
        if (gradientIndex == 0) {
            return frameBitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        val backgroundBitmap = if (gradientIndex == 1) {
            applyBackgroundBlur(frameBitmap)
        } else {
            createGradientBackgroundCached(
                frameBitmap.width,
                frameBitmap.height,
                gradientIndex,
                context
            )
        }

        val outputBitmap = createBitmap(frameBitmap.width, frameBitmap.height)
        val canvas = Canvas(outputBitmap)

        canvas.drawBitmap(backgroundBitmap, 0f, 0f, null)
        if (gradientIndex != 1) {
            backgroundBitmap.recycle()
        }

        val maskBitmap = createEnhancedMask(
            maskArray,
            maskWidth,
            maskHeight,
            frameBitmap.width,
            frameBitmap.height,
            isFrontCamera
        )

        val paint = Paint().apply {
            isAntiAlias = true
            isDither = true
            isFilterBitmap = true
        }

        val personLayer = createBitmap(frameBitmap.width, frameBitmap.height)
        val personCanvas = Canvas(personLayer)
        personCanvas.drawBitmap(frameBitmap, 0f, 0f, null)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        personCanvas.drawBitmap(maskBitmap, 0f, 0f, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        canvas.drawBitmap(personLayer, 0f, 0f, paint)

        maskBitmap.recycle()
        personLayer.recycle()

        return outputBitmap
    }

    @ExperimentalGetImage
    private fun createGradientBackgroundCached(
        width: Int,
        height: Int,
        gradientIndex: Int,
        context: Context
    ): Bitmap {
        val cacheKey = "${gradientIndex}_${width}x${height}"

        backgroundCache[cacheKey]?.let {
            if (!it.isRecycled) return it.copy(Bitmap.Config.ARGB_8888, false)
        }

        val bitmap = createGradientBackgroundInternal(width, height, gradientIndex, context)

        // Cache with size limit
        viewModelScope.launch(Dispatchers.IO) {
            cacheMutex.withLock {
                if (backgroundCache.size > 5) {
                    backgroundCache.values.firstOrNull()?.recycle()
                    backgroundCache.remove(backgroundCache.keys.first())
                }
                backgroundCache[cacheKey] = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            }
        }

        return bitmap
    }

    private fun createGradientBackgroundInternal(
        width: Int,
        height: Int,
        gradientIndex: Int,
        context: Context
    ): Bitmap {
        val blurThumb = createBitmap(width, height)
        val canvas1 = Canvas(blurThumb)
        canvas1.drawColor("#40000000".toColorInt())

        val backgroundList = listOf(
            blurThumb,
            createCustomBackground(context, width, height,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(74, 144, 226),
                    android.graphics.Color.rgb(229, 115, 115)
                ), overlayResId = R.drawable.bg1),
            createCustomBackground(context, width, height,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(74, 144, 226),
                    android.graphics.Color.rgb(229, 115, 115)
                ), overlayResId = R.drawable.bg2),
            createCustomBackground(context, width, height,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(74, 144, 226),
                    android.graphics.Color.rgb(229, 115, 115)
                ), overlayResId = R.drawable.bg3),
            createCustomBackground(context, width, height,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(74, 144, 226),
                    android.graphics.Color.rgb(229, 115, 115)
                ), overlayResId = R.drawable.bg4),
            createCustomBackground(context, width, height,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(74, 144, 226),
                    android.graphics.Color.rgb(229, 115, 115)
                ), overlayResId = R.drawable.bg5),
            createCustomBackground(context, width, height,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(74, 144, 226),
                    android.graphics.Color.rgb(229, 115, 115)
                ), overlayResId = R.drawable.bg6),
            createCustomBackground(context, width, height,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(74, 144, 226),
                    android.graphics.Color.rgb(229, 115, 115)
                ), overlayResId = R.drawable.bg7),
            createCustomBackground(context, width, height,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(156, 39, 176),
                    android.graphics.Color.rgb(233, 30, 99)
                )),
            createCustomBackground(context, width, height,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(135, 206, 235),
                    android.graphics.Color.rgb(152, 251, 152),
                    android.graphics.Color.rgb(34, 139, 34)
                )),
            createCustomBackground(context, width, height,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(255, 182, 193),
                    android.graphics.Color.rgb(255, 160, 122),
                    android.graphics.Color.rgb(160, 82, 45)
                )),
            createCustomBackground(context, width, height,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(173, 216, 230),
                    android.graphics.Color.rgb(144, 238, 144),
                    android.graphics.Color.rgb(107, 142, 35)
                )),
            createCustomBackground(context, width, height,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(255, 228, 181),
                    android.graphics.Color.rgb(255, 165, 0),
                    android.graphics.Color.rgb(128, 128, 0)
                )),
            createCustomBackground(context, width, height,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(240, 248, 255),
                    android.graphics.Color.rgb(152, 251, 152),
                    android.graphics.Color.rgb(46, 125, 50)
                )),
            createCustomBackground(context, width, height,
                gradientColors = intArrayOf(
                    android.graphics.Color.rgb(255, 240, 245),
                    android.graphics.Color.rgb(255, 182, 193),
                    android.graphics.Color.rgb(139, 69, 19)
                ))
        )

        return backgroundList[gradientIndex - 1]
    }

    private fun createEnhancedMask(
        maskArray: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        isFrontCamera: Boolean,
    ): Bitmap {
        val blurredMask = applyGaussianBlur(maskArray, maskWidth, maskHeight, 2.0f)

        val maskBitmap = createBitmap(maskWidth, maskHeight)
        val pixels = IntArray(maskWidth * maskHeight)

        for (i in blurredMask.indices) {
            val alpha = (blurredMask[i] * 255).toInt().coerceIn(0, 255)
            val smoothAlpha = applySigmoidCurve(alpha / 255f) * 255
            pixels[i] = android.graphics.Color.argb(smoothAlpha.toInt(), 255, 255, 255)
        }

        maskBitmap.setPixels(pixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)
        val matrix = Matrix()
        if (isFrontCamera) {
            matrix.postScale(-1f, 1f)
        }
        val rotated = Bitmap.createBitmap(maskBitmap, 0, 0, maskBitmap.width, maskBitmap.height, matrix, true)
        return if (maskWidth != targetWidth || maskHeight != targetHeight) {
            val scaled = rotated.scale(targetWidth, targetHeight)
            rotated.recycle()
            scaled
        } else {
            rotated
        }
    }

    private fun applyGaussianBlur(
        input: FloatArray,
        width: Int,
        height: Int,
        sigma: Float,
    ): FloatArray {
        val output = input.copyOf()
        val kernelSize = (sigma * 3).toInt() * 2 + 1
        val kernel = createGaussianKernel(kernelSize, sigma)
        val radius = kernelSize / 2

        val temp = FloatArray(input.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0f
                var weightSum = 0f

                for (i in -radius..radius) {
                    val nx = (x + i).coerceIn(0, width - 1)
                    val weight = kernel[i + radius]
                    sum += input[y * width + nx] * weight
                    weightSum += weight
                }
                temp[y * width + x] = sum / weightSum
            }
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0f
                var weightSum = 0f

                for (i in -radius..radius) {
                    val ny = (y + i).coerceIn(0, height - 1)
                    val weight = kernel[i + radius]
                    sum += temp[ny * width + x] * weight
                    weightSum += weight
                }
                output[y * width + x] = sum / weightSum
            }
        }
        return output
    }

    private fun createGaussianKernel(size: Int, sigma: Float): FloatArray {
        val kernel = FloatArray(size)
        val center = size / 2
        var sum = 0f

        for (i in 0 until size) {
            val x = i - center
            kernel[i] = exp(-(x * x) / (2 * sigma * sigma))
            sum += kernel[i]
        }

        for (i in kernel.indices) {
            kernel[i] /= sum
        }

        return kernel
    }

    private fun applySigmoidCurve(x: Float): Float {
        val steepness = 8f
        val midpoint = 0.5f
        return 1f / (1f + exp(-steepness * (x - midpoint)))
    }

    override fun onCleared() {
        super.onCleared()
        backgroundCache.values.forEach { it.recycle() }
        backgroundCache.clear()
    }
}