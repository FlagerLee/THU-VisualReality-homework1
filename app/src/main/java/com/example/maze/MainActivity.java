package com.example.maze;

import android.os.Bundle;
import android.util.Log;

import android.opengl.GLES30;
import android.opengl.Matrix;

import com.google.vr.sdk.base.AndroidCompat;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;

public class MainActivity extends GvrActivity implements GvrView.StereoRenderer {
    private static final String[] OBJECT_VERTEX_SHADER_CODE =
            new String[] {
                    "uniform vec4 lightPosition;",
                    "uniform vec3 Kd;", // 漫反射系数
                    "uniform vec3 Ld;", // 漫反射光强
                    "uniform mat4 u_MVP;",
                    "uniform mat4 mvMatrix;",
                    "uniform mat4 pMatrix;",
                    "uniform mat3 normalMatrix;",
                    "attribute vec4 a_Position;",
                    "attribute vec3 a_normal;",
                    "attribute vec2 a_UV;",
                    "varying vec2 v_UV;",
                    "varying vec3 lightIntensity;",
                    "",
                    "void main() {",
                    "  v_UV = a_UV;",
                    "  vec3 tnorm = normalize(normalMatrix * a_normal);",
                    "  vec4 eyeCoords = mvMatrix * a_Position;",
                    "  vec4 newLightPosition = u_MVP * lightPosition;",
                    "  vec3 s = normalize(vec3(newLightPosition - eyeCoords));",
                    "  lightIntensity = Ld * Kd * max(dot(s, tnorm), 0.0);",
                    "  gl_Position = u_MVP * a_Position;",
                    "}",
            };
    private static final String[] OBJECT_FRAGMENT_SHADER_CODE =
            new String[] {
                    "// This determines how much precision the GPU uses when calculating floats",
                    "precision mediump float;",
                    "varying vec2 v_UV;",
                    "varying vec3 lightIntensity;",
                    "uniform sampler2D u_Texture;",
                    "",
                    "void main() {",
                    "  // The y coordinate of this sample's textures is reversed compared to",
                    "  // what OpenGL expects, so we invert the y coordinate.",
                    "  gl_FragColor = texture2D(u_Texture, vec2(v_UV.x, 1.0 - v_UV.y));",
                    "  // gl_FragColor = vec4(lightIntensity, 1.0);",
                    "}",
            };
    private int eyeX = 1, eyeY = 10, eyeZ = 0;
    private float centerX = (float)2.0, centerY = (float)10.0, centerZ = (float)0.0;
    private int forward = 0;

    private static final float Z_NEAR = 0.01f;
    private static final float Z_FAR = 10000.0f;
    private static final float DEFAULT_FLOOR_HEIGHT = 0.0f;
    private final int numBox = 80;


    private int objectProgram;
    private int objectPositionParam;
    private int objectUvParam;
    private int objectNormalParam;
    private int objectModelViewProjectionParam;
    private int objectNormalMatrixParam;
    private int objectModelViewParam;
    private int objectProjectionParam;
    private int objectKdParam;
    private int objectLdParam;
    private int objectLightPosParam;

    private TexturedMesh room;
    private Texture roomTex;

    private float[] lightPosition = new float[4];
    private float[][] modelMatrix = new float[numBox][16];
    private float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[][] mvpMatrix = new float[numBox][16];
    private final float[] modelViewMatrix = new float[16];

