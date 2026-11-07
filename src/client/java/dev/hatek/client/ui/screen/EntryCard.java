package dev.hatek.client.ui.screen;

import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class EntryCard {
    private EntryCard() {
    }

    static void header(GuiGraphicsExtractor gg, float x, float y, String author, String date,
                       String title, int avatarTint) {
        Render2D.circle(gg, x + Style.AVATAR_X, y + Style.AVATAR_Y, Style.AVATAR,
                Render2D.withAlpha(avatarTint, 0.22f));
        String initial = author == null || author.isEmpty()
                ? "?" : author.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        Fonts.label().drawCentered(gg, initial,
                x + Style.AVATAR_X + Style.AVATAR / 2.0f,
                y + Style.AVATAR_Y + Style.AVATAR / 2.0f + 4.0f, avatarTint);

        Fonts.label().draw(gg, author, x + Style.ENTRY_AUTHOR_X,
                y + Style.ENTRY_META_BASELINE, Style.WHITE_25);
        Fonts.label().drawRight(gg, date, x + Style.ENTRY_DATE_RIGHT,
                y + Style.ENTRY_META_BASELINE, Style.WHITE_25);

        Fonts.title().draw(gg, Fonts.title().trim(title, 190.0f), x + Style.ENTRY_NAME_X,
                y + Style.ENTRY_NAME_BASELINE, Style.WHITE);
    }
}
