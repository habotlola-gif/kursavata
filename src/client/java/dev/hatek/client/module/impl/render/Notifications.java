package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.MultiSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Notifications: small pills (designs notify-on / notify-off / notify-warning) for a module switched on or off, a
 * totem used up ("Осталось тотемов: N") and a potion effect that is about to run out.
 */
public final class Notifications extends Module {
    public static final String MODULES = "Включение / выключение модулей";
    public static final String TOTEMS = "Остаток тотемов";
    public static final String EFFECTS = "Эффект скоро закончится";

    private static final float DESIGN_W = 460.0f;
    private static final float DESIGN_H = 72.0f;
    private static final float GAP = 10.0f;
    private static final int MAX_TOASTS = 5;
    private static final long FADE_IN_MS = 220L;
    private static final long FADE_OUT_MS = 380L;

    private static final int BACKGROUND = 0x990D0D10;
    private static final int TRACK_ON = 0xFF9D93E6;
    private static final int TRACK_OFF = 0xFF2A2832;
    private static final int KNOB_OFF = 0xFF6E6B7A;
    private static final int TEXT_ON = 0xFFB3A8F0;
    private static final int TEXT_OFF = 0xFF8A8794;
    private static final int WARN = 0xFFE8C23A;
    private static final int WARN_FILL = 0x2EE8C23A;
    private static final int SUBTITLE = 0xFFC9C6D4;

    private static Notifications instance;
    /** Set while configs are applied so loading does not produce a notification for every module. */
    private static boolean suppressed;

    private enum Kind { ON, OFF, WARNING }

    private static final class Toast {
        final Kind kind;
        final String title;
        final String subtitle;
        final long created = System.currentTimeMillis();
        float slot;

        Toast(Kind kind, String title, String subtitle, float slot) {
            this.kind = kind;
            this.title = title;
            this.subtitle = subtitle;
            this.slot = slot;
        }
    }

    private final MultiSetting events = new MultiSetting("Уведомления", List.of(MODULES, TOTEMS, EFFECTS), MODULES, TOTEMS, EFFECTS);
    private final ModeSetting position = new ModeSetting("Позиция", 0, "Сверху по центру", "Сверху справа", "Снизу справа");
    private final SliderSetting scale = new SliderSetting("Размер (%)", 100.0, 50.0, 200.0, 5.0);
    private final SliderSetting duration = new SliderSetting("Время показа (с)", 3.0, 1.0, 8.0, 0.5);
    private final SliderSetting effectSeconds = new SliderSetting("Эффект: предупредить за (с)", 10.0, 3.0, 60.0, 1.0);

    private final List<Toast> toasts = new ArrayList<>();
    private int lastTotems = -1;
    private final Set<String> longSeen = new HashSet<>();
    private final Set<String> warned = new HashSet<>();

    public Notifications() {
        super("Notifications", "Pop-up notices: module on/off, totems left, effect ending", Category.RENDER);
        instance = this;
        with(this.events, this.position, this.scale, this.duration, this.effectSeconds);
    }

    public static Notifications instance() {
        return instance;
    }

    public static void suppress(boolean value) {
        suppressed = value;
    }

    public static boolean isSuppressed() {
        return suppressed;
    }

    /** Called by Module.toggle after the state changed. */
    public static void moduleToggled(Module module, boolean enabled) {
        Notifications self = instance;
        if (self == null || suppressed || !self.isEnabled() || module == self || !self.events.isSelected(MODULES)) {
            return;
        }
        self.push(enabled ? Kind.ON : Kind.OFF, module.name(), "");
    }

    @Override
    protected void onEnable() {
        this.lastTotems = -1;
        this.longSeen.clear();
        this.warned.clear();
    }

    @Override
    protected void onDisable() {
        this.toasts.clear();
    }

    private void push(Kind kind, String title, String subtitle) {
        float slot = this.toasts.size();
        this.toasts.add(new Toast(kind, title, subtitle, slot));
        while (this.toasts.size() > MAX_TOASTS) {
            this.toasts.remove(0);
        }
    }

    // ------------------------------------------------------------------------------------------------ events

