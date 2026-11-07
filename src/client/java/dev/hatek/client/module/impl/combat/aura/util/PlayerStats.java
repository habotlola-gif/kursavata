package dev.hatek.client.module.impl.combat.aura.util;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PlayerStats {
    private static final float ANGLE_BIN = 1.0F;
    private static final int ANGLE_BINS = 121;

    private static final int MS_BIN = 10;
    private static final int MS_BINS = 200;

    private static final int ENOUGH_TURN = 600;
    private static final int ENOUGH_CLICK = 40;

    private static final float MOVEMENT_FLOOR = 0.8F;

    private static final float MICRO_BIN = 0.05F;
    private static final int MICRO_BINS = 20;

    private static final int PAUSE_BINS = 8;

    private static final int[] TURN = new int[ANGLE_BINS];
    private static final int[] ACCEL = new int[ANGLE_BINS];
    private static final int[] CLICK = new int[MS_BINS];
    private static final int[] MICRO = new int[MICRO_BINS];
    private static final int[] PAUSE = new int[PAUSE_BINS];

    private static int turnSamples;
    private static int accelSamples;
    private static int clickSamples;
    private static int microSamples;
    private static int pauseSamples;

    private static int movingTicks;

    private static int idleRun;

    private static float pendingYaw;
    private static float pendingPitch;

    private static float lastTurn;
    private static boolean haveLast;

    private static long lastClickMs;
    private static boolean dirty;
    private static long lastSaveMs;

    private PlayerStats() {
    }

    public static void frame(double yawDegrees, double pitchDegrees) {
        pendingYaw += (float) yawDegrees;
        pendingPitch += (float) pitchDegrees;
    }

    public static void tick() {
        float turn = (float) Math.hypot(pendingYaw, pendingPitch);
        pendingYaw = 0.0F;
        pendingPitch = 0.0F;

        if (turn < MOVEMENT_FLOOR) {
            if (turn > 1.0E-4F) {
                MICRO[Math.min(MICRO_BINS - 1, (int) (turn / MICRO_BIN))]++;
                microSamples++;
                dirty = true;
            }
            idleRun++;
            haveLast = false;
            return;
        }

        if (idleRun > 0 && idleRun <= PAUSE_BINS) {
            PAUSE[idleRun - 1]++;
            pauseSamples++;
        }
        idleRun = 0;
        movingTicks++;

        add(TURN, turn);
        turnSamples++;

        if (haveLast) {
            add(ACCEL, Math.abs(turn - lastTurn));
            accelSamples++;
        }
        lastTurn = turn;
        haveLast = true;
        dirty = true;

        maybeSave();
    }

    public static void click() {
        long now = System.currentTimeMillis();
        long previous = lastClickMs;
        lastClickMs = now;
        if (previous == 0L) {
            return;
        }
        long gap = now - previous;
        if (gap < 20L || gap >= (long) MS_BIN * MS_BINS) {
            return;
        }
        CLICK[(int) (gap / MS_BIN)]++;
        clickSamples++;
        dirty = true;
    }

    public static boolean turnReady() {
        return turnSamples >= ENOUGH_TURN;
    }

    public static boolean clickReady() {
        return clickSamples >= ENOUGH_CLICK;
    }

    public static boolean microReady() {
        return microSamples >= ENOUGH_TURN;
    }

    public static boolean pauseReady() {
        return pauseSamples >= 40;
    }

    public static float micro(float p) {
        return percentile(MICRO, microSamples, p) * MICRO_BIN;
    }

    public static int pauseLength(float p) {
        return Math.max(1, Math.round(percentile(PAUSE, pauseSamples, p) + 0.5F));
    }

    public static float pauseEvery() {
        return pauseSamples <= 0 ? 0.0F : (float) movingTicks / pauseSamples;
    }

    public static int turnSamples() {
        return turnSamples;
    }

    public static int clickSamples() {
        return clickSamples;
    }

    public static float turn(float p) {
        return percentile(TURN, turnSamples, p) * ANGLE_BIN;
    }

    public static float accel(float p) {
        return percentile(ACCEL, accelSamples, p) * ANGLE_BIN;
    }

    public static float click(float p) {
        return percentile(CLICK, clickSamples, p) * MS_BIN;
    }

    private static void add(int[] bins, float value) {
        int i = (int) (value / ANGLE_BIN);
        bins[Math.min(bins.length - 1, Math.max(0, i))]++;
    }

    private static float percentile(int[] bins, int total, float p) {
        if (total <= 0) {
            return 0.0F;
        }
        int want = (int) Math.ceil(total * p);
        int seen = 0;
        for (int i = 0; i < bins.length; i++) {
            seen += bins[i];
            if (seen >= want) {
                return i + 0.5F;
            }
        }
        return bins.length - 0.5F;
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("hatek").resolve("hand.txt");
    }

    private static void maybeSave() {
        long now = System.currentTimeMillis();
        if (!dirty || now - lastSaveMs < 30_000L) {
            return;
        }
        lastSaveMs = now;
        dirty = false;
        save();
    }

    public static void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            StringBuilder out = new StringBuilder();
            write(out, "turn", TURN, turnSamples);
            write(out, "accel", ACCEL, accelSamples);
            write(out, "click", CLICK, clickSamples);
            write(out, "micro", MICRO, microSamples);
            write(out, "pause", PAUSE, pauseSamples);
            out.append("moving ").append(movingTicks).append('\n');
            Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) {
        }
    }

    public static void load() {
        try {
            Path path = file();
            if (!Files.exists(path)) {
                return;
            }
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String[] parts = line.split(" ");
                if (parts.length < 2) {
                    continue;
                }
                if (parts[0].equals("moving")) {
                    movingTicks = Integer.parseInt(parts[1]);
                    continue;
                }
                int[] bins = switch (parts[0]) {
                    case "turn" -> TURN;
                    case "accel" -> ACCEL;
                    case "click" -> CLICK;
                    case "micro" -> MICRO;
                    case "pause" -> PAUSE;
                    default -> null;
                };
                if (bins == null) {
                    continue;
                }
                int total = 0;
                for (int i = 1; i < parts.length && i - 1 < bins.length; i++) {
                    int v = Integer.parseInt(parts[i]);
                    bins[i - 1] = v;
                    total += v;
                }
                switch (parts[0]) {
                    case "turn" -> turnSamples = total;
                    case "accel" -> accelSamples = total;
                    case "click" -> clickSamples = total;
                    case "micro" -> microSamples = total;
                    case "pause" -> pauseSamples = total;
                    default -> {
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static void write(StringBuilder out, String name, int[] bins, int total) {
        if (total <= 0) {
            return;
        }
        List<String> parts = new ArrayList<>();
        for (int bin : bins) {
            parts.add(Integer.toString(bin));
        }
        out.append(name).append(' ').append(String.join(" ", parts)).append('\n');
    }
}
