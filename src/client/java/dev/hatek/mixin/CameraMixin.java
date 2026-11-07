package dev.hatek.mixin;

import dev.hatek.client.event.ClientEvents;
import dev.hatek.client.module.impl.combat.aura.rotation.ComponentManager;
import net.minecraft.client.Camera;
import dev.hatek.client.module.impl.render.Freecam;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    private boolean detached;

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void hatek$freecam(float partial, CallbackInfo ci) {
        if (Freecam.active()) {
            this.setRotation(Freecam.yaw(), Freecam.pitch());
            this.setPosition(Freecam.position(partial));
            if (Freecam.showBody()) {
                this.detached = true;
            }
        }
    }

    @Redirect(
            method = "alignWithEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setRotation(FF)V"
            )
    )
    private void hatek$redirectSetRotation(Camera instance, float yaw, float pitch) {
        float[] angles = ComponentManager.rotation(yaw, pitch);
        this.setRotation(angles[0], angles[1]);
    }

    @Inject(method = "update", at = @At("HEAD"))
    private void hatek$onFrame(DeltaTracker delta, CallbackInfo ci) {
        ClientEvents.render3D();
    }
}