    @Override
    public void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            this.lastTotems = -1;
            this.longSeen.clear();
            this.warned.clear();
            return;
        }
        if (this.events.isSelected(TOTEMS)) {
            checkTotems(player);
        }
        if (this.events.isSelected(EFFECTS)) {
            checkEffects(player);
        }
    }

    private void checkTotems(LocalPlayer player) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                count += stack.getCount();
            }
        }
        if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            count += player.getOffhandItem().getCount();
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.is(Items.TOTEM_OF_UNDYING)) {
            count += carried.getCount();
        }
        if (this.lastTotems >= 0 && count < this.lastTotems) {
            if (count == 0) {
                push(Kind.WARNING, "Тотемы закончились", "У тебя не осталось тотемов");
            } else {
                push(Kind.WARNING, "Тотем использован", "У тебя осталось " + count + " " + totemWord(count));
            }
        }
        this.lastTotems = count;
    }

    private static String totemWord(int n) {
        int mod100 = n % 100;
        int mod10 = n % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return "тотемов";
        }
        return mod10 == 1 ? "тотем" : (mod10 >= 2 && mod10 <= 4 ? "тотема" : "тотемов");
    }

    private void checkEffects(LocalPlayer player) {
        int limit = (int) Math.round(this.effectSeconds.value() * 20.0);
        Set<String> present = new HashSet<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (effect.isInfiniteDuration() || !effect.getEffect().value().isBeneficial()) {
                continue;
            }
            String key = effect.getEffect().getRegisteredName();
            present.add(key);
            int left = effect.getDuration();
            if (left > limit + 20) {
                this.longSeen.add(key);
                this.warned.remove(key);
            } else if (left > 0 && left <= limit && this.longSeen.contains(key) && this.warned.add(key)) {
                String name = effect.getEffect().value().getDisplayName().getString();
                push(Kind.WARNING, "Эффект скоро закончится", name + " — осталось " + Math.max(1, left / 20) + " с");
            }
        }
        this.longSeen.retainAll(present);
        this.warned.retainAll(present);
    }

    // ------------------------------------------------------------------------------------------------ drawing

    /** HUD element: called every frame. */
    public void render(GuiGraphicsExtractor gg, DeltaTracker delta) {
        if (!isEnabled() || this.toasts.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long life = (long) (this.duration.value() * 1000.0);
        this.toasts.removeIf(toast -> now - toast.created > life + FADE_OUT_MS);
        if (this.toasts.isEmpty()) {
            return;
        }
        float s = Render2D.screenHeight() / 1080.0f * (float) this.scale.value() / 100.0f * 0.5f;
        float width = DESIGN_W * s;
        float height = DESIGN_H * s;
        float gap = GAP * s;
        float margin = 16.0f * s * 2.0f;
        int screenW = Render2D.screenWidth();
        int screenH = Render2D.screenHeight();
        boolean bottom = this.position.is("Снизу справа");
        boolean center = this.position.is("Сверху по центру");

        Render2D.begin(gg);
        try {
            for (int index = 0; index < this.toasts.size(); index++) {
                Toast toast = this.toasts.get(index);
                toast.slot += (index - toast.slot) * 0.18f;
                long age = now - toast.created;
                float in = Mth.clamp(age / (float) FADE_IN_MS, 0.0f, 1.0f);
                float out = age > life ? 1.0f - Mth.clamp((age - life) / (float) FADE_OUT_MS, 0.0f, 1.0f) : 1.0f;
                float alpha = easeOut(in) * out;
                float slide = (1.0f - easeOut(in)) * 28.0f * s * (bottom ? -1.0f : 1.0f);
                float stack = toast.slot * (height + gap);
                float x = center ? (screenW - width) / 2.0f : screenW - width - margin;
                float y = bottom ? screenH - margin - height - stack - slide
                        : margin + stack - slide;
                Render2D.pushAlpha(alpha);
                drawToast(gg, toast, x, y, s, easeOut(Mth.clamp(age / 320.0f, 0.0f, 1.0f)));
                Render2D.popAlpha();
            }
        } finally {
            Render2D.end(gg);
        }
    }

    private static float easeOut(float t) {
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private void drawToast(GuiGraphicsExtractor gg, Toast toast, float x, float y, float s, float progress) {
        Render2D.round(gg, x, y, DESIGN_W * s, DESIGN_H * s, 36.0f * s, BACKGROUND);
        if (toast.kind == Kind.WARNING) {
            drawWarning(gg, toast, x, y, s);
            return;
        }
        boolean on = toast.kind == Kind.ON;
        // the switch slides to its new position
        float k = on ? progress : 1.0f - progress;
        Render2D.round(gg, x + 16.0f * s, y + 16.0f * s, 72.0f * s, 40.0f * s, 20.0f * s,
                Render2D.lerp(TRACK_OFF, TRACK_ON, k));
        float knobX = x + (20.0f + 32.0f * k) * s;
        Render2D.circle(gg, knobX, y + 20.0f * s, 32.0f * s, Render2D.lerp(KNOB_OFF, 0xFFFFFFFF, k));

        HFont title = Fonts.of(Fonts.INTER_BOLD, Math.max(8, Math.round(20.0f * s)));
        HFont state = Fonts.of(Fonts.INTER_SEMIBOLD, Math.max(8, Math.round(15.0f * s)));
        String stateText = on ? "Включено" : "Выключено";
        float stateWidth = state.width(stateText);
        float maxTitle = (428.0f - 106.0f) * s - stateWidth - 14.0f * s;
        title.draw(gg, title.trim(toast.title, maxTitle), x + 106.0f * s, y + 43.0f * s, 0xFFFFFFFF);
        state.drawRight(gg, stateText, x + 428.0f * s, y + 40.5f * s, on ? TEXT_ON : TEXT_OFF);
    }

    private void drawWarning(GuiGraphicsExtractor gg, Toast toast, float x, float y, float s) {
        Render2D.circle(gg, x + 16.0f * s, y + 16.0f * s, 40.0f * s, WARN_FILL);
        Render2D.round(gg, x + 33.5f * s, y + 25.0f * s, 5.0f * s, 15.0f * s, 2.5f * s, WARN);
        Render2D.circle(gg, x + 33.2f * s, y + 42.7f * s, 5.6f * s, WARN);

        HFont title = Fonts.of(Fonts.INTER_BOLD, Math.max(8, Math.round(18.0f * s)));
        HFont text = Fonts.of(Fonts.INTER_MEDIUM, Math.max(8, Math.round(14.0f * s)));
        float maxWidth = (DESIGN_W - 72.0f - 28.0f) * s;
        title.draw(gg, title.trim(toast.title, maxWidth), x + 72.0f * s, y + 29.5f * s, 0xFFFFFFFF);
        text.draw(gg, text.trim(toast.subtitle, maxWidth), x + 72.0f * s, y + 52.5f * s, SUBTITLE);
    }

}
