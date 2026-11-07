package dev.hatek.client.module.impl.combat.aura.util;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hatek.client.module.impl.combat.AttackAura;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.HashSet;
import java.util.Set;

public final class MovementManager {
    private static final MovementManager INSTANCE = new MovementManager();

    public final Set<String> lockRequests = new HashSet<>();

    private MovementManager() {
    }

    public static MovementManager getInstance() {
        return INSTANCE;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public void lockMovement(String moduleName) {
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) {
            return;
        }
        AttackAura.canSwap = true;
        this.lockRequests.add(moduleName);
        setMovementKeys(false);
    }

    public void unlockMovement(String moduleName) {
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) {
            return;
        }
        this.lockRequests.remove(moduleName);
        if (this.lockRequests.isEmpty() && mc.gui.screen() == null) {
            setMovementKeys(true);
            AttackAura.canSwap = false;
        }
    }

    public boolean isMovementLocked() {
        return !this.lockRequests.isEmpty();
    }

    private void setMovementKeys(boolean state) {
        Minecraft mc = mc();
        KeyMapping[] movementKeys = {
                mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft,
                mc.options.keyRight, mc.options.keyJump, mc.options.keySprint
        };
        for (KeyMapping key : movementKeys) {
            boolean pressed = state
                    && InputConstants.isKeyDown(mc.getWindow(), key.getDefaultKey().getValue());
            key.setDown(pressed);
        }
    }
}
