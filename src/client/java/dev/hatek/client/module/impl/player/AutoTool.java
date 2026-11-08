package dev.hatek.client.module.impl.player;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * AutoTool: while you hold the attack button on a block, the hotbar slot with the fastest tool for that block is
 * selected; when you let go (or look away) the slot you had comes back.
 */
public final class AutoTool extends Module {
    private final BoolSetting switchBack = new BoolSetting("Вернуть прошлый слот", true);
    private final BoolSetting onlyBetter = new BoolSetting("Менять только если лучше текущего", true);
    private final BoolSetting skipWhenUsing = new BoolSetting("Не менять, пока ем / блокирую", true);

    private int previousSlot = -1;

    public AutoTool() {
        super("AutoTool", "Picks the fastest tool in the hotbar for the block you are breaking", Category.PLAYER);
        with(this.switchBack, this.onlyBetter, this.skipWhenUsing);
    }

    @Override
    protected void onDisable() {
        restore();
    }

    private void restore() {
        Minecraft mc = Minecraft.getInstance();
        if (this.previousSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(this.previousSlot);
        }
        this.previousSlot = -1;
    }

    @Override
    public void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) {
            this.previousSlot = -1;
            return;
        }
        boolean breaking = mc.options.keyAttack.isDown() && mc.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK;
        if (!breaking) {
            if (this.switchBack.value()) {
                restore();
            } else {
                this.previousSlot = -1;
            }
            return;
        }
        if (this.skipWhenUsing.value() && player.isUsingItem()) {
            return;
        }
        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        if (state.isAir()) {
            return;
        }
        int current = player.getInventory().getSelectedSlot();
        float currentSpeed = speed(player.getInventory().getItem(current), state);
        int best = current;
        float bestSpeed = currentSpeed;
        for (int slot = 0; slot < 9; slot++) {
            float speed = speed(player.getInventory().getItem(slot), state);
            if (speed > bestSpeed + 1.0E-3F) {
                bestSpeed = speed;
                best = slot;
            }
        }
        if (best != current && (!this.onlyBetter.value() || bestSpeed > currentSpeed)) {
            if (this.previousSlot < 0) {
                this.previousSlot = current;
            }
            player.getInventory().setSelectedSlot(best);
        }
    }

    private static float speed(ItemStack stack, BlockState state) {
        float speed = stack.getDestroySpeed(state);
        // a tool that also drops the block beats a faster one that does not
        if (state.requiresCorrectToolForDrops() && stack.isCorrectToolForDrops(state)) {
            speed += 1000.0F;
        }
        return speed;
    }
}
