package jp.co.cyberagent.android.gpuimage.filter

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import jp.co.cyberagent.android.gpuimage.util.OpenGlUtils
import java.nio.FloatBuffer
import kotlin.math.*

class GPUImageMultiLevelMattingFilter(img: Bitmap, mask: Bitmap) : GPUImageFilterGroup() {

    init {
        val transformMatrix = FloatArray(16)
        var didSet = false
        pyramidScaleList(img.width, img.height).forEach { lvlSize ->
            val numOfIterations = if (lvlSize.width <= 32 && lvlSize.height <= 32) 10 else 2
            val scaledMask = Bitmap.createScaledBitmap(mask, lvlSize.width, lvlSize.height, true)
            //scale main image
            addFilter(GPUImageTransformFilter().apply {
                val scaleX = img.width / lvlSize.width.toFloat()
                val scaleY = img.height / lvlSize.height.toFloat()
                Matrix.setIdentityM(transformMatrix, 0)
                Matrix.scaleM(transformMatrix, 0, scaleX, scaleY, 0f)
            })
            //add num of iterations of this level
            for (i in 0..numOfIterations) {
                addFilter(GPUImageSingleLevelMattingFilter().apply {
                    setBitmap(scaledMask, 0)
                    if (!didSet) {
                        didSet = true
                        val fg = Bitmap.createBitmap(mask.width, mask.height, mask.config).apply {
                            eraseColor(Color.BLACK)
                        }
                        val bg = Bitmap.createBitmap(mask.width, mask.height, mask.config).apply {
                            eraseColor(Color.BLACK)
                        }
                        setBitmap(fg, 2)
                        setBitmap(bg, 3)
                    }
                })
            }
        }
        Log.d("TEST", "starting multi level matting with ${getFilters().size} filters");
    }

    override fun onDraw(textureId: Int, cubeBuffer: FloatBuffer, textureBuffer: FloatBuffer) {
        runPendingOnDrawTasks()
        if (!isInitialized || frameBuffers == null || frameBufferTextures == null) {
            return
        }
        val mergedFilters = mergedFilters ?: return
        val size = mergedFilters.size
        var previousTexture = textureId
        for (i in 0 until size) {
            val filter = mergedFilters[i]
            val isNotLast = i < size - 1
            if (isNotLast) {
                frameBuffers?.let { fb ->
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[i])
                    GLES20.glClearColor(0f, 0f, 0f, 0f)
                }
            }
            when {
                filter is GPUImageSingleLevelMattingFilter -> {
                    //todo pass result of previuos iteration as input to next step
                    //todo figure out how to get 2 result of fg and bg output
                    val prevFilter = mergedFilters.getOrNull(i - 1)
                    if (prevFilter is GPUImageSingleLevelMattingFilter) {
                        val maskTexture = prevFilter.getTextureId(1) ?: OpenGlUtils.NO_TEXTURE
                        val fgTexture = prevFilter.getTextureId(2) ?: OpenGlUtils.NO_TEXTURE
                        val bgTexture = prevFilter.getTextureId(3) ?: OpenGlUtils.NO_TEXTURE
                        filter.setSourceTexture(1, maskTexture)
                        filter.setSourceTexture(2, fgTexture)
                        filter.setSourceTexture(3, bgTexture)
                    }

                    filter.onDraw(previousTexture, glCubeBuffer, glTextureBuffer)
                }
                i == 0 -> {
                    filter.onDraw(previousTexture, cubeBuffer, textureBuffer)
                }
                i == size - 1 -> {
                    filter.onDraw(
                        previousTexture,
                        glCubeBuffer,
                        if (size % 2 == 0) glTextureFlipBuffer else glTextureBuffer
                    )
                }
                else -> {
                    filter.onDraw(previousTexture, glCubeBuffer, glTextureBuffer)
                }
            }

            if (isNotLast) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                previousTexture = frameBufferTextures?.getOrNull(i) ?: previousTexture
            }
        }
    }

    private fun pyramidScaleList(w: Int, h: Int): List<Size> {
        val w0 = w.toDouble()
        val h0 = h.toDouble()
        val levels = floor(log2(max(w0, h0))).toInt()
        val pyramid = mutableListOf<Size>()
        for (i in 0..levels) {
            val scaledW = w.toDouble().pow(i / levels).roundToInt()
            val scaledH = h.toDouble().pow(i / levels).roundToInt()
            pyramid.add(Size(scaledW, scaledH))
        }
        return pyramid.toList()
    }

}