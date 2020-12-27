package com.example.maze;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public class Wav {
    static private class wav_header {
        public int ChunkID;
        public int ChunkSize;
        public String Format;
        public int SubChunk1ID;
        public int SubChunk1Size;
        public String AudioFormat;
        public short NumChannels;
        public int SampleRate;
        public int ByteRate;
        public short BlockAlign;
        public short BitsPerSample;
        public int SubChunk2ID;
        public int SubChunk2Size;
    }

    private byte[] wav_data;
    private int data_len;
    private wav_header header = new wav_header();

    Wav(Context context, String wav_path) {
        try {
            BufferedInputStream wav_stream = new BufferedInputStream(context.getAssets().open(wav_path));
            if(wav_stream == null) {
                Log.i("Error In Wav", "wav_stream is null");
                Log.i("Error In Wav", wav_path);
                Log.i("Error In Wav", context.getAssets().open((wav_path)).toString());
            }
            header.ChunkID          = read_int(wav_stream);
            header.ChunkSize        = read_int(wav_stream);
            header.Format           = read_string(wav_stream, 4);
            header.SubChunk1ID      = read_int(wav_stream);
            header.SubChunk1Size    = read_int(wav_stream);
            header.AudioFormat      = read_string(wav_stream, 2);
            header.NumChannels      = read_short(wav_stream);
            header.SampleRate       = read_int(wav_stream);
            header.ByteRate         = read_int(wav_stream);
            header.BlockAlign       = read_short(wav_stream);
            header.BitsPerSample    = read_short(wav_stream);
            header.SubChunk2ID      = read_int(wav_stream);
            header.SubChunk2Size    = read_int(wav_stream);

            Log.i("SubChunk2Size", header.SubChunk2Size + " ");
            data_len = header.SubChunk2Size - 8;
            wav_data = new byte[data_len];
            if(wav_stream.read(wav_data) != data_len) throw new IOException("Read Error When Reading Data");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    byte[] getWav_data() {
        return wav_data;
    }

    int getData_len() {
        return data_len;
    }

    private short read_short(BufferedInputStream stream) throws IOException {
        byte[] buffer = new byte[2];
        if(stream.read(buffer) != 2) throw new IOException("Read Error When Reading Short");
        return (short)(((short)(buffer[1] & 0xff) << 8) | (short)(buffer[0] & 0xff));
    }
    private int read_int(BufferedInputStream stream) throws IOException {
        byte[] buffer = new byte[4];
        if(stream.read(buffer) != 4) throw new IOException("Read Error When Reading Integer");
        Log.i("read int", buffer[0] + " " + buffer[1] + " " + buffer[2] + " " + buffer[3]);
        return (int)(((int)(buffer[3] & 0xff) << 24) | ((int)(buffer[2] & 0xff) << 16) | ((int)(buffer[1] & 0xff) << 8) | (int)(buffer[0] & 0xff));
    }
    private String read_string(BufferedInputStream stream, int len) throws IOException {
        byte[] buffer = new byte[len];
        if(stream.read(buffer) != len) throw new IOException("Read Error When Reading String");
        return new String(buffer);
    }
}
