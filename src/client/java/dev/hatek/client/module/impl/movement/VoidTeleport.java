package dev.hatek.client.module.impl.movement;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * VoidTeleport: turn the module on and it teleports you to the X / Y / Z set in its settings, then switches itself off.
 * The route goes down under the bedrock, along the void floor (chunks must be loaded there) and back up above the
 * target. Every hop is a separate movement packet with a client-tick-end packet behind it; the real movement packets
 * are held back while the trip runs. Needs flight ({@code /fly}) because the player hangs in the air while the packets
 * go out. A position correction from the server cancels the trip.
 */
public final class VoidTeleport extends Module {
    private static final double STEP = 9.5D;
    private static final int MAX_PACKETS = 20;
    private static final double MAX_CLIP = STEP * MAX_PACKETS;
    private static final int PAUSE_TICKS = 7;

    private enum Stage { IDLE, DESCEND, TRAVEL, ASCEND }

    private static VoidTeleport instance;
    private static boolean silent;

    private final TextSetting xText = new TextSetting("X", "", "empty = where you stand");
    private final TextSetting yText = new TextSetting("Y", "", "empty = where you stand");
    private final TextSetting zText = new TextSetting("Z", "", "empty = where you stand");

    private Vec3 pending;
    private volatile Stage stage = Stage.IDLE;
    private volatile boolean rolledBack;
    private Vec3 position = Vec3.ZERO;
    private Vec3 target = Vec3.ZERO;
    private double voidY;
    private int chargeTicks;
    private int pauseTicks;

    public VoidTeleport() {
        super("VoidTeleport", "Teleport to X / Y / Z through the void under the bedrock, then turn off", Category.MOVEMENT);
        instance = this;
        with(this.xText, this.yText, this.zText);
    }

    public static VoidTeleport instance() {
        return instance;
    }

    /** Called from ConnectionMixin before a packet is sent; true cancels it (the trip sends its own packets silently). */
    public static boolean blockPacket(Packet<?> packet) {
        VoidTeleport self = instance;
        if (self == null || silent || self.stage == Stage.IDLE) {
            return false;
        }
        return packet instanceof ServerboundMovePlayerPacket || packet instanceof ServerboundClientTickEndPacket;
    }

    /** Called when the server sets the player's position (a correction ends the trip). */
    public static void onServerPosition() {
        VoidTeleport self = instance;
        if (self != null && self.stage != Stage.IDLE) {
            self.stage = Stage.IDLE;
            self.pauseTicks = 0;
            self.rolledBack = true;
        }
    }

