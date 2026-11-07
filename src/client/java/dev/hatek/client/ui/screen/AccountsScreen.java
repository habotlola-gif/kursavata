package dev.hatek.client.ui.screen;

import dev.hatek.client.feature.account.Account;
import dev.hatek.client.feature.account.AccountManager;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Anim;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AccountsScreen extends Screen {
    private static final int PANEL_W = 420;
    private static final int PANEL_H = 404;
    private static final int PAD = 18;

    private static final int HEAD_H = 56;
    private static final int FIELD_H = 32;
    private static final int FIELD_R = 9;
    private static final int ADD_W = 74;

    private static final int ROW_H = 40;
    private static final int ROW_GAP = 7;
    private static final int ROW_R = 11;

    private final Screen parent;

    private String draft = "";
    private boolean typing;
    private String notice = "";
    private long noticeUntil;

    private float scroll;
    private final Anim openAnim = new Anim(0.0f, 14.0f);
    private final Anim scrollAnim = new Anim(0.0f, 17.0f);
    private final Anim fieldAnim = new Anim(0.0f, 16.0f);
    private final Anim addAnim = new Anim(0.0f, 16.0f);
    private final Map<String, Anim> rowHover = new HashMap<>();
    private final Map<String, Anim> trashHover = new HashMap<>();

    private long lastFrame = System.nanoTime();

    public AccountsScreen(Screen parent) {
        super(Component.literal("Accounts"));
        this.parent = parent;
    }

    private float panelX() {
        return Math.round((Render2D.screenWidth() - PANEL_W) / 2.0f);
    }

    private float panelY() {
        return Math.round((Render2D.screenHeight() - PANEL_H) / 2.0f);
    }

    private Anim hover(Map<String, Anim> map, String key) {
        return map.computeIfAbsent(key, ignored -> new Anim(0.0f, 18.0f));
    }

    @Override
    protected void init() {
        AccountManager.all();
        this.openAnim.snap(0.0f);
        this.openAnim.to(1.0f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gg, int mouseX, int mouseY,
                                   float partialTick) {
        super.extractRenderState(gg, mouseX, mouseY, partialTick);

        long now = System.nanoTime();
        Anim.beginFrame((now - this.lastFrame) / 1_000_000_000.0f);
        this.lastFrame = now;

        float eased = Anim.easeOut(this.openAnim.get());
        float x = panelX();
        float y = panelY();
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();

        Render2D.begin(gg);
        Render2D.pushScale(gg, x + PANEL_W / 2.0f, y + PANEL_H / 2.0f, 0.96f + 0.04f * eased);
        Render2D.pushTranslate(gg, 0.0f, (1.0f - eased) * 12.0f);
        Render2D.pushAlpha(eased);

        Render2D.round(gg, x, y, PANEL_W, PANEL_H, Style.PANEL_R, Style.PANEL);

        renderHead(gg, x, y);
        renderAddRow(gg, x, y, mx, my);
        renderList(gg, x, y, mx, my);

        Render2D.popAlpha();
        Render2D.popTransform(gg);
        Render2D.popTransform(gg);
        Render2D.end(gg);
    }

    private void renderHead(GuiGraphicsExtractor gg, float x, float y) {
        HFont title = Fonts.title();
        title.draw(gg, "Accounts", x + PAD, y + 30.0f, Style.WHITE);

        String right = AccountManager.canSwitch()
                ? "Playing as " + AccountManager.activeName()
                : "In game - leave the server to switch";
        Fonts.body().drawRight(gg, right, x + PANEL_W - PAD, y + 29.0f, Style.WHITE_25);
    }

    private void renderAddRow(GuiGraphicsExtractor gg, float x, float y, double mx, double my) {
        float fx = x + PAD;
        float fy = y + HEAD_H;
        float fw = PANEL_W - PAD * 2 - ADD_W - 8;

        boolean fieldHover = Render2D.hovered(mx, my, fx, fy, fw, FIELD_H);
        this.fieldAnim.to(this.typing || fieldHover);
        float k = this.fieldAnim.get();
        Render2D.round(gg, fx, fy, fw, FIELD_H, FIELD_R,
                Render2D.lerp(Style.WHITE_04, Style.white(0.085f), k));
        if (this.typing) {
            Render2D.round(gg, fx, fy + FIELD_H - 2.0f, fw, 2.0f, 1.0f,
                    Render2D.withAlpha(Style.accent(), 0.85f));
        }

        HFont body = Fonts.body();
        boolean empty = this.draft.isEmpty();
        String shown = empty && !this.typing ? "Nickname" : this.draft;
        float baseline = body.centeredBaseline(fy, FIELD_H);
        body.draw(gg, shown, fx + 12.0f, baseline, empty ? Style.WHITE_25 : Style.WHITE);

        if (this.typing && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float caret = fx + 12.0f + body.width(this.draft) + 1.0f;
            Render2D.rect(gg, caret, fy + 8.0f, 1.0f, FIELD_H - 16.0f, Style.WHITE_45);
        }

        float ax = x + PANEL_W - PAD - ADD_W;
        boolean canAdd = Account.valid(this.draft) && !AccountManager.has(this.draft);
        boolean addHover = Render2D.hovered(mx, my, ax, fy, ADD_W, FIELD_H);
        this.addAnim.to(addHover && canAdd);
        float a = this.addAnim.get();
        int fill = canAdd
                ? Render2D.lerp(Render2D.withAlpha(Style.accent(), 0.85f), Style.accent(), a)
                : Style.WHITE_04;
        Render2D.round(gg, ax, fy, ADD_W, FIELD_H, FIELD_R, fill);
        Fonts.label().drawCentered(gg, "Add", ax + ADD_W / 2.0f,
                Fonts.label().centeredBaseline(fy, FIELD_H),
                canAdd ? Style.PANEL : Style.WHITE_25);

        if (!this.notice.isEmpty() && System.currentTimeMillis() < this.noticeUntil) {
            body.draw(gg, this.notice, fx, fy + FIELD_H + 15.0f,
                    Render2D.withAlpha(Style.accent(), 0.9f));
        }
    }

    private float listTop(float y) {
        return y + HEAD_H + FIELD_H + 20.0f;
    }

    private float listHeight(float y) {
        return y + PANEL_H - PAD - listTop(y);
    }

    private void renderList(GuiGraphicsExtractor gg, float x, float y, double mx, double my) {
        List<Account> accounts = AccountManager.all();
        float top = listTop(y);
        float height = listHeight(y);

        if (accounts.isEmpty()) {
            Fonts.body().drawCentered(gg, "No accounts yet - type a nickname above",
                    x + PANEL_W / 2.0f, top + height / 2.0f, Style.WHITE_25);
            return;
        }

        this.scrollAnim.to(this.scroll);
        float offset = Math.round(this.scrollAnim.get());

        Render2D.pushClip(gg, x, top, PANEL_W, height);
        float cursor = top - offset;
        for (Account account : accounts) {
            if (cursor + ROW_H >= top - ROW_H && cursor <= top + height + ROW_H) {
                renderRow(gg, account, x + PAD, cursor, mx, my);
            }
            cursor += ROW_H + ROW_GAP;
        }
        Render2D.popClip(gg);
    }

    private void renderRow(GuiGraphicsExtractor gg, Account account, float x, float y,
                           double mx, double my) {
        float w = PANEL_W - PAD * 2;
        boolean active = AccountManager.isActive(account);
        boolean hover = Render2D.hovered(mx, my, x, y, w, ROW_H);

        Anim anim = hover(this.rowHover, account.name());
        anim.to(hover);
        float k = anim.get();

        Render2D.round(gg, x, y, w, ROW_H, ROW_R,
                Render2D.lerp(Style.WHITE_02, Style.white(0.06f), k));
        if (active) {
            Render2D.round(gg, x, y + 9.0f, 3.0f, ROW_H - 18.0f, 1.5f, Style.accent());
        }

        HFont label = Fonts.label();
        label.draw(gg, account.name(), x + 16.0f, label.centeredBaseline(y, ROW_H) - 4.0f,
                active ? Style.WHITE : Style.white(0.82f));
        Fonts.body().draw(gg, active ? "in use" : "offline", x + 16.0f,
                label.centeredBaseline(y, ROW_H) + 9.0f,
                active ? Render2D.withAlpha(Style.accent(), 0.9f) : Style.WHITE_25);

        float tx = x + w - 16.0f - 13.0f;
        float ty = y + (ROW_H - 13.0f) / 2.0f;
        Anim trash = hover(this.trashHover, account.name());
        trash.to(Render2D.hovered(mx, my, tx - 6.0f, y, 25.0f, ROW_H));
        Render2D.icon(gg, "trash", tx, ty, 13, 13,
                Render2D.lerp(Style.WHITE_25, Style.WHITE_45, trash.get()));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();
        float x = panelX();
        float y = panelY();

        float fx = x + PAD;
        float fy = y + HEAD_H;
        float fw = PANEL_W - PAD * 2 - ADD_W - 8;

        this.typing = Render2D.hovered(mx, my, fx, fy, fw, FIELD_H);

        float ax = x + PANEL_W - PAD - ADD_W;
        if (Render2D.hovered(mx, my, ax, fy, ADD_W, FIELD_H)) {
            submit();
            return true;
        }

        float top = listTop(y);
        float height = listHeight(y);
        if (my >= top && my <= top + height) {
            float cursor = top - Math.round(this.scrollAnim.get());
            for (Account account : AccountManager.all()) {
                float rowX = x + PAD;
                float w = PANEL_W - PAD * 2;
                float tx = rowX + w - 16.0f - 13.0f;
                if (Render2D.hovered(mx, my, tx - 6.0f, cursor, 25.0f, ROW_H)) {
                    AccountManager.remove(account);
                    clampScroll();
                    return true;
                }
                if (Render2D.hovered(mx, my, rowX, cursor, w, ROW_H)) {
                    if (!AccountManager.login(account)) {
                        say("Leave the server first");
                    }
                    return true;
                }
                cursor += ROW_H + ROW_GAP;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scroll -= (float) scrollY * 28.0f;
        clampScroll();
        return true;
    }

    private void clampScroll() {
        float content = AccountManager.all().size() * (ROW_H + ROW_GAP) - ROW_GAP;
        float max = Math.max(0.0f, content - listHeight(panelY()));
        this.scroll = Mth.clamp(this.scroll, 0.0f, max);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (this.typing) {
            switch (key) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (!this.draft.isEmpty()) {
                        this.draft = this.draft.substring(0, this.draft.length() - 1);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    submit();
                    return true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    this.typing = false;
                    return true;
                }
                default -> {
                }
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!this.typing) {
            return super.charTyped(event);
        }
        char c = (char) event.codepoint();
        boolean allowed = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'
                || c >= '0' && c <= '9' || c == '_';
        if (allowed && this.draft.length() < Account.MAX_NAME) {
            this.draft += c;
        }
        return true;
    }

    private void submit() {
        if (this.draft.isBlank()) {
            return;
        }
        if (!Account.valid(this.draft)) {
            say("3-16 characters, letters digits underscore");
            return;
        }
        if (AccountManager.has(this.draft)) {
            say("Already on the list");
            return;
        }
        Account added = AccountManager.add(this.draft);
        if (added != null) {
            this.draft = "";
            clampScroll();
        }
    }

    private void say(String text) {
        this.notice = text;
        this.noticeUntil = System.currentTimeMillis() + 2600L;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    public static List<Account> snapshot() {
        return new ArrayList<>(AccountManager.all());
    }
}
