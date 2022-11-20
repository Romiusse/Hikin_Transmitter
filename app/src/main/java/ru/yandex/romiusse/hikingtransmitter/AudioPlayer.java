package ru.yandex.romiusse.hikingtransmitter;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.util.Log;

import java.util.Arrays;

public class AudioPlayer extends AsyncTask<Void, Void, Void> {

    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_OUT_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private boolean isPlaying = true;
    private static byte[] bData = new byte[2048];

    public void stopRecording() {
        isPlaying = false;
    }
    public void setMicData(byte[] data){ bData = data.clone();}

    protected Void doInBackground(Void... params) {

        int minBufferSize = AudioTrack.getMinBufferSize(RECORDER_SAMPLERATE,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
        AudioTrack at = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize*10, AudioTrack.MODE_STREAM);

        at.play();
        byte[] lastData = new byte[2048];
        while (isPlaying) {
            // gets the voice output from microphone to byte format

            if(!Arrays.equals(lastData, bData)) {
                at.write(bData, 0, 2048);
                lastData = bData.clone();
                //System.out.println(Arrays.toString(bData));
            }

        }


        return null;

    }
    protected void onPostExecute(Void result) {
        Log.println(Log.ERROR, "LOG", "UDP Server closed");
    }

}
