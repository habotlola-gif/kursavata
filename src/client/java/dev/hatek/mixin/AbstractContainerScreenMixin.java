package dev.hatek.mixin;

import dev.hatek.client.module.impl.player.ItemScroller;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow
    protected Slot hoveredSlot;

    @Shadow
    protected abstract void slotClicked(Slot slot, int slotId, int buttonNum, ContainerInput type);

    /** A new click starts a new stroke; the slot a plain shift-click hits is already handled by the game. */
    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void hatek$strokeStart(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ItemScroller.active()) {
            ItemScroller.resetStroke();
            if (event.button() == 0 && event.hasShiftDown()) {
                ItemScroller.markVisited(this.hoveredSlot);
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void hatek$strokeEnd(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (ItemScroller.active()) {
            ItemScroller.resetStroke();
        }
    }

    /** Shift + left button dragged over slots: every stack passed is moved with a shift-click. */
    @Inject(method = "mouseDragged", at = @At("HEAD"))
    private void hatek$drag(MouseButtonEvent event, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
        if (ItemScroller.active() && event.button() == 0 && event.hasShiftDown()
                && ItemScroller.shouldMove(this.hoveredSlot)) {
            this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, 0, ContainerInput.QUICK_MOVE);
        }
    }

    /** The wheel over a slot moves its stack (if the option is on). */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void hatek$wheel(double mouseX, double mouseY, double scrollX, double scrollY,
                             CallbackInfoReturnable<Boolean> cir) {
        if (ItemScroller.wheelEnabled() && this.hoveredSlot != null && this.hoveredSlot.hasItem() && scrollY != 0.0) {
            this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, 0, ContainerInput.QUICK_MOVE);
            cir.setReturnValue(true);
        }
    }
}
