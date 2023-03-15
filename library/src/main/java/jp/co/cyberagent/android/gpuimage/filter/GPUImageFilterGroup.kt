/*
 * Copyright (C) 2018 CyberAgent, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jp.co.cyberagent.android.gpuimage.filter

import android.annotation.SuppressLint
import android.opengl.GLES20
import jp.co.cyberagent.android.gpuimage.GPUImageRenderer
import jp.co.cyberagent.android.gpuimage.util.Rotation
import jp.co.cyberagent.android.gpuimage.util.TextureRotationUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Resembles a filter that consists of multiple filters applied after each
 * other.
 */
open class GPUImageFilterGroup @JvmOverloads constructor(private val filters: MutableList<GPUImageFilter> = mutableListOf()) :
    GPUImageFilter() {

    private lateinit var frameBufferIndexMap: Map<Int, Int>
    internal var mergedFilters: MutableList<GPUImageFilter>? = null
    internal var frameBuffers: IntArray? = null
    internal var frameBufferTextures: IntArray? = null
    internal val glCubeBuffer: FloatBuffer
    internal val glTextureBuffer: FloatBuffer
    internal val glTextureFlipBuffer: FloatBuffer
    /**
     * Instantiates a new GPUImageFilterGroup with the given filters.
     *
     * @param filters the filters which represent this filter
     */
    /**
     * Instantiates a new GPUImageFilterGroup with no filters.
     */
    init {
        updateMergedFilters()
        glCubeBuffer = ByteBuffer.allocateDirect(GPUImageRenderer.CUBE.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        glCubeBuffer.put(GPUImageRenderer.CUBE).position(0)
        glTextureBuffer =
            ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        glTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0)
        val flipTexture = TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)
        glTextureFlipBuffer = ByteBuffer.allocateDirect(flipTexture.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        glTextureFlipBuffer.put(flipTexture).position(0)
    }

    fun addFilter(aFilter: GPUImageFilter?) {
        if (aFilter == null) {
            return
        }
        filters.add(aFilter)
        updateMergedFilters()
    }

    /*
     * (non-Javadoc)
     * @see jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter#onInit()
     */
    override fun onInit() {
        super.onInit()
        for (filter in filters) {
            filter.ifNeedInit()
        }
    }

    /*
     * (non-Javadoc)
     * @see jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter#onDestroy()
     */
    override fun onDestroy() {
        destroyFrameBuffers()
        for (filter in filters) {
            filter.destroy()
        }
        super.onDestroy()
    }

    private fun destroyFrameBuffers() {
        frameBufferTextures?.let {
            GLES20.glDeleteTextures(it.size, frameBufferTextures, 0)
            frameBufferTextures = null
        }
        frameBuffers?.let {
            GLES20.glDeleteFramebuffers(it.size, frameBuffers, 0)
            frameBuffers = null
        }
    }

    /*
     * (non-Javadoc)
     * @see
     * jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter#onOutputSizeChanged(int,
     * int)
     */
    override fun onOutputSizeChanged(width: Int, height: Int) {
        super.onOutputSizeChanged(width, height)
        if (frameBuffers != null) {
            destroyFrameBuffers()
        }
        var size = filters.size
        for (i in 0 until size) {
            filters[i].onOutputSizeChanged(width, height)
        }
        val mergedFilters = mergedFilters ?: return
        if (mergedFilters.size > 0) {
            size = mergedFilters.size
            frameBuffers = IntArray(size - 1)
            frameBufferTextures = IntArray(size - 1)
            for (i in 0 until size - 1) {
                GLES20.glGenFramebuffers(1, frameBuffers, i)
                GLES20.glGenTextures(1, frameBufferTextures, i)
                frameBufferTextures?.let { fbt ->
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fbt[i])
                }
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
                )
                GLES20.glTexParameterf(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat()
                )
                GLES20.glTexParameterf(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat()
                )
                GLES20.glTexParameterf(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat()
                )
                GLES20.glTexParameterf(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat()
                )
                frameBuffers?.let { fb ->
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[i])
                }
                frameBufferTextures?.let { fbt ->
                    GLES20.glFramebufferTexture2D(
                        GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                        GLES20.GL_TEXTURE_2D, fbt[i], 0
                    )
                }
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter#onDraw(int,
     * java.nio.FloatBuffer, java.nio.FloatBuffer)
     */
    @SuppressLint("WrongCall")
    override fun onDraw(
        textureId: Int, cubeBuffer: FloatBuffer,
        textureBuffer: FloatBuffer
    ) {
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
                filter is GPUImageNormalBlendSavedStateFilter -> {
                    //get index of saved state filter
                    var idx =
                        mergedFilters.indexOfFirst { it is GPUImageSaveCurrentStateFilter && it.tag == filter.tag }
                            .takeIf { it > 0 } ?: continue

                    // if this and the source have the same module then copy from
                    // previous frame buffer (content is the same) to be in correct orientation
                    if (i % 2 == idx % 2) {
                        idx -= 1
                    }

                    val copyFrameBufferTexture = frameBufferTextures?.getOrNull(idx) ?: continue
                    // set source texture for normal blend to blend
                    // GPUImageNormalBlendSavedStateFilter texture on top of other filters
                    // (e.g original on top of outline)
                    filter.setSecondarySourceTexture(copyFrameBufferTexture)
                    filter.onDraw(
                        previousTexture,
                        glCubeBuffer, glTextureBuffer
                    )
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

    /**
     * Gets the filters.
     *
     * @return the filters
     */
    fun getFilters(): List<GPUImageFilter> {
        return filters
    }

    fun clearFilters() {
        filters.clear()
    }

    fun getMergedFilters(): List<GPUImageFilter>? {
        return mergedFilters
    }

    fun updateMergedFilters() {
        val mergedFilters = mergedFilters ?: ArrayList()
        mergedFilters.clear()
        var filters: List<GPUImageFilter>?
        for (filter in this.filters) {
            if (filter is GPUImageFilterGroup) {
                filter.updateMergedFilters()
                filters = filter.getMergedFilters()
                if (filters == null || filters.isEmpty()) continue
                mergedFilters.addAll(filters)
                continue
            }
            mergedFilters.add(filter)
        }
        this.mergedFilters = mergedFilters
    }
}