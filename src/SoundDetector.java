import javax.sound.sampled.*;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.ArrayList;

/**
 * Created by User on 27/02/2017.
 */
public class SoundDetector implements Runnable {
    private static final int     TIMER           = 5;     /* secs */
    private static final String  FILENAME        = "temp.wav";

    // For now = 0.1, will change later to dynamically adapt
    private static volatile float THRESHOLD;
    private static final int     SAMPLE_RATE     = 16000; /* MHz  */
    private static final int     SAMPLE_SIZE     = 16;    /* bits */
    private static final int     SAMPLE_CHANNELS = 1;     /* mono */
    private AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE, SAMPLE_CHANNELS, true, true);
    private AudioInputStream ais;
    private SoundDetectionThread soundDetector;

    private ArrayList<ActionListener> listeners= new ArrayList<>();

    private boolean micOn = true;
    private boolean running = true;


    void calibrateMic(SoundDetectionThread detector) {
        THRESHOLD = -0.75f;
        while (detector.soundDetected()) {
            System.out.println("THRESHOLD NOW: " + THRESHOLD);
            THRESHOLD += 0.05f;
        }
        THRESHOLD += 0.13f;
    }

    @Override
    /**
     * Run method of the thread, will listen for audio whilst in listening mode & record audio if it hears anything
     */
    public void run() {
        try {
            final int bufferByteSize = format.getFrameSize() * SAMPLE_RATE;
            TargetDataLine line;

            line = AudioSystem.getTargetDataLine(format);
            line.open(format, bufferByteSize);
            line.start();

            ais = new AudioInputStream(line);
            soundDetector = new SoundDetectionThread(line, bufferByteSize);
            soundDetector.start();
            calibrateMic(soundDetector);
            System.out.println("Started silenceDetector");

            while (running) {
                try {
                    if (soundDetector.soundDetected()) {
                        System.out.println("Detected Audio, starting recording..");
                        startRecording();
                    }
                    Thread.currentThread().sleep(10);
                } catch (InterruptedException e) {
                    System.out.println("Interrupted exception - this shouldn't have happened.");
                    e.printStackTrace();
                    System.exit(1);
                }

            }

        } catch (LineUnavailableException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Method to record a given amount of audio (TIMER) and store it to a file (FILENAME) as a wave file.
     */
    private void startRecording() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            int bufferSize = SAMPLE_RATE * ais.getFormat().getFrameSize();
            byte buffer[] = new byte[bufferSize];

            int counter = TIMER;
            while (counter > 0 || soundDetector.soundDetected()) {
                counter--;
                int n = ais.read(buffer, 0, buffer.length);
                if (n > 0) {
                    bos.write(buffer, 0, n);
                } else {
                    break;
                }
            }

            byte[] ba = bos.toByteArray();
            InputStream is = new ByteArrayInputStream(ba);
            AudioInputStream ais = new AudioInputStream(is, format, ba.length);
            File file = new File(FILENAME);
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file);

            System.out.println("Recorded a new audio file, notifying listeners..");
            SoundRecordedEvent event = new SoundRecordedEvent(this, 1, "soundDetected");
            for (ActionListener listener : listeners) {
                listener.actionPerformed(event);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Method to register as an EventListener for any new sound recordings
     * @param listener the EventListener to register
     * @return true if successful
     */
    boolean registerRecordingListener(ActionListener listener) {
        return listeners.add(listener);
    }

    /**
     * Method to unregister as an EventListener for any new sound recordings
     * @param listener the EventListener to unregister
     * @return true if successful
     */
    boolean unregisterRecordingListener(ActionListener listener) {
        return listeners.remove(listener);
    }

    void enableMic() {
        micOn = true;
    }

    void disableMic() {
        micOn = false;
    }

    void shutdown() {
        running = false;
    }

    private class SoundDetectionThread extends Thread {
        private TargetDataLine line;
        private int bufferSize;
        private float lastAmplitude;

        SoundDetectionThread(TargetDataLine line, int bufferSize) {
            this.line = line;
            this.bufferSize = bufferSize;
        }

        boolean soundDetected() {
            return lastAmplitude > THRESHOLD;
        }

        @Override
        public void run() {
            while (running) {
                byte[] buf = new byte[bufferSize];
                float[] samples = new float[bufferSize / 2];
                while (running && micOn) {
                    int b = line.read(buf, 0, buf.length);
                    for (int i = 0, s = 0; i < b; ) {
                        int sample = 0;

                        // Converting the bytes to floats
                        sample |= buf[i++] << 8;
                        sample |= buf[i++] & 0xFF;

                        // Reducing it to the range -1 to +1
                        samples[s++] = sample / 32768f;
                    }

                    // Calculating the RMS amplitude
                    float rms = 0f;
                    float peak = 0f;
                    for (float sample : samples) {
                        float abs = Math.abs(sample);
                        if (abs > peak) {
                            peak = abs;
                        }
                        rms += sample * sample;
                    }
                    rms = (float) Math.sqrt(rms / samples.length);
                    lastAmplitude = rms;
                    try {
                        sleep(11);
                    } catch (InterruptedException e) {
                        System.out.println("Interrupted exception - this shouldn't have happened.");
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
            }
        }
    }
}
