package dev.hatek.client.module.impl.combat.aura.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.net.SocketAddress;
import java.util.Locale;

public final class AuraPlayerUtil {
    private AuraPlayerUtil() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static boolean nullCheck() {
        return mc().player == null || mc().level == null;
    }

    public static float calculateCorrectYawOffset(float yaw) {
        Minecraft mc = mc();
        double xDiff = mc.player.getX() - mc.player.xOld;
        double zDiff = mc.player.getZ() - mc.player.zOld;
        float distSquared = (float) (xDiff * xDiff + zDiff * zDiff);
        float renderYawOffset = mc.player.yBodyRotO;
        float offset = renderYawOffset;

        if (distSquared > 0.0025000002F) {
            offset = (float) Mth.atan2(zDiff, xDiff) * 180.0F / (float) Math.PI - 90.0F;
        }
        if (mc.player.attackAnim > 0.0F) {
            offset = yaw;
        }

        float yawOffsetDiff = Mth.wrapDegrees(
                yaw - (renderYawOffset + Mth.wrapDegrees(offset - renderYawOffset) * 0.3F));
        yawOffsetDiff = Mth.clamp(yawOffsetDiff, -32.0F, 32.0F);
        renderYawOffset = yaw - yawOffsetDiff;

        if (yawOffsetDiff * yawOffsetDiff > 2500.0F) {
            renderYawOffset += yawOffsetDiff * 0.2F;
        }
        return renderYawOffset;
    }

    public static boolean isServerContains(String searchString) {
        if (searchString == null || searchString.isEmpty()) {
            return false;
        }
        Minecraft mc = mc();
        if (nullCheck() || mc.getConnection() == null) {
            return false;
        }

        String serverAddress = null;
        if (mc.getCurrentServer() != null) {
            serverAddress = mc.getCurrentServer().ip;
        }
        if ((serverAddress == null || serverAddress.isEmpty())
                && mc.getConnection().getConnection() != null) {
            SocketAddress address = mc.getConnection().getConnection().getRemoteAddress();
            if (address != null) {
                serverAddress = address.toString();
                if (serverAddress.startsWith("/")) {
                    serverAddress = serverAddress.substring(1);
                }
            }
        }
        if (mc.hasSingleplayerServer()) {
            serverAddress = "localhost";
        }
        return serverAddress != null && !serverAddress.isEmpty()
                && serverAddress.toLowerCase(Locale.ROOT).contains(searchString.toLowerCase(Locale.ROOT));
    }
}
