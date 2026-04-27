/*
 * Copyright (C) 2018 CyberAgent, Inc.
 * Copyright (C) 2010 jsemler
 *
 * Original publication without License
 * http://www.anddev.org/android-2d-3d-graphics-opengl-tutorials-f2/possible-to-do-opengl-off-screen-rendering-in-android-t13232.html#p41662
 */

package jp.co.cyberagent.android.gpuimage;

import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.util.Log;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import static javax.microedition.khronos.egl.EGL10.EGL_ALPHA_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_BLUE_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_DEFAULT_DISPLAY;
import static javax.microedition.khronos.egl.EGL10.EGL_DEPTH_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_GREEN_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_HEIGHT;
import static javax.microedition.khronos.egl.EGL10.EGL_NONE;
import static javax.microedition.khronos.egl.EGL10.EGL_NO_CONTEXT;
import static javax.microedition.khronos.egl.EGL10.EGL_RED_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_STENCIL_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_WIDTH;

public class PixelBuffer {
    private final static String TAG = "PixelBuffer";
    private final static boolean LIST_CONFIGS = false;
    private final static int EGL_OPENGL_ES2_BIT = 4;

    private GLSurfaceView.Renderer renderer; // borrow this interface
    private int width, height;
    private Bitmap bitmap;

    private EGL10 egl10;
    private EGLDisplay eglDisplay;
    private EGLConfig[] eglConfigs;
    private EGLConfig eglConfig;
    private EGLContext eglContext;
    private EGLSurface eglSurface;
    private GL10 gl10;

    private String mThreadOwner;

    public PixelBuffer(final int width, final int height) {
        this.width = width;
        this.height = height;

        int[] version = new int[2];
        int[] attribList = new int[]{
                EGL_WIDTH, this.width,
                EGL_HEIGHT, this.height,
                EGL_NONE
        };

        // No error checking performed, minimum required code to elucidate logic
        egl10 = (EGL10) EGLContext.getEGL();
        eglDisplay = egl10.eglGetDisplay(EGL_DEFAULT_DISPLAY);
        egl10.eglInitialize(eglDisplay, version);
        eglConfig = chooseConfig(); // Choosing a config is a little more
        // complicated

        // eglContext = egl10.eglCreateContext(eglDisplay, eglConfig,
        // EGL_NO_CONTEXT, null);
        int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        int[] attrib_list = {
                EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL10.EGL_NONE
        };
        eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, EGL_NO_CONTEXT, attrib_list);

