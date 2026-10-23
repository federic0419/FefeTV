package tv.fefetv.home;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * The scroll tick, synthesised at start-up (no audio files): a short sine that slides down in
 * pitch with a fast attack and an exponential tail. A lower tone for categories, a higher one
 * for the list.
 */
final class Sounds {
    private static final int RATE = 44100;
    private final AudioTrack category, item;

    Sounds() {
        category = make(1250);
        item = make(1650);
    }

    private static AudioTrack make(double base) {
        int n = RATE * 80 / 1000;
        short[] pcm = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double f = base * Math.pow(0.55, Math.min(1.0, t / 0.045));
            phase += 2 * Math.PI * f / RATE;
            double env = t < 0.004 ? t / 0.004 : Math.exp(-(t - 0.004) * 70);
            pcm[i] = (short) (Math.sin(phase) * env * 0.22 * Short.MAX_VALUE);
        }
        try {
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(n * 2)
                    .build();
            track.write(pcm, 0, n);
            return track;
        } catch (RuntimeException e) {
            return null;
        }
    }

    void play(boolean isCategory) {
        AudioTrack t = isCategory ? category : item;
        if (t == null) return;
        try {
            if (t.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) t.stop();
            t.reloadStaticData();
            t.play();
        } catch (IllegalStateException ignored) {
            // the track was released or never initialised: stay silent
        }
    }

    void release() {
        if (category != null) category.release();
        if (item != null) item.release();
    }
}
