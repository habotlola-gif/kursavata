package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.ButtonSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientSound: a sound when a module is switched on or off. Three effects to choose from; the "on" version rises in
 * pitch, the "off" version falls. The buttons play both for a preview.
 */
public final class ClientSound extends Module {
    private static final String CLICK = "Клик";
    private static final String BELL = "Колокольчик";
    private static final String CRYSTAL = "Кристалл";

    private static ClientSound instance;

    private record Pending(int ticks, SoundEvent sound, float pitch) {
    }

    private final ModeSetting effect = new ModeSetting("Эффект", 0, CLICK, BELL, CRYSTAL);
    private final SliderSetting volume = new SliderSetting("Громкость (%)", 60.0, 10.0, 100.0, 5.0);
    private final ButtonSetting previewOn = new ButtonSetting("Прослушать", "Включение", () -> play(true));
    private final ButtonSetting previewOff = new ButtonSetting("Прослушать", "Выключение", () -> play(false));

    private final List<Pending> queue = new ArrayList<>();

    public ClientSound() {
        super("ClientSound", "A sound when a module is switched on or off (three effects)", Category.RENDER);
        instance = this;
        with(this.effect, this.volume, this.previewOn, this.previewOff);
    }

    public static ClientSound instance() {
        return instance;
    }

    /** Called by Module.toggle after the state changed. */
    public static void moduleToggled(Module module, boolean enabled) {
        ClientSound self = instance;
        if (self == null || !self.isEnabled() || Notifications.isSuppressed()) {
            return;
        }
        if (module == self && !enabled) {
            return;
        }
        self.play(enabled);
    }

    @Override
    protected void onDisable() {
        this.queue.clear();
    }

    /** Called every client tick (also while the module is off, so a preview can finish its second note). */
    public static void tickQueue() {
        if (instance != null) {
            instance.tick();
        }
    }

    private void tick() {
        for (int i = this.queue.size() - 1; i >= 0; i--) {
            Pending pending = this.queue.get(i);
            if (pending.ticks() <= 0) {
                this.queue.remove(i);
                emit(pending.sound(), pending.pitch());
            } else {
                this.queue.set(i, new Pending(pending.ticks() - 1, pending.sound(), pending.pitch()));
            }
        }
    }

    private void play(boolean on) {
        switch (this.effect.value()) {
            case BELL -> {
                // two notes: rising for "on", falling for "off"
                SoundEvent bell = SoundEvents.NOTE_BLOCK_BELL.value();
                emit(bell, on ? 1.0F : 1.5F);
                this.queue.add(new Pending(2, bell, on ? 1.5F : 1.0F));
            }
            case CRYSTAL -> {
                SoundEvent chime = SoundEvents.AMETHYST_BLOCK_CHIME;
                emit(chime, on ? 1.5F : 0.8F);
                this.queue.add(new Pending(2, chime, on ? 1.9F : 0.6F));
            }
            default -> emit(SoundEvents.UI_BUTTON_CLICK.value(), on ? 1.3F : 0.8F);
        }
    }

    private void emit(SoundEvent sound, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, (float) this.volume.value() / 100.0F));
        }
    }
}
