package dev.hatek.client.module.impl.combat.aura.rotation;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class ServerView extends Component {
    private static float yaw;
    private static float pitch;
    private static Vec3 position;
    private static boolean lookKnown;

    private static final double STALE_DISTANCE = 3.0;

    // what the server was last told about sprinting (a duplicate start/stop is a "bad packet" for the anticheat)
    private static boolean sprintSent;
    private static Player sprintOwner;

    public void onSend(Packet<?> packet) {
        if (packet instanceof ServerboundMovePlayerPacket move) {
            if (move.hasRotation()) {
                yaw = move.getYRot(yaw);
                pitch = move.getXRot(pitch);
                lookKnown = true;
            }
            if (move.hasPosition()) {
                position = new Vec3(move.getX(0.0), move.getY(0.0), move.getZ(0.0));
            }
        } else if (packet instanceof ServerboundPlayerCommandPacket command) {
            switch (command.getAction()) {
                case START_SPRINTING -> sprintSent = true;
                case STOP_SPRINTING -> sprintSent = false;
                default -> {
                }
            }
        } else if (packet instanceof ServerboundUseItemPacket use) {
            yaw = use.getYRot();
            pitch = use.getXRot();
            lookKnown = true;
        }
    }

    /** True while the server believes the player is sprinting (by the packets that were really sent). */
    public static boolean serverSprinting(Player player) {
        if (player != sprintOwner) {
            // a new player entity (join, respawn): the server starts with "not sprinting"
            sprintOwner = player;
            sprintSent = false;
        }
        return sprintSent;
    }

    public static float yaw(float fallback) {
        return lookKnown ? yaw : fallback;
    }

    public static float pitch(float fallback) {
        return lookKnown ? pitch : fallback;
    }

    public static Vec3 eyePos(Player player) {
        if (player == null) {
            return Vec3.ZERO;
        }
        Vec3 eyes = player.getEyePosition();
        if (position == null) {
            return eyes;
        }
        Vec3 serverEyes = position.add(0.0, player.getEyeHeight(player.getPose()), 0.0);
        return serverEyes.distanceTo(eyes) > STALE_DISTANCE ? eyes : serverEyes;
    }

    public static void reset() {
        lookKnown = false;
        position = null;
    }
}
