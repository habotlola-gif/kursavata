package dev.hatek.client.feature.voice;

import io.github.givimad.whisperjni.WhisperContext;
import io.github.givimad.whisperjni.WhisperFullParams;
import io.github.givimad.whisperjni.WhisperJNI;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VoiceEngine {
    public static final int SAMPLE_RATE = 16_000;

    private static final int FRAME = SAMPLE_RATE / 50;

    private static final int PREROLL_FRAMES = 30;

    private static final int HANG_FRAMES = 40;

    private static final int MIN_FRAMES = 25;
    private static final int MAX_FRAMES = 300;

    private static final int ONSET_FRAMES = 3;

    private static final float MIN_LEVEL = 0.0025F;

    private static final int AUDIO_CTX = 768;

    private static String prompt() {
        return VoiceCommands.prompt();
    }

    private static final ConcurrentLinkedQueue<String> HEARD = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private static volatile Thread thread;
    private static volatile String status = "off";
    private static volatile String device = "";
    private static volatile boolean speaking;
    private static volatile float level;
    private static volatile float peak;
    private static volatile float sensitivity = 2.2F;
    private static volatile String modelPath = "";
    private static volatile String wantedDevice = "";

    private VoiceEngine() {
    }

    public static boolean running() {
        return RUNNING.get();
    }

    public static String status() {
        return status;
    }

    public static String device() {
        return device;
    }

    public static boolean speaking() {
        return speaking;
    }

    public static float level() {
        return level;
    }

    public static float takePeak() {
        float value = peak;
        peak = 0.0F;
        return value;
    }

    public static void sensitivity(float value) {
        sensitivity = value;
    }

    public static String poll() {
        return HEARD.poll();
    }

    public static List<String> devices() {
        List<String> names = new ArrayList<>();
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            try {
                if (AudioSystem.getMixer(mixerInfo).isLineSupported(info)) {
                    names.add(mixerInfo.getName());
                }
            } catch (Throwable ignored) {
            }
        }
        return names;
    }

    public static synchronized void start(String model, String preferredDevice) {
        if (RUNNING.get()) {
            return;
        }
        modelPath = model;
        wantedDevice = preferredDevice == null ? "" : preferredDevice.trim();
        RUNNING.set(true);
        status = "starting";
        Thread worker = new Thread(VoiceEngine::run, "Hatek Voice");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        thread = worker;
        worker.start();
    }

    public static synchronized void stop() {
        RUNNING.set(false);
        Thread worker = thread;
        if (worker != null) {
            worker.interrupt();
        }
        thread = null;
        speaking = false;
        level = 0.0F;
        peak = 0.0F;
        status = "off";
        HEARD.clear();
    }

    private static void run() {
        Path model = Path.of(modelPath);
        if (modelPath.isBlank() || !Files.isRegularFile(model)) {
            status = "no model at " + modelPath;
            RUNNING.set(false);
            return;
        }

        WhisperJNI whisper;
        WhisperContext ctx;
        try {
            WhisperJNI.loadLibrary();
            WhisperJNI.setLibraryLogger(null);
            whisper = new WhisperJNI();
            ctx = whisper.init(model);
        } catch (Throwable error) {
            status = "load failed: " + error;
            RUNNING.set(false);
            return;
        }

        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        TargetDataLine line = null;
        try {
            line = openLine(format);
            line.open(format, FRAME * 2 * 16);
            line.start();
            status = "listening";
            listen(line, whisper, ctx);
        } catch (Throwable error) {
            status = "microphone failed: " + error;
        } finally {
            if (line != null) {
                line.stop();
                line.close();
            }
            try {
                ctx.close();
            } catch (Throwable ignored) {
            }
            RUNNING.set(false);
            speaking = false;
            level = 0.0F;
        }
    }

    private static TargetDataLine openLine(AudioFormat format) throws Exception {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!wantedDevice.isEmpty()) {
            String wanted = wantedDevice.toLowerCase(Locale.ROOT);
            for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                if (!mixerInfo.getName().toLowerCase(Locale.ROOT).contains(wanted)) {
                    continue;
                }
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (mixer.isLineSupported(info)) {
                    device = mixerInfo.getName();
                    return (TargetDataLine) mixer.getLine(info);
                }
            }
            status = "no microphone named \"" + wantedDevice + "\", using default";
        }
        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        device = "default";
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (mixer.isLineSupported(info)) {
                    device = mixerInfo.getName() + " (default)";
                    break;
                }
            } catch (Throwable ignored) {
            }
        }
        return line;
    }

    private static void listen(TargetDataLine line, WhisperJNI whisper, WhisperContext ctx) {
        byte[] raw = new byte[FRAME * 2];
        float[][] preroll = new float[PREROLL_FRAMES][];
        int prerollAt = 0;
        int prerollHeld = 0;

        float[] phrase = new float[MAX_FRAMES * FRAME];
        int phraseLength = 0;

        float noiseFloor = 0.02F;
        int loudRun = 0;
        int quietRun = 0;
        boolean capturing = false;

        while (RUNNING.get()) {
            int read = line.read(raw, 0, raw.length);
            if (read < raw.length) {
                continue;
            }
            float[] frame = toFloat(raw, read);
            float rms = rms(frame);
            if (rms > peak) {
                peak = rms;
            }

            noiseFloor = rms < noiseFloor
                    ? noiseFloor * 0.90F + rms * 0.10F
                    : noiseFloor * 0.999F + rms * 0.001F;
            float threshold = Math.max(noiseFloor * sensitivity, MIN_LEVEL);
            boolean loud = rms > threshold;
            level = Math.min(1.0F, rms / Math.max(threshold, 1.0E-5F));

            if (!capturing) {
                preroll[prerollAt] = frame;
                prerollAt = (prerollAt + 1) % PREROLL_FRAMES;
                prerollHeld = Math.min(prerollHeld + 1, PREROLL_FRAMES);

                loudRun = loud ? loudRun + 1 : 0;
                if (loudRun < ONSET_FRAMES) {
                    continue;
                }
                capturing = true;
                speaking = true;
                phraseLength = 0;
                quietRun = 0;
                for (int i = 0; i < prerollHeld; i++) {
                    float[] old = preroll[(prerollAt + PREROLL_FRAMES - prerollHeld + i) % PREROLL_FRAMES];
                    if (old != null) {
                        System.arraycopy(old, 0, phrase, phraseLength, FRAME);
                        phraseLength += FRAME;
                    }
                }
                prerollHeld = 0;
                continue;
            }

            if (phraseLength + FRAME <= phrase.length) {
                System.arraycopy(frame, 0, phrase, phraseLength, FRAME);
                phraseLength += FRAME;
            }
            quietRun = loud ? 0 : quietRun + 1;

            boolean ended = quietRun >= HANG_FRAMES || phraseLength + FRAME > phrase.length;
            if (!ended) {
                continue;
            }

            capturing = false;
            speaking = false;
            loudRun = 0;
            int frames = phraseLength / FRAME;
            if (frames >= MIN_FRAMES) {
                int keep = Math.max(MIN_FRAMES, frames - HANG_FRAMES / 2) * FRAME;
                float[] speech = new float[Math.min(keep, phraseLength)];
                System.arraycopy(phrase, 0, speech, 0, speech.length);
                transcribe(whisper, ctx, speech);
            }
            phraseLength = 0;
        }
    }

    private static void transcribe(WhisperJNI whisper, WhisperContext ctx, float[] samples) {
        try {
            status = "recognising";
            WhisperFullParams params = new WhisperFullParams();
            params.language = "ru";
            params.translate = false;
            params.detectLanguage = false;
            params.audioCtx = AUDIO_CTX;
            params.noContext = true;
            params.singleSegment = true;
            params.printProgress = false;
            params.printRealtime = false;
            params.printTimestamps = false;
            params.printSpecial = false;
            params.suppressBlank = true;

            params.suppressNonSpeechTokens = true;

            params.temperature = 0.0F;
            params.temperatureInc = 0.0F;
            params.noSpeechThold = 0.6F;
            params.initialPrompt = prompt();

            params.nThreads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);

            if (whisper.full(ctx, params, samples, samples.length) != 0) {
                status = "listening";
                return;
            }
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < whisper.fullNSegments(ctx); i++) {
                text.append(whisper.fullGetSegmentText(ctx, i));
            }
            String heard = deloop(text.toString().trim());
            if (!heard.isEmpty() && !invented(heard)) {
                HEARD.add(heard);
            }
        } catch (Throwable error) {
            status = "recognition failed: " + error;
        } finally {
            if (RUNNING.get() && status.equals("recognising")) {
                status = "listening";
            }
        }
    }

    private static final String[] INVENTED = {
            "субтитры", "подпишись", "подписывайтесь", "спасибо за просмотр", "продолжение следует",
            "редактор субтитров", "корректор", "dimatorzok", "amara.org", "все на канале",
            "до новых встреч", "спасибо за внимание", "scrolling"};

    private static String deloop(String text) {
        String[] parts = text.split("\\.");
        if (parts.length < 3) {
            return text;
        }
        String first = parts[0].trim().toLowerCase(Locale.ROOT);
        int repeats = 0;
        for (String part : parts) {
            if (part.trim().toLowerCase(Locale.ROOT).equals(first)) {
                repeats++;
            }
        }
        return repeats >= 3 ? parts[0].trim() : text;
    }

    private static boolean invented(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String mark : INVENTED) {
            if (lower.contains(mark)) {
                return true;
            }
        }
        int letters = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                letters++;
            }
        }
        return letters < 2;
    }

    private static float[] toFloat(byte[] raw, int length) {
        ShortBuffer shorts = ByteBuffer.wrap(raw, 0, length)
                .order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        float[] out = new float[shorts.remaining()];
        for (int i = 0; i < out.length; i++) {
            out[i] = shorts.get(i) / 32768.0F;
        }
        return out;
    }

    private static float rms(float[] frame) {
        double sum = 0.0;
        for (float sample : frame) {
            sum += sample * sample;
        }
        return (float) Math.sqrt(sum / frame.length);
    }
}
