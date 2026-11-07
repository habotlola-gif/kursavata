package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;

/** Changes the time of day on your screen only (the server time is untouched): fixed, or a faster/slower cycle. */
public final class TimeChanger extends Module {
    private static TimeChanger instance;
    private static final long START = System.nanoTime();

    private final ModeSetting time = new ModeSetting("Time", 1, "Day", "Noon", "Sunset", "Night", "Midnight", "Custom");
    private final SliderSetting custom = new SliderSetting("Custom (ticks)", 6000.0, 0.0, 24000.0, 250.0);
    private final ModeSetting flow = new ModeSetting("Flow", 0, "Frozen", "Cycle");
    private final SliderSetting cycleSpeed = new SliderSetting("Cycle speed", 20.0, 1.0, 200.0, 1.0);

    public TimeChanger() {
        super("TimeChanger", "Changes the time of day on your screen", Category.RENDER);
        instance = this;
        with(this.time, this.custom, this.flow, this.cycleSpeed);
    }

    public static TimeChanger instance() {
        return instance;
    }

    /** Replaces the world clock's total ticks (the day length is 24000 ticks; 6000 is noon, 18000 is midnight). */
    public static long override(long serverTicks) {
        TimeChanger self = instance;
        if (self == null || !self.isEnabled()) {
            return serverTicks;
        }
        long base = switch (self.time.value()) {
            case "Day" -> 1000L;
            case "Noon" -> 6000L;
            case "Sunset" -> 12000L;
            case "Night" -> 13000L;
            case "Midnight" -> 18000L;
            default -> Math.round(self.custom.value());
        };
        if (self.flow.is("Cycle")) {
            double elapsedTicks = (System.nanoTime() - START) / 50.0e6;
            base += Math.round(elapsedTicks * self.cycleSpeed.value() / 20.0);
        }
        return Math.floorMod(base, 24000L);
    }
}