        eglSurface = egl10.eglCreatePbufferSurface(eglDisplay, eglConfig, attribList);
        egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);

        gl10 = (GL10) eglContext.getGL();

        // Record thread owner of OpenGL context
        mThreadOwner = Thread.currentThread().getName();
    }

    public void setRenderer(final GLSurfaceView.Renderer renderer) {
        this.renderer = renderer;

        // Does this thread own the OpenGL context?
        if (!Thread.currentThread().getName().equals(mThreadOwner)) {
            Log.e(TAG, "setRenderer: This thread does not own the OpenGL context.");
            return;
        }

        // Call the renderer initialization routines
        this.renderer.onSurfaceCreated(gl10, eglConfig);
        this.renderer.onSurfaceChanged(gl10, width, height);
    }

    public Bitmap getBitmap() {
        // Do we have a renderer?
        if (renderer == null) {
            Log.e(TAG, "getBitmap: Renderer was not set.");
            return null;
        }

        // Does this thread own the OpenGL context?
        if (!Thread.currentThread().getName().equals(mThreadOwner)) {
            Log.e(TAG, "getBitmap: This thread does not own the OpenGL context.");
            return null;
        }

        // Call the renderer draw routine (it seems that some filters do not
        // work if this is only called once)
        renderer.onDrawFrame(gl10);
        renderer.onDrawFrame(gl10);
        convertToBitmap();
        return bitmap;
    }

    public void destroy() {
        renderer.onDrawFrame(gl10);
        renderer.onDrawFrame(gl10);
        egl10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);

        egl10.eglDestroySurface(eglDisplay, eglSurface);
        egl10.eglDestroyContext(eglDisplay, eglContext);
        egl10.eglTerminate(eglDisplay);
    }

    private EGLConfig chooseConfig() {
        int[] preferredAttribList = new int[]{
                EGL_DEPTH_SIZE, 0,
                EGL_STENCIL_SIZE, 0,
                EGL_RED_SIZE, 8,
                EGL_GREEN_SIZE, 8,
                EGL_BLUE_SIZE, 8,
                EGL_ALPHA_SIZE, 8,
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL_NONE
        };

        EGLConfig preferredConfig = chooseConfig(preferredAttribList);
        if (preferredConfig != null) {
            return preferredConfig;
        }

        int[] fallbackAttribList = new int[]{
                EGL_RED_SIZE, 4,
                EGL_GREEN_SIZE, 4,
                EGL_BLUE_SIZE, 4,
                EGL_ALPHA_SIZE, 4,
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL_NONE
        };

        Log.w(TAG, "No exact EGL config match for PixelBuffer. Falling back to the best available ES2 config.");
        EGLConfig fallbackConfig = chooseBestConfig(fallbackAttribList);
        if (LIST_CONFIGS) {
            listConfig();
        }
        if (fallbackConfig != null) {
            return fallbackConfig;
        }

        throw new IllegalArgumentException("No matching EGL config for PixelBuffer");
    }

    private EGLConfig chooseConfig(final int[] attribList) {
        EGLConfig[] configs = getConfigs(attribList);
        return configs.length > 0 ? configs[0] : null;
    }

    private EGLConfig chooseBestConfig(final int[] attribList) {
        EGLConfig[] configs = getConfigs(attribList);
        if (configs.length == 0) {
            return null;
        }

        EGLConfig bestConfig = configs[0];
        for (int i = 1; i < configs.length; i++) {
            if (isBetterConfig(configs[i], bestConfig)) {
                bestConfig = configs[i];
            }
        }
        return bestConfig;
    }

    private EGLConfig[] getConfigs(final int[] attribList) {
        // No error checking performed, minimum required code to elucidate logic
        // Expand on this logic to be more selective in choosing a configuration
        int[] numConfig = new int[1];
        egl10.eglChooseConfig(eglDisplay, attribList, null, 0, numConfig);
        int configSize = numConfig[0];
        if (configSize <= 0) {
            eglConfigs = new EGLConfig[0];
            return eglConfigs;
        }

        eglConfigs = new EGLConfig[configSize];
        egl10.eglChooseConfig(eglDisplay, attribList, eglConfigs, configSize, numConfig);

        if (LIST_CONFIGS) {
            listConfig();
        }

        return eglConfigs;
    }

    private boolean isBetterConfig(final EGLConfig candidate, final EGLConfig currentBest) {
        int candidateAlpha = Math.min(getConfigAttrib(candidate, EGL_ALPHA_SIZE), 8);
        int bestAlpha = Math.min(getConfigAttrib(currentBest, EGL_ALPHA_SIZE), 8);
        if (candidateAlpha != bestAlpha) {
            return candidateAlpha > bestAlpha;
        }

        int candidateColor = Math.min(getConfigAttrib(candidate, EGL_RED_SIZE), 8)
                + Math.min(getConfigAttrib(candidate, EGL_GREEN_SIZE), 8)
                + Math.min(getConfigAttrib(candidate, EGL_BLUE_SIZE), 8);
        int bestColor = Math.min(getConfigAttrib(currentBest, EGL_RED_SIZE), 8)
                + Math.min(getConfigAttrib(currentBest, EGL_GREEN_SIZE), 8)
                + Math.min(getConfigAttrib(currentBest, EGL_BLUE_SIZE), 8);
        if (candidateColor != bestColor) {
            return candidateColor > bestColor;
        }

        int candidateDepth = getConfigAttrib(candidate, EGL_DEPTH_SIZE);
        int bestDepth = getConfigAttrib(currentBest, EGL_DEPTH_SIZE);
        if (candidateDepth != bestDepth) {
            return candidateDepth < bestDepth;
        }

        int candidateStencil = getConfigAttrib(candidate, EGL_STENCIL_SIZE);
        int bestStencil = getConfigAttrib(currentBest, EGL_STENCIL_SIZE);
        return candidateStencil < bestStencil;
    }

    private void listConfig() {
        Log.i(TAG, "Config List {");

        for (EGLConfig config : eglConfigs) {
            int d, s, r, g, b, a;

            // Expand on this logic to dump other attributes
            d = getConfigAttrib(config, EGL_DEPTH_SIZE);
            s = getConfigAttrib(config, EGL_STENCIL_SIZE);
            r = getConfigAttrib(config, EGL_RED_SIZE);
            g = getConfigAttrib(config, EGL_GREEN_SIZE);
            b = getConfigAttrib(config, EGL_BLUE_SIZE);
            a = getConfigAttrib(config, EGL_ALPHA_SIZE);
            Log.i(TAG, "    <d,s,r,g,b,a> = <" + d + "," + s + "," +
                    r + "," + g + "," + b + "," + a + ">");
        }

        Log.i(TAG, "}");
    }

    private int getConfigAttrib(final EGLConfig config, final int attribute) {
        int[] value = new int[1];
        return egl10.eglGetConfigAttrib(eglDisplay, config,
                attribute, value) ? value[0] : 0;
    }

    private void convertToBitmap() {
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        GPUImageNativeLibrary.adjustBitmap(bitmap);
    }
}