    private static void message(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§bVoidTeleport: §f" + text));
        }
    }

    private static double parse(String text, double fallback) {
        try {
            String clean = text.trim().replace(',', '.');
            return clean.isEmpty() ? fallback : Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Starts a trip to the given point (used by the .tp command). */
    public void teleportTo(Vec3 point) {
        if (this.stage != Stage.IDLE) {
            message("Телепорт уже идёт.");
            return;
        }
        this.pending = point;
        if (isEnabled()) {
            onEnable();
        } else {
            setEnabled(true);
        }
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            setEnabled(false);
            return;
        }
        Vec3 point = this.pending != null ? this.pending
                : new Vec3(parse(this.xText.value(), player.getX()), parse(this.yText.value(), player.getY()),
                parse(this.zText.value(), player.getZ()));
        this.pending = null;
        if (!start(player, level, point.x, point.y, point.z)) {
            setEnabled(false);
        }
    }

    @Override
    protected void onDisable() {
        this.stage = Stage.IDLE;
        this.pauseTicks = 0;
    }

    private boolean start(LocalPlayer player, ClientLevel level, double x, double y, double z) {
        if (!player.getAbilities().mayfly) {
            message("Нужен /fly.");
            return false;
        }
        double underBedrock = level.getMinY() - player.getDimensions(Pose.STANDING).height() - 0.05D;
        if (player.getY() - underBedrock > MAX_CLIP || y - underBedrock > MAX_CLIP) {
            message(String.format(Locale.ROOT, "Слишком высоко: клип под бедрок работает до Y=%.0f.", underBedrock + MAX_CLIP));
            return false;
        }
        if (!player.getAbilities().flying) {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
        this.position = player.position();
        this.target = new Vec3(x, y, z);
        this.voidY = underBedrock;
        this.rolledBack = false;
        this.chargeTicks = packetsFor(this.position.y - this.voidY) + 2;
        this.pauseTicks = 0;
        this.stage = Stage.DESCEND;
        return true;
    }

    @Override
    public void onClientTick() {
        if (this.rolledBack) {
            this.rolledBack = false;
            message("Сервер откатил телепорт.");
            setEnabled(false);
            return;
        }
        if (this.stage == Stage.IDLE) {
            setEnabled(false);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            this.stage = Stage.IDLE;
            this.pauseTicks = 0;
            setEnabled(false);
            return;
        }
        if (this.pauseTicks > 0) {
            this.pauseTicks--;
            hold(player);
            return;
        }
        switch (this.stage) {
            case DESCEND -> clip(new Vec3(this.position.x, this.voidY, this.position.z), Stage.TRAVEL);
            case TRAVEL -> travel(level);
            case ASCEND -> ascend(player, level);
            default -> {
            }
        }
        hold(player);
        if (this.stage == Stage.IDLE) {
            setEnabled(false);
        }
    }

    private void hold(LocalPlayer player) {
        player.setPos(this.position);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
    }

    private void travel(ClientLevel level) {
        double dx = this.target.x - this.position.x;
        double dz = this.target.z - this.position.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0E-3D) {
            this.stage = Stage.ASCEND;
            this.chargeTicks = -1;
            this.pauseTicks = PAUSE_TICKS;
            return;
        }
        double scale = Math.min(STEP, distance) / distance;
        Vec3 next = new Vec3(this.position.x + dx * scale, this.voidY, this.position.z + dz * scale);
        if (!level.hasChunk(SectionPos.blockToSectionCoord(next.x), SectionPos.blockToSectionCoord(next.z))) {
            return;   // wait for the chunk to load
        }
        send(next);
        this.position = next;
    }

    private void ascend(LocalPlayer player, ClientLevel level) {
        if (this.chargeTicks < 0) {
            Double freeY = findFreeY(player, level);
            if (freeY == null) {
                this.stage = Stage.IDLE;
                message("Над целью нет свободного места, укажите другую Y.");
                return;
            }
            this.target = new Vec3(this.target.x, freeY, this.target.z);
            this.chargeTicks = packetsFor(freeY - this.voidY) + 2;
        }
        clip(this.target, Stage.IDLE);
        if (this.stage == Stage.IDLE) {
            message(String.format(Locale.ROOT, "Телепортирован на X=%.2f, Y=%.2f, Z=%.2f.",
                    this.target.x, this.target.y, this.target.z));
        }
    }

    private void clip(Vec3 destination, Stage next) {
        if (this.chargeTicks-- > 0) {
            return;
        }
        int packets = packetsFor(Math.abs(destination.y - this.position.y));
        for (int i = 1; i < packets; i++) {
            send(this.position);
        }
        send(destination);
        this.position = destination;
        this.stage = next;
        if (next != Stage.IDLE) {
            this.pauseTicks = PAUSE_TICKS;
        }
    }

    private Double findFreeY(LocalPlayer player, ClientLevel level) {
        for (double y = this.target.y; y <= this.voidY + MAX_CLIP; y = Math.floor(y) + 1.0D) {
            Vec3 candidate = new Vec3(this.target.x, y, this.target.z);
            if (level.noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(candidate))) {
                return y;
            }
        }
        return null;
    }

    private static int packetsFor(double distance) {
        return Math.max(1, (int) Math.ceil(distance / STEP));
    }

    private static void send(Vec3 pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return;
        }
        silent = true;
        try {
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y, pos.z, false, false));
            mc.getConnection().send(ServerboundClientTickEndPacket.INSTANCE);
        } finally {
            silent = false;
        }
    }
}
