package dev.hatek.client.module.impl.combat.aura.rotation;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class ServerView extends Component {
    private static float yaw;
    private static float pitch;
    private static Vec3 position;
    private static boolean lookKnown;

    private static final double STALE_DISTANCE = 3.0;

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
        } else if (packet instanceof ServerboundUseItemPacket use) {
            yaw = use.getYRot();
            pitch = use.getXRot();
            lookKnown = true;
        }
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
