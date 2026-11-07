package dev.hatek.mixin;

import dev.hatek.client.module.impl.render.ViewModel;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    /** The mouse wheel moves the hands toward/away from the camera (unless the mouse is over the chat messages). */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void hatek$handWheel(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        ViewModel viewModel = ViewModel.instance();
        if (viewModel != null && viewModel.onScroll(mouseX, mouseY, scrollY)) {
            cir.setReturnValue(true);
        }
    }
}
