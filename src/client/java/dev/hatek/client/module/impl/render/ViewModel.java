package dev.hatek.client.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.impl.render.hud.DragHandle;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ButtonSetting;
import dev.hatek.client.module.setting.HandSetting;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.HumanoidArm;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Position of the first-person hands. With the chat open: hold the left mouse button and drag to move a hand
 * (the left half of the screen moves the left hand, the right half the right one), the mouse wheel moves it
 * away from or toward the camera, the right button resets it. Over the chat messages the mouse behaves normally.
 */
public final class ViewModel extends Module {
    private static final float HAND_DEPTH = 0.72f;
    private static final double WHEEL_STEP = 0.05;
    private static final double ROLL_STEP = 6.0;
    private static final double DEGREES_PER_PIXEL = 0.35;

    private static ViewModel instance;

    private final BoolSetting chatEditor = new BoolSetting("Move in chat", true);
    private final ButtonSetting resetButton = new ButtonSetting("Reset hands", "Reset both hands", this::resetBoth);
    private final HandSetting right = new HandSetting("Right hand");
    private final HandSetting left = new HandSetting("Left hand");

    private HumanoidArm draggedArm;
    private double lastX;
    private double lastY;
    private boolean lmbWasDown;
    private boolean rmbWasDown;

    public ViewModel() {
        super("ViewModel", "Position of your hands: drag them in chat", Category.RENDER);
        instance = this;
        with(this.chatEditor, this.resetButton, this.right, this.left);
    }

    public static ViewModel instance() {
        return instance;
    }

    private HandSetting hand(HumanoidArm arm) {
        return arm == HumanoidArm.RIGHT ? this.right : this.left;
    }

    private void resetBoth() {
        this.right.reset();
        this.left.reset();
    }

    /** Called from the item-in-hand renderer after the arm transform. */
    public void apply(PoseStack pose, HumanoidArm arm) {
        if (!isEnabled()) {
            return;
        }
        HandSetting hand = hand(arm);
        pose.translate(hand.x(), hand.y(), hand.z());
        if (hand.hasRotation()) {
            pose.mulPose((org.joml.Quaternionfc) Axis.XP.rotationDegrees(hand.rotX()));
            pose.mulPose((org.joml.Quaternionfc) Axis.YP.rotationDegrees(hand.rotY()));
            pose.mulPose((org.joml.Quaternionfc) Axis.ZP.rotationDegrees(hand.rotZ()));
        }
    }

    private boolean editorActive() {
        Minecraft mc = Minecraft.getInstance();
        return isEnabled() && this.chatEditor.value() && mc.gui.screen() instanceof ChatScreen;
    }

    private static HumanoidArm armAt(double guiX, Screen screen) {
        return guiX < screen.width / 2.0 ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    private static boolean overChatMessages(double guiX, double guiY, Screen screen) {
        var options = Minecraft.getInstance().options;
        double scale = options.chatScale().get();
        double chatWidth = ChatComponent.getWidth(options.chatWidth().get()) * scale + 12.0;
        double chatHeight = ChatComponent.getHeight(options.chatHeightFocused().get()) * scale;
        double chatBottom = screen.height - 40;
        return guiX < chatWidth && guiY > chatBottom - chatHeight && guiY < chatBottom + 26.0;
    }

    private static boolean ctrlDown() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    /** Mouse wheel in the chat screen. Returns true when it moved a hand (the chat then does not scroll). */
    public boolean onScroll(double guiX, double guiY, double scrollY) {
        if (!editorActive() || scrollY == 0.0) {
            return false;
        }
        Screen screen = Minecraft.getInstance().gui.screen();
        if (overChatMessages(guiX, guiY, screen)) {
            return false;
        }
        HandSetting hand = hand(armAt(guiX, screen));
        if (ctrlDown()) {
            hand.roll(Math.signum(scrollY) * ROLL_STEP);
        } else {
            hand.moveZ(Math.signum(scrollY) * WHEEL_STEP);
        }
        return true;
    }

    /** HUD pass while the chat is open: drag/reset handling (polled) and the hint. */
    public void renderEditor(GuiGraphicsExtractor gg) {
        Minecraft mc = Minecraft.getInstance();
        if (!editorActive()) {
            this.draggedArm = null;
            this.lmbWasDown = false;
            this.rmbWasDown = false;
            return;
        }
        Screen screen = mc.gui.screen();
        float guiScale = Render2D.guiScale();
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();
        double guiX = mx / guiScale;
        double guiY = my / guiScale;
        long window = mc.getWindow().handle();
        boolean lmb = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rmb = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean free = !overChatMessages(guiX, guiY, screen) && !DragHandle.cardsBusy();

        if (lmb && !this.lmbWasDown && free) {
            this.draggedArm = armAt(guiX, screen);
            this.lastX = mx;
            this.lastY = my;
        }
        if (!lmb) {
            this.draggedArm = null;
        }
        if (this.draggedArm != null) {
            HandSetting hand = hand(this.draggedArm);
            if (ctrlDown()) {
                // Ctrl + drag turns the hand instead of moving it
                hand.rotate((mx - this.lastX) * DEGREES_PER_PIXEL, (my - this.lastY) * DEGREES_PER_PIXEL);
            } else {
                double fov = Math.toRadians(mc.options.fov().get());
                double depth = Math.max(0.2, HAND_DEPTH - hand.z());
                double unitsPerPixel = 2.0 * Math.tan(fov / 2.0) * depth / Render2D.screenHeight();
                hand.move((mx - this.lastX) * unitsPerPixel, -(my - this.lastY) * unitsPerPixel);
            }
            this.lastX = mx;
            this.lastY = my;
        }
        if (rmb && !this.rmbWasDown && free) {
            hand(armAt(guiX, screen)).reset();
        }
        this.lmbWasDown = lmb;
        this.rmbWasDown = rmb;

        drawHint(gg);
    }

    private void drawHint(GuiGraphicsExtractor gg) {
        String text;
        if (this.draggedArm != null) {
            HandSetting hand = hand(this.draggedArm);
            text = String.format(Locale.ROOT, "%s hand   X %.2f  Y %.2f  Z %.2f   |   rot %.0f / %.0f / %.0f",
                    this.draggedArm == HumanoidArm.RIGHT ? "Right" : "Left", hand.x(), hand.y(), hand.z(),
                    hand.rotX(), hand.rotY(), hand.rotZ());
        } else {
            text = "LMB drag: move   |   Ctrl + LMB drag: rotate   |   wheel: closer / farther   |   Ctrl + wheel: roll   |   RMB: reset";
        }
        float s = Render2D.screenHeight() / 1080.0f;
        HFont font = Fonts.of(Fonts.INTER_MEDIUM, Math.max(9, Math.round(14.0f * s)));
        Render2D.begin(gg);
        try {
            float w = font.width(text) + 28.0f * s;
            float h = 30.0f * s;
            float x = (Render2D.screenWidth() - w) / 2.0f;
            float y = 16.0f * s;
            Render2D.round(gg, x, y, w, h, h / 2.0f, 0xF00D0D10);
            font.drawCentered(gg, text, x + w / 2.0f, y + font.centeredBaseline(y, h) - y, 0xFFFFFFFF);
        } finally {
            Render2D.end(gg);
        }
    }
}
