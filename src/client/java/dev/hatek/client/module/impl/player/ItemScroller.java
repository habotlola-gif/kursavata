package dev.hatek.client.module.impl.player;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.world.inventory.Slot;

import java.util.HashSet;
import java.util.Set;

/**
 * ItemScroller: in inventories and chests, hold Shift and drag the left mouse button over slots to move every stack you
 * pass (like shift-clicking each one, but in one stroke). Optionally the mouse wheel over a slot moves its stack.
 * The actual work happens in AbstractContainerScreenMixin, which asks this module what to do.
 */
public final class ItemScroller extends Module {
    private static ItemScroller instance;

    private final SliderSetting delay = new SliderSetting("Задержка между слотами (мс)", 25.0, 0.0, 200.0, 5.0);
    private final BoolSetting wheel = new BoolSetting("Колесо над слотом переносит стак", false);

    private final Set<Integer> visited = new HashSet<>();
    private long lastMove;

    public ItemScroller() {
        super("ItemScroller", "Shift + drag over slots moves items quickly", Category.PLAYER);
        instance = this;
        with(this.delay, this.wheel);
    }

    public static ItemScroller instance() {
        return instance;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    public static boolean wheelEnabled() {
        return active() && instance.wheel.value();
    }

    /** A new stroke starts (or ends): forget which slots were already moved. */
    public static void resetStroke() {
        if (instance != null) {
            instance.visited.clear();
        }
    }

    /** Marks the slot under a plain shift-click as done so the drag does not move it twice. */
    public static void markVisited(Slot slot) {
        if (instance != null && slot != null) {
            instance.visited.add(slot.index);
        }
    }

    /** True when this slot should be moved now (once per stroke, with the configured pause between slots). */
    public static boolean shouldMove(Slot slot) {
        ItemScroller self = instance;
        if (self == null || slot == null || !slot.hasItem() || self.visited.contains(slot.index)) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - self.lastMove < self.delay.value()) {
            return false;
        }
        self.lastMove = now;
        self.visited.add(slot.index);
        return true;
    }
}
