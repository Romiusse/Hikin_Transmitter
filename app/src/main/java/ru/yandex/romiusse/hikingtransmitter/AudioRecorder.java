package ru.yandex.romiusse.hikingtransmitter;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.util.Log;

public class AudioRecorder extends AsyncTask<Void, Void, Void> {

    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private AudioRecord recorder = null;
    private byte[] bData = new byte[2048];
    private boolean isRecording = true;

    //convert short to byte
    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];
        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;

    }

    public byte[] getMicData() {
        return bData.clone();
    }

    public void stopRecording() {
        isRecording = false;
    }


    protected Void doInBackground(Void... params) {
        Log.println(Log.ERROR, "LOG", ">>> Starting mic record");
        /*
         * Initialize buffer to hold continuously recorded audio data, start recording
         */
        try {

            int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                    RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);

            int bufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
            int bytesPerElement = 2; // 2 bytes in 16bit format

            short[] sData = new short[bufferElements2Rec];


            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                    RECORDER_AUDIO_ENCODING, bufferElements2Rec * bytesPerElement);


            recorder.startRecording();

            while (isRecording) {
                // gets the voice output from microphone to byte format

                recorder.read(sData, 0, bufferElements2Rec);
                bData = short2byte(sData);

            }

        } finally {
            recorder.stop();
            recorder.release();
        }
        return null;
    }


    protected void onPostExecute(Void result) {
        Log.println(Log.ERROR, "LOG", "UDP Server closed");
    }
}
