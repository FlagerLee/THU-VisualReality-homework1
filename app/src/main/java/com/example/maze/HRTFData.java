package com.example.maze;

import android.content.res.AssetManager;

import java.io.IOException;
import java.io.InputStream;

public class HRTFData {
    int data_left[][][]  = new int[25][50][200]; // HRTF left ear data
    int data_right[][][] = new int[25][50][200]; // HRTF right ear data

    final int Azimut[] = new int[] {
            -80, -65, -55, -45, -40, -35, -30, -25, -20, -15, -10, -5, 0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 55, 65, 80
    };
    final int Elevation[] = new int[] {
            -45, -39, -34, -28, -23, -17, -11, -6, 0, 6, 11, 17, 23, 28, 34, 39, 45, 51, 56, 62, 68, 73, 79, 84, 90, 96, 101, 107, 113, 118, 124, 129, 135, 141, 146, 152, 158, 163, 169, 174, 180, 186, 191, 197, 203, 208, 214, 219, 225, 231
    };

    boolean load_success;
    HRTFData(AssetManager assets) {
        String RootPath = "subject03/";
        byte[] buffer = new byte[20044];
        for(int i = 0; i < 25; i ++) {
            StringBuilder left_builder = new StringBuilder(Azimut[i]);
            StringBuilder right_builder = new StringBuilder(Azimut[i]);
            if(Azimut[i] < 0) left_builder.replace(0, 1, "neg");
            if(Azimut[i] < 0) right_builder.replace(0, 1, "neg");
            left_builder.append("azleft.wav");
            right_builder.append("azright.wav");
            String left_path = RootPath + left_builder.toString();
            String right_path = RootPath + right_builder.toString();
            try {
                InputStream left_wav = assets.open(left_path);
                left_wav.read(buffer);
                int start = 44;
                for(int elevations = 0; elevations < 50; elevations ++) {
                    for(int j = 0; j < 200; j ++) data_left[i][elevations][j] = bytesToInt16(buffer[start + j * 2], buffer[start + j * 2 + 1]);
                    start += 400;
                }
                left_wav.close();
                InputStream right_wav = assets.open(right_path);
                right_wav.read(buffer);
                start = 44;
                for(int elevations = 0; elevations < 50; elevations ++) {
                    for(int j = 0; j < 200; j ++) data_right[i][elevations][j] = bytesToInt16(buffer[start + j * 2], buffer[start + j * 2 + 1]);
                    start += 400;
                }
                right_wav.close();
            } catch (IOException e) {
                load_success = false;
                return;
            }
        }
        load_success = true;
    }

    private int bytesToInt16(byte byte_hi, byte byte_lo) {
        return (byte_lo & 0xFF) + ((byte_hi & 0xFF) << 8);
    }

    public int[] getData(float azimut, float elevation, boolean leftOrRight) {
        int a_id = 0, e_id = 0;
        if(azimut <= -90.0 || azimut > 90.0) return null;
        if(azimut > -90.0 && azimut <= -80.0) a_id = 0;
        else if(azimut > 80.0 && azimut <= 90.0) a_id = 24;
        else for(int i = 0; i < 24; i ++) {
            if(azimut > Azimut[i] && azimut <= Azimut[i + 1]) {
                if(azimut - Azimut[i] > Azimut[i + 1] - azimut) a_id = i + 1;
                else a_id = i;
                break;
            }
        }
        if(elevation <= -90.0 || elevation > 270.0) return null;
        if(elevation > -90.0 && elevation <= -45.0) e_id = 0;
        else if(elevation > 231.0 && elevation <= 270.0) e_id = 49;
        else for(int i = 0; i < 49; i ++) {
            if(elevation > Elevation[i] && elevation <= Elevation[i + 1]) {
                if(elevation - Elevation[i] > Elevation[i + 1] - elevation) e_id = i + 1;
                else e_id = i;
                break;
            }
        }
        return leftOrRight ? data_left[a_id][e_id] : data_right[a_id][e_id];
    }
}
