package com.example.maze;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLES30;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjData;
import de.javagl.obj.ObjReader;
import de.javagl.obj.ObjUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;


/**
 * THIS FILE IS FROM https://github.com/googlevr/gvr-android-sdk/blob/master/samples/sdk-hellovr/src/main/java/com/google/vr/sdk/samples/hellovr/TexturedMesh.java
 */

/** Renders an object loaded from an OBJ file. */
class TexturedMesh {
    private static final String TAG = "TexturedMesh";

    private final FloatBuffer vertices;
    private final FloatBuffer uv;
    private final FloatBuffer normals;
    private final ShortBuffer indices;
    private final int positionAttrib;
    private final int uvAttrib;
    private final int normalAttrib;

    /**
     * Initializes the mesh from an .obj file.
     *  @param context Context for loading the .obj file.
     * @param objFilePath Path to the .obj file.
     * @param positionAttrib The position attribute in the shader.
     * @param uvAttrib The UV attribute in the shader.
     */
    public TexturedMesh(Context context, String objFilePath, int positionAttrib, int uvAttrib, int normalAttrib)
            throws IOException {
        InputStream objInputStream = context.getAssets().open(objFilePath);
        Obj obj = ObjUtils.convertToRenderable(ObjReader.read(objInputStream));
        objInputStream.close();

        IntBuffer intIndices = ObjData.getFaceVertexIndices(obj, 3);
        vertices = ObjData.getVertices(obj);
        uv = ObjData.getTexCoords(obj, 2);
        normals = ObjData.getNormals(obj);

        // Convert int indices to shorts (GLES doesn't support int indices)
        indices =
                ByteBuffer.allocateDirect(2 * intIndices.limit())
                        .order(ByteOrder.nativeOrder())
                        .asShortBuffer();
        while (intIndices.hasRemaining()) {
            indices.put((short) intIndices.get());
        }
        indices.rewind();

        this.positionAttrib = positionAttrib;
        this.uvAttrib = uvAttrib;
        this.normalAttrib = normalAttrib;
    }

    /**
     * Draws the mesh. Before this is called, u_MVP should be set with glUniformMatrix4fv(), and a
     * texture should be bound to GL_TEXTURE0.
     */
    public void draw() {
        GLES30.glEnableVertexAttribArray(positionAttrib);
        GLES30.glVertexAttribPointer(positionAttrib, 3, GLES30.GL_FLOAT, false, 0, vertices);
        GLES30.glEnableVertexAttribArray(uvAttrib);
        GLES30.glVertexAttribPointer(uvAttrib, 2, GLES30.GL_FLOAT, false, 0, uv);
        GLES30.glEnableVertexAttribArray(normalAttrib);
        GLES30.glVertexAttribPointer(normalAttrib, 3, GLES30.GL_FLOAT, false, 0, normals);
        try {
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, indices.limit(), GLES30.GL_UNSIGNED_SHORT, indices);
        }
        catch (Exception e) {
            e.toString();
        }
        int i = 0;
    }
}
