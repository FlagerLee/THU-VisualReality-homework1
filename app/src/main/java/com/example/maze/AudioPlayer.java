package com.example.maze;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Scanner;

public class AudioPlayer {
    private AudioTrack soundTrack;
    private Thread playThread;

    double[][][] hrir_left  = new double[25][50][200]; // HRIR left ear data
    double[][][] hrir_right = new double[25][50][200]; // HRIR right ear data

    final int[] Azimut = new int[] {
            -80, -65, -55, -45, -40, -35, -30, -25, -20, -15, -10, -5, 0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 55, 65, 80
    };
    int azimut = 12;
    final int[] Elevation = new int[] {
            -45, -39, -34, -28, -23, -17, -11, -6, 0, 6, 11, 17, 23, 28, 34, 39, 45, 51, 56, 62, 68, 73, 79, 84, 90, 96, 101, 107, 113, 118, 124, 129, 135, 141, 146, 152, 158, 163, 169, 174, 180, 186, 191, 197, 203, 208, 214, 219, 225, 231
    };
    int elevation = 25;

    boolean playing = false;

    private int mMinBufferSize;

    DataInputStream inputStream;

    AudioPlayer(Context context, String hrir_path, String audio_path) {
        // initialize hrir data
        mMinBufferSize = AudioTrack.getMinBufferSize(44100,AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);
        try {
            Scanner scanner = new Scanner(context.getAssets().open(hrir_path));
            for(int i = 0; i < 25; i ++) {
                for(int j = 0; j < 50; j ++) {
                    for(int k = 0; k < 200; k ++) {
                        hrir_left[i][j][k] = scanner.nextDouble();
                    }
                }
            }
            for(int i = 0; i < 25; i ++) {
                for(int j = 0; j < 50; j ++) {
                    for(int k = 0; k < 200; k ++) {
                        hrir_right[i][j][k] = scanner.nextDouble();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        // initialize audio track
        try {
            inputStream = new DataInputStream(context.getAssets().open(audio_path));
        } catch (Exception e) {

        }

        // initialize sound track
        int temp = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        soundTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setLegacyStreamType(android.media.AudioManager.STREAM_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(44100)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(temp * 2)
                .build();
    }

    void recyclePlay() {
        if(playing) return;
        playing = true;
        playThread = new Thread(playRunnable);
        playThread.start();
    }

    Runnable playRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                byte[] tempBuffer = new byte[mMinBufferSize];
                while(true){
                    inputStream.skipBytes(44);
                    while (inputStream.available() > 0) {
                        int length = inputStream.read(tempBuffer);
                        Complex[]wavFile = byteToComplex(tempBuffer);

                        byte[] soundLeft = complexToByte(FFT.cconvolve(wavFile, doubleToComplex(hrir_left[azimut][elevation])));
                        byte[] soundRight = complexToByte(FFT.cconvolve(wavFile, doubleToComplex(hrir_right[azimut][elevation])));

                        byte[] sound = new byte[length * 2];
                        for(int i = 0; i < length; i += 2)
                        {
                            sound[i * 2] = soundLeft[i];
                            sound[i * 2 + 1] = soundLeft[i + 1];
                            sound[i * 2 + 2] = soundRight[i];
                            sound[i * 2 + 3] = soundRight[i + 1];
                        }
                        soundTrack.write(sound, 0, sound.length);
                        soundTrack.play();
                    }
                    inputStream.reset();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private byte[] complexToByte(Complex[] com) {
        int len = com.length;
        byte[] buffer = new byte[len * 2];
        for(int i = 0; i < len; i ++) {
            int re = (int)com[i].re();
            buffer[i * 2] = (byte)(re & 0xFF);
            buffer[i * 2 + 1] = (byte)((re >> 8) & 0xFF);
        }
        return buffer;
    }

    private Complex[] byteToComplex(byte[] bytes) {
        int len = bytes.length;
        Complex[] buffer = new Complex[16384];
        for(int i = 0; i < len / 2; i ++) {
            buffer[i] = new Complex((float)((int)bytes[i * 2 + 1] << 8 | ((int)bytes[i * 2] & 0xFF)), 0f);
        }
        for(int i = len / 2; i < 16384; i ++) buffer[i] = new Complex(0f, 0f);
        return buffer;
    }

    private Complex[] doubleToComplex(double []buffer)
    {
        Complex[] temp = new Complex[16384];
        for(int i=0;i<16384;i++)
        {
            if(i < buffer.length)
                temp[i] = new Complex(buffer[i], 0.0);
            else temp[i] = new Complex(0,0);
        }
        return temp;
    }

    void updateAngle(double azimutAngle, double elevationAngle) {
        azimut = (int)((180 - azimutAngle) / 7.2);
        if(azimut > 24) azimut = 24;
        if(azimut < 0) azimut = 0;
        elevationAngle += 45;
        elevation = (int)(elevationAngle / 5.675);
        if(elevation > 49) elevation = 49;
        if(elevation < 0) elevation = 0;
        Log.i("updata", "azimut: " + azimut + "; elevation: " + elevation);
    }
}