    private float[] DebugEyeMatrix = new float[16];

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initializeGvrView();
    }

    public void initializeGvrView() {
        setContentView(R.layout.activity_main);

        GvrView gvrView = (GvrView) findViewById(R.id.gvr_view);
        gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);

        gvrView.setRenderer(this);
        gvrView.setTransitionViewEnabled(true);

        gvrView.enableCardboardTriggerEmulation();

        if (gvrView.setAsyncReprojectionEnabled(true)) {
            // Async reprojection decouples the app framerate from the display framerate,
            // allowing immersive interaction even at the throttled clockrates set by
            // sustained performance mode.
            AndroidCompat.setSustainedPerformanceMode(this, true);
        }

        setGvrView(gvrView);
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        // Set viewport
        GLES30.glViewport(0, 0, width, height);

        // Calculate projection matrix parameters based on screen aspect ratio and FoV
        float ratio = (float) width / height;
        float left = -Z_NEAR * 1.732f, right = Z_NEAR * 1.732f;
        float top = right / ratio, bottom = left / ratio;

        // Calculate the projection matrix
        // This projection matrix is applied to object coordinates in the onDrawFrame() method
        Matrix.frustumM(projectionMatrix, 0, left, right, bottom, top, Z_NEAR, Z_FAR);
    }

    @Override
    public void onSurfaceCreated(EGLConfig config) {
        // Set the background frame color
        GLES30.glClearColor(0.75f, .75f, .75f, .75f);

        // Compile shaders
        objectProgram = Util.compileProgram(OBJECT_VERTEX_SHADER_CODE, OBJECT_FRAGMENT_SHADER_CODE);

        // Get handles to shader parameters
        objectPositionParam = GLES30.glGetAttribLocation(objectProgram, "a_Position");
        objectUvParam = GLES30.glGetAttribLocation(objectProgram, "a_UV");
        objectModelViewProjectionParam = GLES30.glGetUniformLocation(objectProgram, "u_MVP");
        objectNormalParam = GLES30.glGetAttribLocation(objectProgram, "a_normal");
        objectNormalMatrixParam = GLES30.glGetUniformLocation(objectProgram, "normalMatrix");
        objectModelViewParam = GLES30.glGetUniformLocation(objectProgram, "mvMatrix");
        objectProjectionParam = GLES30.glGetUniformLocation(objectProgram, "pMatrix");
        objectKdParam = GLES30.glGetUniformLocation(objectProgram, "Kd");
        objectLdParam = GLES30.glGetUniformLocation(objectProgram, "Ld");
        objectLightPosParam = GLES30.glGetUniformLocation(objectProgram, "lightPosition");

        // Set model transformation matrix
        int k = 0;
        for(int i = 0; i < row; i ++)
            for(int j = 0; j < col; j ++) {
                if(maze[i][j] == 1) {
                    Matrix.setIdentityM(modelMatrix[k], 0);
                    Matrix.translateM(modelMatrix[k], 0, i, j, DEFAULT_FLOOR_HEIGHT);
                    k ++;
                }
            }

        // Load objects and textures
        try {
            room = new TexturedMesh(MainActivity.this, "Wooden_stuff.obj", objectPositionParam, objectUvParam, objectNormalParam);
            roomTex = new Texture(MainActivity.this, "Wooden_box_01_BaseColor.png");
        } catch (IOException e) {
            Log.e("Renderer", "Unable to initialize objects", e);
        }
    }

    @Override
    public void onDrawEye(Eye eye) {
        // Redraw background color
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        // Enable depth test
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);

        // Set the camera position (view matrix)
        float[] camera = new float[16];
        Matrix.multiplyMM(camera, 0, eye.getEyeView(), 0, viewMatrix, 0);

        DebugEyeMatrix = eye.getEyeView();

        // Calculate the MVP transformation matrix
        for(int i = 0; i < numBox; i ++) {
            projectionMatrix = eye.getPerspective(Z_NEAR, Z_FAR);
            Matrix.multiplyMM(modelViewMatrix, 0, camera, 0, modelMatrix[i], 0);
            Matrix.multiplyMM(mvpMatrix[i], 0, projectionMatrix, 0, modelViewMatrix, 0);
        }
        float[] normalMatrix = new float[] {
                modelViewMatrix[0], modelViewMatrix[1], modelViewMatrix[2],
                modelViewMatrix[4], modelViewMatrix[5], modelViewMatrix[6],
                modelViewMatrix[8], modelViewMatrix[9], modelViewMatrix[10]
        };
        //Matrix.invertM(normalMatrix, 0, normalMatrix, 0);
        //Matrix.transposeM(normalMatrix, 0, nMatrix, 0);

        float[] Kd = new float[] {.9f, .5f, .3f};
        float[] Ld = new float[] {1.f, 1.f, 1.f};
        float[] originLightPosition = new float[] {10.0f, 10.0f, 10.0f, 1.0f};
        Matrix.multiplyMV(lightPosition, 0, viewMatrix, 0, originLightPosition, 0);

        // bind parameters for shaders
        GLES30.glUseProgram(objectProgram);
        GLES30.glUniformMatrix4fv(objectModelViewParam, 1, false, modelViewMatrix, 0);
        GLES30.glUniformMatrix4fv(objectProjectionParam, 1, false, projectionMatrix, 0);
        GLES30.glUniformMatrix3fv(objectNormalMatrixParam, 1, false, normalMatrix, 0);
        GLES30.glUniform3fv(objectKdParam, 1, Kd, 0);
        GLES30.glUniform3fv(objectLdParam, 1, Ld, 0);
        GLES30.glUniform4fv(objectLightPosParam, 1, lightPosition, 0);
        for(int i = 0; i < numBox; i ++) {
            GLES30.glUniformMatrix4fv(objectModelViewProjectionParam, 1, false, mvpMatrix[i], 0);
            roomTex.bind();
            room.draw();
        }
    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {
        Matrix.setLookAtM(viewMatrix, 0, (float)eyeX, (float)eyeY, (float)eyeZ, centerX, centerY, centerZ, 0.0f, 0.0f, -1.0f);
    }

    @Override
    public void onFinishFrame(Viewport viewport) {

    }

    @Override
    public void onRendererShutdown() {

    }

    @Override
    public void onCardboardTrigger() {
        float Zx = -DebugEyeMatrix[8], Zy = -DebugEyeMatrix[9], Zz = -DebugEyeMatrix[10];
        if(Zx > 0.90) {
            if(maze[eyeX][eyeY - 1] == 0) eyeY -= 1;
        }
        else if(Zx < -0.90) {
            if(maze[eyeX][eyeY + 1] == 0) eyeY += 1;
        }
        else if(Zz > 0.90) {
            if(maze[eyeX - 1][eyeY] == 0) eyeX -= 1;
        }
        else if(Zz < -0.90) {
            if(maze[eyeX + 1][eyeY] == 0) eyeX += 1;
        }
        centerX = (float)eyeX + (float)1.0;
        centerY = (float)eyeY;
        Log.i("EyeMatrix", getFloatString(DebugEyeMatrix) + eyeX + " " + eyeY + " " + eyeZ + "\n");
    }

    public String getFloatString(float[] matrix) {
        StringBuilder str = new StringBuilder("\n");
        for(int i = 0; i < 4; i ++) {
            str.append("[ ");
            for(int j = 0; j < 4; j ++) {
                if(j != 3) str.append(matrix[i * 4 + j]).append(", ");
                else str.append(matrix[i * 4 + j]).append("]\n");
            }
        }
        return str.toString();
    }

    private int row = 12, col = 12;
    // up: Z; down: -Z; left: X; right: -X
    private int[][] maze = new int[][] {
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1},
            {1, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 1},
            {1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1},
            {1, 1, 0, 1, 0, 1, 0, 0, 1, 0, 1, 1},
            {1, 1, 0, 0, 0, 0, 1, 0, 1, 0, 0, 1},
            {1, 0, 0, 1, 1, 0, 0, 0, 0, 1, 0, 1},
            {1, 0, 1, 1, 0, 0, 1, 1, 0, 0, 0, 1},
            {1, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 1},
            {1, 1, 1, 1, 0, 1, 0, 1, 1, 1, 0, 1},
            {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}
    };
}