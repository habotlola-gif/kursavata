package dev.hatek.client.feature.command;

import dev.hatek.client.module.impl.movement.VoidTeleport;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Chat commands that start with a dot. They are handled on the client and never sent to the server.
 *
 *   .tp ник         — VoidTeleport to a player that is loaded around you
 *   .tp x y z       — VoidTeleport to coordinates
 */
public final class Commands {
    private Commands() {
    }

    private static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§bHatek: §f" + text));
        }
    }

    /** True when the line was a client command (the chat must not send it). */
    public static boolean handle(String line) {
        String text = line.trim();
        if (!text.startsWith(".")) {
            return false;
        }
        String[] parts = text.substring(1).split("\\s+");
        String name = parts[0].toLowerCase(Locale.ROOT);
        if (name.equals("tp")) {
            teleport(parts);
            return true;
        }
        return false;
    }

    private static void teleport(String[] parts) {
        Minecraft mc = Minecraft.getInstance();
        VoidTeleport module = VoidTeleport.instance();
        if (mc.player == null || mc.level == null || module == null) {
            return;
        }
        if (parts.length == 2) {
            String nick = parts[1];
            Player found = null;
            for (Player other : mc.level.players()) {
                if (other != mc.player && other.getGameProfile().name().equalsIgnoreCase(nick)) {
                    found = other;
                    break;
                }
            }
            if (found == null) {
                say("Игрок " + nick + " не найден рядом (его должно быть видно в мире).");
                return;
            }
            say("Телепорт к " + found.getName().getString() + "...");
            module.teleportTo(found.position());
        } else if (parts.length == 4) {
            try {
                Vec3 point = new Vec3(Double.parseDouble(parts[1].replace(',', '.')),
                        Double.parseDouble(parts[2].replace(',', '.')), Double.parseDouble(parts[3].replace(',', '.')));
                module.teleportTo(point);
            } catch (NumberFormatException e) {
                say("Координаты должны быть числами: .tp x y z");
            }
        } else {
            say("Использование: .tp ник  или  .tp x y z");
        }
    }
}
