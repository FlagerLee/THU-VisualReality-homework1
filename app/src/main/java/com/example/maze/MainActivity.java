package com.example.maze;

import android.accessibilityservice.GestureDescription;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import android.opengl.GLES30;
import android.opengl.Matrix;
import android.opengl.GLSurfaceView;
import android.view.GestureDetector;
import android.view.MotionEvent;

import java.io.IOException;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity {
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
    private float eyeX = (float)1.0, eyeY = (float)10.0, eyeZ = (float)0.0;
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
    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[][] mvpMatrix = new float[numBox][16];
    private final float[] modelViewMatrix = new float[16];

    private GLSurfaceView gLView;
    private GestureDetector gDetector;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Create a GLSurfaceView instance and set it
        // as the ContentView for this Activity.
        gLView = new GLSurfaceView(this);
        // Create an OpenGL ES 2.0-compatible context.
        gLView.setEGLContextClientVersion(2);
        // The renderer is responsible for making OpenGL calls to render a frame.
        gLView.setRenderer(new GLSurfaceView.Renderer() {
            /*
            Called when the surface is created or recreated.
            Load model and texture here.
             */
            @Override
            public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
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

            /*
            Called after the surface is created and whenever the OpenGL ES surface size changes.
            Typically you will set your viewport here.
            If your camera is fixed then you could also set your projection matrix here.
             */
            @Override
            public void onSurfaceChanged(GL10 gl10, int width, int height) {
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

            /*
            Called to draw the current frame.
            Calculate MVP transformation and draw the objects.
             */
            @Override
            public void onDrawFrame(GL10 gl10) {
                // Redraw background color
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

                // Enable depth test
                GLES30.glEnable(GLES30.GL_DEPTH_TEST);

                // Set the camera position (view matrix)
                Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, centerX, centerY, centerZ, 0.0f, 0.0f, -1.0f);

                // Calculate the MVP transformation matrix
                for(int i = 0; i < numBox; i ++) {
                    Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix[i], 0);
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
        });

        setContentView(gLView);

        gDetector = new GestureDetector(this, new GestureDetector.OnGestureListener() {
            @Override
            public boolean onDown(MotionEvent motionEvent) {
                return false;
            }

            @Override
            public void onShowPress(MotionEvent motionEvent) {

            }

            @Override
            public boolean onSingleTapUp(MotionEvent motionEvent) {
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
                return false;
            }

            @Override
            public void onLongPress(MotionEvent motionEvent) {

            }

            @Override
            public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
                float rightDis = motionEvent1.getX() - motionEvent.getX();
                float upDis = motionEvent1.getY() - motionEvent.getY();
                final double flingDis = 50.0;
                if(Math.abs(rightDis) > Math.abs(upDis)) {
                    if(rightDis > flingDis) {
                        // right
                        forward = (forward + 1) % 4;
                    } else if(rightDis < -flingDis){
                        // left
                        forward = (forward + 3) % 4;
                    }
                    if(forward == 0) {
                        centerX = eyeX + 1;
                        centerY = eyeY;
                    } else if(forward == 1) {
                        centerX = eyeX;
                        centerY = eyeY + 1;
                    } else if(forward == 2) {
                        centerX = eyeX - 1;
                        centerY = eyeY;
                    } else if(forward == 3) {
                        centerX = eyeX;
                        centerY = eyeY - 1;
                    }
                }
                else {
                    int step = 0;
                    if(upDis > flingDis) {
                        // go forward
                        step = -1;
                    } else if(upDis < -flingDis) {
                        // go backward
                        step = 1;
                    }
                    if(forward == 0) {
                        if(maze[(int)eyeX + step][(int)eyeY] == 0) {
                            eyeX = eyeX + step;
                            centerX = eyeX + 1;
                            centerY = eyeY;
                        }
                    } else if(forward == 1) {
                        if(maze[(int)eyeX][(int)eyeY + step] == 0) {
                            eyeY = eyeY + step;
                            centerX = eyeX;
                            centerY = eyeY + 1;
                        }
                    } else if(forward == 2) {
                        if(maze[(int)eyeX - step][(int)eyeY] == 0) {
                            eyeX = eyeX - step;
                            centerX = eyeX - 1;
                            centerY = eyeY;
                        }
                    } else if(forward == 3) {
                        if(maze[(int)eyeX][(int)eyeY - step] == 0) {
                            eyeY = eyeY - step;
                            centerX = eyeX;
                            centerY = eyeY - 1;
                        }
                    }
                }
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gDetector.onTouchEvent(event);
    }

    private int row = 12, col = 12;
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