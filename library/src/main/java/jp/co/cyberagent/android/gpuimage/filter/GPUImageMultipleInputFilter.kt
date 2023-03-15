package jp.co.cyberagent.android.gpuimage.filter

import android.graphics.Bitmap
import android.opengl.GLES20
import android.util.Log
import jp.co.cyberagent.android.gpuimage.util.OpenGlUtils
import jp.co.cyberagent.android.gpuimage.util.OpenGlUtils.NO_TEXTURE
import jp.co.cyberagent.android.gpuimage.util.Rotation
import jp.co.cyberagent.android.gpuimage.util.TextureRotationUtil
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val NUM_OF_TEXTURES = 5

open class GPUImageMultipleInputFilter(
    vertexShader: String = VERTEX_ATTRS + VERTEX_SHADER,
    fragmentShader: String
) :
    GPUImageFilter(vertexShader, fragmentShader) {

    companion object {
        private const val VERTEX_ATTRS = """
                attribute vec4 position;
                attribute vec4 inputTextureCoordinate;
                attribute vec4 inputTextureCoordinate2;
                attribute vec4 inputTextureCoordinate3;
                attribute vec4 inputTextureCoordinate4;
                attribute vec4 inputTextureCoordinate5;
                attribute vec4 inputTextureCoordinate6;
                varying vec2 textureCoordinate;
                varying vec2 textureCoordinate2;
                varying vec2 textureCoordinate3;
                varying vec2 textureCoordinate4;
                varying vec2 textureCoordinate5;
                varying vec2 textureCoordinate6;
        """
        private const val VERTEX_SHADER = """
                void main()
                {
                    gl_Position = position;
                    textureCoordinate = inputTextureCoordinate.xy;
                    textureCoordinate2 = inputTextureCoordinate2.xy;
                    textureCoordinate3 = inputTextureCoordinate3.xy;
                    textureCoordinate4 = inputTextureCoordinate4.xy;
                    textureCoordinate5 = inputTextureCoordinate5.xy;
                    textureCoordinate6 = inputTextureCoordinate6.xy;
                }
                """
    }

    private var filterTextureCoordinateAttributeArray: IntArray = IntArray(NUM_OF_TEXTURES)
    private var filterInputTextureUniformArray: IntArray = IntArray(NUM_OF_TEXTURES)
    private var filterSourceTextureArray: IntArray = IntArray(NUM_OF_TEXTURES) { NO_TEXTURE }
    private var texture2CoordinatesBuffer: Array<ByteBuffer?> = Array(NUM_OF_TEXTURES) { null }
    private var bitmaps: Array<WeakReference<Bitmap>?> = Array(NUM_OF_TEXTURES) { null }

    init {
        setRotation(Rotation.NORMAL, flipHorizontal = false, flipVertical = false)
    }

    override fun onInit() {
        super.onInit()
        filterTextureCoordinateAttributeArray = arrayOf(
            GLES20.glGetAttribLocation(program, "inputTextureCoordinate2"),
            GLES20.glGetAttribLocation(program, "inputTextureCoordinate3"),
            GLES20.glGetAttribLocation(program, "inputTextureCoordinate4"),
            GLES20.glGetAttribLocation(program, "inputTextureCoordinate5"),
            GLES20.glGetAttribLocation(program, "inputTextureCoordinate6")
        ).toIntArray()

        // This does assume a name of "inputImageTexture2,3,4,5,6" for second input texture in the fragment shader
        filterInputTextureUniformArray = arrayOf(
            GLES20.glGetUniformLocation(program, "inputImageTexture2"),
            GLES20.glGetUniformLocation(program, "inputImageTexture3"),
            GLES20.glGetUniformLocation(program, "inputImageTexture4"),
            GLES20.glGetUniformLocation(program, "inputImageTexture5"),
            GLES20.glGetUniformLocation(program, "inputImageTexture6")
        ).toIntArray()

        filterTextureCoordinateAttributeArray.forEach {
            GLES20.glEnableVertexAttribArray(it)
        }
    }

    override fun onInitialized() {
        super.onInitialized()
        bitmaps.forEachIndexed { index, wr ->
            val b = wr?.get()?.takeIf { !it.isRecycled }
            if (b != null) {
                setBitmap(b, index)
            }
        }
    }

    fun setSourceTexture(index: Int, textureId: Int) {
        filterSourceTextureArray[index] = textureId
    }

    /**
     * NOTE, only support first tesxture, rest need to be loaded after filter is initlized
     */
    fun setBitmap(bitmap: Bitmap?, index: Int) {
        if (bitmap != null && bitmap.isRecycled) {
            return
        }
        bitmaps[index] = WeakReference(bitmap)
        if (bitmaps[index]?.get() == null) {
            Log.e("TEST", "setBitmap: no bitmap");
            return
        }
        runOnDraw(Runnable {
            if (filterSourceTextureArray[index] == NO_TEXTURE) {
                if (bitmap == null || bitmap.isRecycled) {
                    return@Runnable
                }
                GLES20.glActiveTexture(getOpenGlTextureId(index))
                filterSourceTextureArray[index] = OpenGlUtils.loadTexture(bitmap, NO_TEXTURE, false)
            }
        })
    }

    private fun getOpenGlTextureId(index: Int): Int {
        return when (index) {
            0 -> GLES20.GL_TEXTURE3
            1 -> GLES20.GL_TEXTURE4
            2 -> GLES20.GL_TEXTURE5
            3 -> GLES20.GL_TEXTURE6
            4 -> GLES20.GL_TEXTURE7
            5 -> GLES20.GL_TEXTURE8
            else -> NO_TEXTURE
        }
    }

    fun recycleBitmap() {
        bitmaps.forEach {
            it?.get()?.takeIf { !it.isRecycled }?.recycle()
            it?.clear()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        GLES20.glDeleteTextures(
            filterSourceTextureArray.size, filterSourceTextureArray, 0
        )
        filterSourceTextureArray = IntArray(NUM_OF_TEXTURES) { NO_TEXTURE }
    }

    override fun onDrawArraysPre() {
        filterTextureCoordinateAttributeArray.forEachIndexed { index, i ->
            GLES20.glEnableVertexAttribArray(i)
            GLES20.glActiveTexture(getOpenGlTextureId(index))
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, filterSourceTextureArray[index])
            GLES20.glUniform1i(filterInputTextureUniformArray[index], 3 + index)
            texture2CoordinatesBuffer[index]?.position(0)
            GLES20.glVertexAttribPointer(
                filterTextureCoordinateAttributeArray[index],
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                texture2CoordinatesBuffer[index]
            )

        }

    }

    fun setRotation(rotation: Rotation?, flipHorizontal: Boolean, flipVertical: Boolean) {
        for (i in filterTextureCoordinateAttributeArray.indices) {
            val buffer = TextureRotationUtil.getRotation(rotation, flipHorizontal, flipVertical)
            val bBuffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder())
            val fBuffer = bBuffer.asFloatBuffer()
            fBuffer.put(buffer)
            fBuffer.flip()
            texture2CoordinatesBuffer[i] = bBuffer
        }
    }

    fun getTextureId(index: Int): Int? {
        return filterInputTextureUniformArray.getOrNull(index)
    }
}