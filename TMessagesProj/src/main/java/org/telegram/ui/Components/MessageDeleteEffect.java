package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLES20;
import android.opengl.GLES31;
import android.opengl.GLUtils;
import android.os.Build;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.ChatMessageCell;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class MessageDeleteEffect {
    private FrameLayout textureViewContainer;
    private TextureView textureView;
    private RendererThread rendererThread;

    public boolean attach(ChatMessageCell view) {
        if (view == null || !supports()) return false;

        if (view.getCurrentMessagesGroup() != null) return false;

        View rootView = view.getRootView();
        if (rootView == null) return false;
        if (!(rootView instanceof ViewGroup)) return false;

        int modHeight = 3 * view.getHeight() / 10;

        /*int transX = 0;
        int transY = 0;
        if (view.getCurrentMessagesGroup() != null) {
            transX = view.getCurrentMessagesGroup().transitionParams.left;
            transY = view.getCurrentMessagesGroup().transitionParams.top;
        }*/

        float lpart = ((float) view.getBackgroundDrawableLeft()) / view.getWidth();
        float rpart = ((float) view.getBackgroundDrawableRight()) / view.getWidth();

        if (view.getPhotoImage() != null) {
            view.getPhotoImage().stopAnimation();
            view.getPhotoImage().setAutoRepeat(0);
            view.getPhotoImage().invalidate();
        }
        final Bitmap bmp = getBitmapFromView(view, modHeight);

        textureViewContainer = new FrameLayout(rootView.getContext());
        ((ViewGroup) rootView).addView(textureViewContainer);

        textureView = new TextureView(textureViewContainer.getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                setMeasuredDimension(view.getWidth(), view.getHeight() + modHeight);
            }
        };
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                if (rendererThread == null) {
                    rendererThread = new RendererThread(surface, width, height, bmp, lpart, rpart,
                            () -> view.setAlpha(0.0f),
                            () -> {
                                rendererThread = null;
                                textureViewContainer.removeView(textureView);
                                ((ViewGroup) rootView).removeView(textureViewContainer);
                            });
                    rendererThread.start();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                if (rendererThread != null) {
                    // TODO: rendererThread.updateSize(width, height, recreate bmp?, parts?);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                if (rendererThread != null) {
                    rendererThread.running = false;
                }
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}
        });
        textureView.setOpaque(false);
        textureView.setTranslationY(view.getTop() - view.parentViewTopOffset - modHeight);
        textureViewContainer.addView(textureView);

        return true;
    }

    private static boolean supports() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    private static Bitmap getBitmapFromView(View view, int modHeight) {
        //Define a bitmap with the same size as the view
        Bitmap returnedBitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight() + modHeight, Bitmap.Config.ARGB_8888);
        //Bind a canvas to it
        Canvas canvas = new Canvas(returnedBitmap);
        canvas.save();
        canvas.translate(0f, modHeight);
        //Get the view's background
        Drawable bgDrawable = view.getBackground();
        if (bgDrawable != null) {
            //has background drawable, then draw it on the canvas
            bgDrawable.draw(canvas);
        }/* else {
            //does not have background drawable, then draw white background on the canvas
            canvas.drawColor(Color.WHITE);
        }*/
        // draw the view on the canvas
        view.draw(canvas);
        canvas.restore();
        //return the bitmap
        return returnedBitmap;
    }

    private static class RendererThread extends Thread {
        public boolean running = true;
        private final SurfaceTexture surface;
        private final int width, height;
        private final Bitmap bitmap;
        private final float lpart, rpart;
        private Runnable onFirstDraw;
        private final Runnable onEnd;
        public int MAX_FPS;

        RendererThread(SurfaceTexture surface, int width, int height, Bitmap bitmap, float lpart, float rpart,
                       Runnable onFirstDraw, Runnable onEnd) {
            this.surface = surface;
            this.width = width;
            this.height = height;
            this.bitmap = bitmap;
            this.lpart = lpart;
            this.rpart = rpart;
            this.onFirstDraw = onFirstDraw;
            this.onEnd = onEnd;
            MAX_FPS = 60;//(int) AndroidUtilities.screenRefreshRate;
        }

        @Override
        public void run() {
            EGL10 egl = (EGL10) EGLContext.getEGL();

            EGLDisplay eglDisplay = egl.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == egl.EGL_NO_DISPLAY) {
                running = false;
                return;
            }
            int[] version = new int[2];
            if (!egl.eglInitialize(eglDisplay, version)) {
                running = false;
                return;
            }

            int[] configAttributes = {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                    EGL14.EGL_NONE
            };
            EGLConfig[] eglConfigs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!egl.eglChooseConfig(eglDisplay, configAttributes, eglConfigs, 1, numConfigs)) {
                running = false;
                return;
            }
            EGLConfig eglConfig = eglConfigs[0];

            int[] contextAttributes = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                    EGL14.EGL_NONE
            };
            EGLContext eglContext = egl.eglCreateContext(eglDisplay, eglConfig, egl.EGL_NO_CONTEXT, contextAttributes);
            if (eglContext == null) {
                running = false;
                return;
            }

            EGLSurface eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, surface, null);
            if (eglSurface == null) {
                running = false;
                return;
            }

            if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                running = false;
                return;
            }

            //
            int vertexShader = GLES31.glCreateShader(GLES31.GL_VERTEX_SHADER);
            int fragmentShader = GLES31.glCreateShader(GLES31.GL_FRAGMENT_SHADER);
            if (vertexShader == 0 || fragmentShader == 0) {
                running = false;
                return;
            }

            GLES31.glShaderSource(vertexShader, RLottieDrawable.readRes(null, R.raw.msg_delete_vertex) + "\n// " + Math.random());
            GLES31.glCompileShader(vertexShader);
            int[] status = new int[1];
            GLES31.glGetShaderiv(vertexShader, GLES31.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                FileLog.e("MessageDeleteEffect, compile vertex shader error: " + GLES31.glGetShaderInfoLog(vertexShader));
                GLES31.glDeleteShader(vertexShader);
                running = false;
                return;
            }
            GLES31.glShaderSource(fragmentShader, RLottieDrawable.readRes(null, R.raw.msg_delete_fragment) + "\n// " + Math.random());
            GLES31.glCompileShader(fragmentShader);
            GLES31.glGetShaderiv(fragmentShader, GLES31.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                FileLog.e("MessageDeleteEffect, compile fragment shader error: " + GLES31.glGetShaderInfoLog(fragmentShader));
                GLES31.glDeleteShader(fragmentShader);
                running = false;
                return;
            }
            int drawProgram = GLES31.glCreateProgram();
            if (drawProgram == 0) {
                running = false;
                return;
            }
            GLES31.glAttachShader(drawProgram, vertexShader);
            GLES31.glAttachShader(drawProgram, fragmentShader);

            GLES31.glLinkProgram(drawProgram);
            GLES31.glGetProgramiv(drawProgram, GLES31.GL_LINK_STATUS, status, 0);
            if (status[0] == 0) {
                FileLog.e("MessageDeleteEffect, link draw program error: " + GLES31.glGetProgramInfoLog(drawProgram));
                running = false;
                return;
            }

            int positionHandle = GLES31.glGetAttribLocation(drawProgram, "vPosition");
            int sizeHandle = GLES31.glGetUniformLocation(drawProgram, "size");
            int partsHandle = GLES31.glGetUniformLocation(drawProgram, "parts");
            int progressHandle = GLES31.glGetUniformLocation(drawProgram, "progress");

            GLES31.glViewport(0, 0, width, height);
            GLES31.glEnable(GLES31.GL_BLEND);
            GLES31.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES31.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

            float[] gTriangleVertices = {
                    -1.0f, 1.0f,
                    -1.0f, -1.0f,
                    1.0f, -1.0f,
                    -1.0f, 1.0f,
                    1.0f, -1.0f,
                    1.0f, 1.0f,
            };

            ByteBuffer bb = ByteBuffer.allocateDirect(gTriangleVertices.length * 4).order(ByteOrder.nativeOrder());
            FloatBuffer vertexBuffer = bb.asFloatBuffer().put(gTriangleVertices);
            vertexBuffer.position(0);

            int[] vbo = {0,};
            GLES31.glGenBuffers(1, vbo, 0);
            GLES31.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0]);
            GLES31.glBufferData(GLES20.GL_ARRAY_BUFFER, gTriangleVertices.length * 4, vertexBuffer, GLES31.GL_STATIC_DRAW);

            int[] textures = {0, 0,};
            GLES31.glGenTextures(2, textures, 0);

            //GLES31.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES31.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES31.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
            GLES31.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
            GLES31.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES31.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            bitmap.recycle();

            //GLES31.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES31.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1]);
            GLES31.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
            GLES31.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
            GLES31.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES31.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            Random rnd = new Random();
            ByteBuffer dirTexture = ByteBuffer.allocateDirect(width * height * 3).order(ByteOrder.nativeOrder());
            for (int i = 0; i < width * height; i++) {
                dirTexture.put((byte) rnd.nextInt(256));
                dirTexture.put((byte) rnd.nextInt(256));
                dirTexture.put((byte) rnd.nextInt(256));
            }
            dirTexture.position(0);
            GLES31.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, width, height, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, dirTexture);

            GLES31.glUseProgram(drawProgram);
            GLES31.glUniform2f(sizeHandle, width, height);
            GLES31.glUniform2f(partsHandle, lpart, rpart);
            GLES31.glUniform1f(progressHandle, 0);
            GLES31.glUniform1i(GLES31.glGetUniformLocation(drawProgram, "texture1"), 0);
            GLES31.glUniform1i(GLES31.glGetUniformLocation(drawProgram, "texture2"), 1);

            float progressStep = 0.02f / (MAX_FPS / 60f);
            progressStep = progressStep * (rpart - lpart)/* / 1.0f*/;

            float progress = lpart;
            while (running) {
                if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                    running = false;
                    return;
                }

                progress += progressStep;
                if (progress > rpart) {
                    progress = lpart;
                    GLES31.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                    break;
                }

                GLES31.glClear(GLES20.GL_COLOR_BUFFER_BIT);

                GLES31.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES31.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
                GLES31.glActiveTexture(GLES20.GL_TEXTURE1);
                GLES31.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1]);

                GLES31.glUseProgram(drawProgram);

                GLES31.glUniform1f(progressHandle, progress);
                GLES31.glVertexAttribPointer(positionHandle, 2, GLES31.GL_FLOAT, false, 0, 0);
                GLES31.glEnableVertexAttribArray(positionHandle);

                GLES31.glDrawArrays(GLES31.GL_TRIANGLES, 0, 6);

                egl.eglSwapBuffers(eglDisplay, eglSurface);

                if (onFirstDraw != null) {
                    AndroidUtilities.runOnUIThread(onFirstDraw);
                    onFirstDraw = null;
                }

                try {
                    sleep((long) (1f / MAX_FPS * 1000f));
                } catch (Exception ignored) {}
            }

            GLES31.glDeleteBuffers(1, vbo, 0);
            GLES31.glDeleteTextures(2, textures, 0);

            GLES31.glDeleteShader(vertexShader);
            GLES31.glDeleteShader(fragmentShader);
            GLES31.glDeleteProgram(drawProgram);

            egl.eglDestroySurface(eglDisplay, eglSurface);
            egl.eglDestroyContext(eglDisplay, eglContext);

            surface.release();

            AndroidUtilities.runOnUIThread(onEnd, 30);
        }
    }
}
