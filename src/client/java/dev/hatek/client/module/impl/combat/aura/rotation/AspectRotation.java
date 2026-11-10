package dev.hatek.client.module.impl.combat.aura.rotation;

import dev.hatek.client.module.impl.combat.aura.util.RayTraceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The "ShardAC" and "ShardAC-AI" rotations of the aspectdlc client (its sources are CC0), ported to this client.
 *
 * <p>The head is driven by an "acceleration" that slowly grows (the head creeps toward the aim point), is pulled back
 * when the crosshair is on the body and after every hit (the aim is frozen until the weapon is charged again), is
 * slowed down when the target is very close, and has a small overshoot on big turns. The aim point is a spot that
 * drifts around inside the hitbox with the time and the entity id. Runs once per tick.
 */
public final class AspectRotation {
    public enum Variant { SHARD_AC, SHARD_AC_AI }

    private final Variant variant;

    private LivingEntity target;
    private float lastYaw;
    private float lastPitch;
    private float acc;
    private boolean back;
    private boolean hit;
    private float slow = 1.0F;
    private Vec3 lastPoint;
    private LivingEntity lastPointTarget;
    private int lastPointTicks;
    private float overshootYaw;
    private float overshootPitch;

    public AspectRotation(Variant variant) {
        this.variant = variant;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public void reset() {
        this.target = null;
        this.acc = 0.0F;
        this.back = false;
        this.hit = false;
        this.slow = 1.0F;
        this.lastPoint = null;
        this.lastPointTarget = null;
        this.lastPointTicks = 0;
        this.overshootYaw = 0.0F;
        this.overshootPitch = 0.0F;
        if (mc().player != null) {
            this.lastYaw = mc().player.getYRot();
            this.lastPitch = mc().player.getXRot();
        }
    }

    /** Called right after a hit: the head goes back and the aim is held until the weapon is ready again. */
    public void onAttack() {
        this.hit = true;
        this.back = true;
        this.acc = -0.04F;
    }

    /** The drifting aim point inside the hitbox (about 70 % of the height), refreshed every second tick. */
    private Vec3 point(LivingEntity entity) {
        if (this.lastPointTarget == entity && this.lastPoint != null && this.lastPointTicks < 1) {
            this.lastPointTicks++;
            return this.lastPoint;
        }
        AABB box = entity.getBoundingBox();
        double width = box.maxX - box.minX;
        double height = box.maxY - box.minY;
        double depth = box.maxZ - box.minZ;
        double t = (double) System.currentTimeMillis() / 50.0;
        int id = entity.getId();
        Vec3 point = new Vec3(
                box.minX + width / 2.0 + Math.sin(t + id) * width * 0.45,
                box.minY + height * 0.7 + Math.cos(t * 0.8 + id) * height * 0.1,
                box.minZ + depth / 2.0 + Math.cos(t * 1.2 + id) * depth * 0.45);
        this.lastPointTarget = entity;
        this.lastPoint = point;
        this.lastPointTicks = 0;
        return point;
    }

    /** Slows the head down while the eye is inside or right at the hitbox. */
    private float slowFor(AABB box, Vec3 eye) {
        boolean close = box.inflate(-0.3).contains(eye) || box.inflate(0.3).contains(eye);
        float goal = close ? 0.05F : 1.0F;
        float rate = close ? 1.0F : 0.01F;
        this.slow += (goal - this.slow) * rate;
        this.slow = Mth.clamp(this.slow, 0.05F, 1.0F);
        return this.slow;
    }

    private static boolean rayHits(float pitch, float yaw, AABB box) {
        Vec3 eye = mc().player.getEyePosition();
        if (box.contains(eye)) {
            return true;
        }
        Vec3 end = eye.add(RayTraceUtil.getVectorForRotation(pitch, yaw).scale(1950.0));
        return box.clip(eye, end).isPresent();
    }

    /** One tick of the rotation toward the entity. */
    public void update(LivingEntity entity) {
        Minecraft mc = mc();
        if (mc.player == null || entity == null) {
            return;
        }
        boolean ai = this.variant == Variant.SHARD_AC_AI;
        if (this.target != entity) {
            this.target = entity;
            this.lastYaw = mc.player.getYRot();
            this.lastPitch = mc.player.getXRot();
            this.acc = 0.0F;
            this.back = false;
            this.hit = false;
            this.slow = 1.0F;
            this.lastPoint = null;
            this.lastPointTarget = null;
            this.lastPointTicks = 0;
            this.overshootYaw = 0.0F;
            this.overshootPitch = 0.0F;
        }

        Vec3 point = point(entity);
        Vec3 eye = mc.player.getEyePosition();
        Vec3 delta = point.subtract(eye);
        float wantYaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        // the first variant never looks up, the second one stays inside +-89
        wantPitch = ai ? Mth.clamp(wantPitch, -89.0F, 89.0F) : Mth.clamp(wantPitch, 0.0F, 90.0F);

        AABB box = entity.getBoundingBox();
        boolean aimed = rayHits(this.lastPitch, this.lastYaw, box.inflate(-0.3));
        float errYaw = Mth.wrapDegrees(wantYaw - this.lastYaw);
        float errPitch = wantPitch - this.lastPitch;
        float yaw = this.lastYaw;
        float pitch = this.lastPitch;
        float charge = mc.player.getAttackStrengthScale(0.5F);
        boolean ready = charge >= 0.65F
                || (!mc.player.onGround() && mc.player.getDeltaMovement().y < 0.0 && mc.player.fallDistance > 0.0F);
        if (this.hit && ready) {
            this.hit = false;
            this.back = false;
            this.acc = 0.0F;
        }

        if (this.hit && !ready) {
            // after a hit the aim is held until the weapon is ready again
            this.acc = Math.min(this.acc, 0.0F);
            if (ai) {
                float speed = Math.max(this.acc, 0.05F * slowFor(box, eye) * 0.4F);
                yaw = this.lastYaw + errYaw * Math.min(speed * 1.8F, 1.0F);
                pitch = this.lastPitch + errPitch * Math.min(speed * 0.4F, 1.0F);
            }
        } else if (aimed) {
            if (ai) {
                // on the body: a slow follow, the head does not stop
                float speed = Math.max(this.acc, 0.06F * slowFor(box, eye) * 0.35F);
                yaw = this.lastYaw + errYaw * Math.min(speed * 1.8F, 1.0F);
                pitch = this.lastPitch + errPitch * Math.min(speed * 0.4F, 1.0F);
            }
        } else {
            if (this.back) {
                float floor = -0.1F;
                float step = ai ? (Math.abs(errYaw) > 30.0F ? 0.012F : 0.022F) : (Math.abs(errYaw) > 30.0F ? 0.01F : 0.046F);
                if (this.acc >= floor) {
                    this.acc -= step;
                }
                if (this.acc <= floor) {
                    this.back = false;
                }
            } else {
                this.acc += ai ? 0.0035F : 0.003F;
                if (this.acc >= (ai ? 0.24F : 0.25F)) {
                    this.back = true;
                }
            }
            float speed = Math.max(this.acc, 0.0F) * slowFor(box, eye);
            yaw = this.lastYaw + errYaw * Mth.clamp(speed * 1.8F, 0.0F, 1.0F);
            pitch = this.lastPitch + errPitch * Mth.clamp(speed * 0.4F, 0.0F, 1.0F);
        }

        // a small overshoot on big turns that fades out
        if (Math.abs(errYaw) > 18.0F && Math.abs(this.acc) > 0.08F) {
            this.overshootYaw = Math.signum(errYaw) * Math.min(Math.abs(errYaw) * 0.06F, 1.2F) * 0.9F;
            this.overshootPitch = Math.signum(errPitch) * Math.min(Math.abs(errPitch) * 0.04F, 0.8F) * 0.9F;
        } else {
            this.overshootYaw *= 0.65F;
            this.overshootPitch *= 0.65F;
        }
        float outYaw = yaw + this.overshootYaw;
        float outPitch = pitch + this.overshootPitch;
        if (ai) {
            outPitch = Mth.clamp(outPitch, -89.0F, 89.0F);
        }
        RotationProcess.updateFrame(new Rotation(outYaw, outPitch), 360.0F, 360.0F, 30.0F, 9.0F, 1, 1);
        this.lastYaw = outYaw;
        this.lastPitch = outPitch;
    }
}
