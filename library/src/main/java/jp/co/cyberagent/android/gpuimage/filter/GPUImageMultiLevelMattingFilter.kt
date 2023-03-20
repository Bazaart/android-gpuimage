package jp.co.cyberagent.android.gpuimage.filter

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

class GPUImageMultiLevelMattingFilter(img: Bitmap, mask: Bitmap) : GPUImageFilterGroup() {

    init {
        val transformMatrix = FloatArray(16)
        var didSetFg = false
        pyramidScaleList(img.width, img.height).filter { it.width > 32 && it.height > 32 }
            .forEachIndexed { index, lvlSize ->
                Log.d("TEST", "lvl: $index size: $lvlSize: ");
                val numOfIterations = if (lvlSize.width <= 32 && lvlSize.height <= 32) 10 else 2
                val scaledMask =
                    Bitmap.createScaledBitmap(mask, lvlSize.width, lvlSize.height, true)
//            val scaledImg = Bitmap.createScaledBitmap(img, lvlSize.width, lvlSize.height, true)
                //scale main image
                addFilter(GPUImageTransformFilter().apply {
                    val scaleX = img.width / lvlSize.width.toFloat()
                    val scaleY = img.height / lvlSize.height.toFloat()
                    Matrix.setIdentityM(transformMatrix, 0)
                    Matrix.scaleM(transformMatrix, 0, scaleX, scaleY, 0f)
                })
                //add num of iterations of this level

                //input: 32x32, mask, new new
                //render into fbo fg
                //render into fbo bg
                var setMask = false
                for (i in 0..numOfIterations * 2) {
                    addFilter(GPUImageSingleIterationMattingFilter(i % 2 == 0, lvlSize).apply {
                        if (!setMask) {
                            setMask = true
                            setBitmap(scaledMask, 0)
                        }

                        if (!didSetFg) {
                            didSetFg = true
                            val fg =
                                Bitmap.createBitmap(mask.width, mask.height, mask.config).apply {
//                            eraseColor(Color.TRANSPARENT)
                                }
                            val bg =
                                Bitmap.createBitmap(mask.width, mask.height, mask.config).apply {
//                            eraseColor(Color.TRANSPARENT)
                                }
                            setBitmap(fg, 1)
                            setBitmap(bg, 2)
                        }
                    })
                }
            }
//        getFilters().first { it as? GPUImageSingleLevelMattingFilter != null}.let {
//            val f = it as GPUImageSingleLevelMattingFilter
//            onOutputSizeChanged(f.size.width, f.size.height)
//        }
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
            if (filter is GPUImageSingleIterationMattingFilter) {
                //todo pass result of previuos iteration as input to next step
                //todo figure out how to get 2 result of fg and bg output
                val prevFilter = mergedFilters.getOrNull(i - 2)
                if (i % 2 == 0 && prevFilter is GPUImageSingleIterationMattingFilter) {
//                    val maskTexture = prevFilter.getTextureId(0) ?: OpenGlUtils.NO_TEXTURE
//                    val fgTexture = prevFilter.getTextureId(1) ?: OpenGlUtils.NO_TEXTURE
//                    val bgTexture = prevFilter.getTextureId(2) ?: OpenGlUtils.NO_TEXTURE
                    val prevMask = prevFilter.bitmaps[0]?.get() ?: continue
                    if (filter.size == prevFilter.size) {
                        filter.setBitmap(prevMask, 0)
                    }
                    frameBuffers?.let { fb ->
                        //set input as the output of previous step
                        val fg = readTexture(filter.outputWidth, filter.outputHeight, fb[i - 2])
                            ?: return@let
                        val bg = readTexture(filter.outputWidth, filter.outputHeight, fb[i - 1])
                            ?: return@let
                        filter.setBitmap(
                            Bitmap.createScaledBitmap(
                                fg,
                                filter.size.width,
                                filter.size.height,
                                true
                            ), 1
                        )
                        filter.setBitmap(
                            Bitmap.createScaledBitmap(
                                bg,
                                filter.size.width,
                                filter.size.height,
                                true
                            ), 2
                        )
                        //bind again fb
                        if (isNotLast) {
                            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[i])
                            GLES20.glClearColor(0f, 0f, 0f, 0f)
                        }
                    }
                }
            }
            when (i) {
                0 -> {
                    filter.onDraw(previousTexture, cubeBuffer, textureBuffer)
                }
                size - 1 -> {
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
            val scaledW = w.toDouble().pow(i / levels.toDouble()).roundToInt()
            val scaledH = h.toDouble().pow(i / levels.toDouble()).roundToInt()
            pyramid.add(Size(scaledW, scaledH))
        }
        return pyramid.toList()
    }

    fun readTexture(width: Int, height: Int, fbId: Int): Bitmap? {
        val buffer: ByteBuffer = ByteBuffer.allocateDirect(width * height * 4)
        buffer.order(ByteOrder.nativeOrder())
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbId)
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            this.copyPixelsFromBuffer(buffer)
        }
    }

}