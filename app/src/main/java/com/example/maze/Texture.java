package com.example.maze;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import java.io.IOException;


/**
 * THIS FILE IS FROM https://github.com/googlevr/gvr-android-sdk/blob/master/samples/sdk-hellovr/src/main/java/com/google/vr/sdk/samples/hellovr/Texture.java
 */

/** A texture, meant for use with TexturedMesh. */
class Texture {
    private final int[] textureId = new int[1];

    /**
     * Initializes the texture.
     *  @param context Context for loading the texture file.
     * @param texturePath Path to the image to use for the texture.
     */
    public Texture(Context context, String texturePath) throws IOException {
        GLES20.glGenTextures(1, textureId, 0);
        bind();
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        Bitmap textureBitmap = BitmapFactory.decodeStream(context.getAssets().open(texturePath));
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0);
        textureBitmap.recycle();
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
    }

    /** Binds the texture to GL_TEXTURE0. */
    public void bind() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0]);
    }
}
