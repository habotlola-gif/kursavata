package dev.hatek.client.module.impl.movement;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.Vec3;

/**
 * AirStuck: hangs the player in the air. While frozen the player does not move and no movement packets
 * are sent. Limited mode releases after a set time, Unlimited holds until the module is turned off.
 * The server's last known state is "falling with motion v", so on release the fall continues with the
 * saved motion (Continue) or from zero (From zero, as in other clients).
 */
public final class AirStuck extends Module {
    private static AirStuck instance;

    private final ModeSetting mode = new ModeSetting("Mode", 0, "Limited", "Unlimited");
    private final SliderSetting seconds = new SliderSetting("Time (s)", 12.0, 0.5, 20.0, 0.5);
    private final ModeSetting exit = new ModeSetting("After exit", 0, "Continue fall", "From zero");

    private boolean frozen;
    private boolean released;
    private int stuckTicks;
    private Vec3 savedPos = Vec3.ZERO;
    private Vec3 savedMotion = Vec3.ZERO;
    private double savedFall;

    public AirStuck() {
        super("AirStuck", "Freezes you in the air: timed, or until you turn it off", Category.MOVEMENT);
        instance = this;
        with(this.mode, this.seconds, this.exit);
    }

    public static AirStuck instance() {
        return instance;
    }

    /** Called from ConnectionMixin before a packet is sent; true cancels it. */
    public static boolean blockPacket(Packet<?> packet) {
        AirStuck self = instance;
        if (self == null || !self.frozen) {
            return false;
        }
        if (packet instanceof ServerboundMovePlayerPacket) {
            return true;
        }
        if (packet instanceof ServerboundUseItemOnPacket use) {
            Minecraft mc = Minecraft.getInstance();
            // while hanging only blocks may be placed, other item uses on blocks are dropped
            return mc.player != null && !(mc.player.getItemInHand(use.getHand()).getItem() instanceof BlockItem);
        }
        return false;
    }

    private static boolean unavailable(LocalPlayer player) {
        return player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable()
                || player.isPassenger() || player.getAbilities().flying || player.isFallFlying() || !player.isAlive();
    }

    private Vec3 exitMotion() {
        return this.exit.is("Continue fall") ? this.savedMotion : Vec3.ZERO;
    }

    /** Start of LocalPlayer.tick. */
    public void beforeTick(LocalPlayer player) {
        if (!isEnabled() || unavailable(player)) {
            finish(player);
            return;
        }
        if (this.released) {
            return;
        }
        // start only while falling: the server judges "floating" by the last movement packet
        if (!this.frozen && player.getDeltaMovement().y > -0.05) {
            return;
        }
        if (!this.frozen) {
            this.frozen = true;
            this.savedPos = player.position();
            this.savedMotion = player.getDeltaMovement();
            this.savedFall = player.fallDistance;
            this.stuckTicks = 0;
        }
    }

    /** End of LocalPlayer.tick. */
    public void afterTick(LocalPlayer player) {
        if (!this.frozen) {
            return;
        }
        player.setPos(this.savedPos.x, this.savedPos.y, this.savedPos.z);
        player.xo = this.savedPos.x;
        player.yo = this.savedPos.y;
        player.zo = this.savedPos.z;
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = this.savedFall;
        if (this.mode.is("Limited")
                && ++this.stuckTicks >= Math.max(1, Math.round(this.seconds.value() * 20.0))) {
            player.setDeltaMovement(exitMotion());
            this.frozen = false;
            this.released = true;
        }
    }

    private void finish(LocalPlayer player) {
        if (this.frozen) {
            player.setDeltaMovement(exitMotion());
        }
        this.frozen = false;
        this.released = false;
        this.stuckTicks = 0;
    }

    @Override
    protected void onEnable() {
        this.released = false;
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            finish(mc.player);
        }
    }
}
