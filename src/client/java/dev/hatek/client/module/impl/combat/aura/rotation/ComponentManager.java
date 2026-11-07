package dev.hatek.client.module.impl.combat.aura.rotation;

import net.minecraft.network.protocol.Packet;

import java.util.HashMap;

public final class ComponentManager extends HashMap<Class<? extends Component>, Component> {
    private static final ComponentManager INSTANCE = new ComponentManager();

    private boolean initialized;

    private volatile FreeLookUtil freeLook;
    private volatile RotationProcess process;
    private volatile ServerView view;

    public static ComponentManager getInstance() {
        return INSTANCE;
    }

    public void init() {
        if (this.initialized) {
            return;
        }
        this.initialized = true;
        dev.hatek.client.module.impl.combat.aura.util.PlayerStats.load();
        this.freeLook = new FreeLookUtil();
        this.process = new RotationProcess();
        this.view = new ServerView();
        add(this.freeLook, this.process, this.view);
    }

    public void add(Component... components) {
        for (Component component : components) {
            put(component.getClass(), component);
        }
    }

    public void unregister(Component... components) {
        for (Component component : components) {
            remove(component.getClass());
        }
    }

    public <T extends Component> T get(Class<T> clazz) {
        return values().stream()
                .filter(component -> component.getClass() == clazz)
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }

    public static void tick() {
        dev.hatek.client.module.impl.combat.aura.util.PlayerStats.tick();
        RotationProcess process = INSTANCE.process;
        if (process != null) {
            process.onTick();
        }
    }

    public static void render3D() {
        RotationProcess process = INSTANCE.process;
        if (process != null) {
            process.onRender3D();
        }
        SpaceTime.onFrame();
    }

    public static void packetSent(Packet<?> packet) {
        ServerView view = INSTANCE.view;
        if (view != null) {
            view.onSend(packet);
        }
    }

    public static boolean look(double deltaYaw, double deltaPitch) {
        FreeLookUtil freeLook = INSTANCE.freeLook;
        return freeLook != null && freeLook.onLook(deltaYaw, deltaPitch);
    }

    public static float[] rotation(float yaw, float pitch) {
        FreeLookUtil freeLook = INSTANCE.freeLook;
        return freeLook == null ? new float[]{yaw, pitch} : freeLook.onRotation(yaw, pitch);
    }
}
