package com.ext.healthextended.logic;

import com.ext.healthextended.data.BodyPart;
import com.ext.healthextended.data.HitFace;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Resolves which {@link BodyPart} was hit and where (localV) within that part,
 * using three tiers of accuracy:
 *
 * <ol>
 *   <li><b>Deterministic environmental</b> – damage types whose affected body part
 *       is known without spatial data (fall, drown, fire on feet, etc.).</li>
 *   <li><b>Projectile exact</b> – the stored Y position from
 *       {@link com.ext.healthextended.event.ProjectileImpactTracker} is used to
 *       derive the normalized hit height on the player's body.</li>
 *   <li><b>Melee geometry</b> – the attacker's Y center is compared to the victim's
 *       bounding-box subdivisions; horizontal lateral vs. forward analysis discriminates
 *       arm hits from torso hits.</li>
 *   <li><b>Weighted random fallback</b> – same distribution as the old heuristic.</li>
 * </ol>
 */
public final class HitLocationResolver {

    // Vanilla player bounding-box defaults
    private static final float PLAYER_HEIGHT = 1.8f;

    // Vertical split points (fraction of PLAYER_HEIGHT from foot = 0)
    private static final float LEG_TORSO_BOUNDARY  = 0.42f;  // 0 – 42%  → legs
    private static final float TORSO_HEAD_BOUNDARY = 0.78f;  // 42 – 78% → torso/arms; above → head

    // Arm discrimination: if |lateral| > |forward| * this factor, it's an arm hit
    private static final float ARM_LATERAL_FACTOR = 0.80f;

    private HitLocationResolver() {}

    /** Result of a hit resolution: which part was struck, how far up that part (0=bottom, 1=top), and which face. */
    public record ImpactResult(BodyPart part, float localV, HitFace face) {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Resolves the body part struck by {@code source} on {@code victim}.
     *
     * @param victim           the player taking damage
     * @param source           the damage source
     * @param projectileImpact impact position captured by {@link com.ext.healthextended.event.ProjectileImpactTracker},
     *                         or {@code null} if not available
     */
    public static ImpactResult resolve(Player victim, DamageSource source, @Nullable Vec3 projectileImpact) {
        String damageId = normId(source);

        // Tier 1: deterministic environmental
        ImpactResult env = tryDeterministicEnvironmental(victim, damageId);
        if (env != null) {
            return env;
        }

        // Tier 2: projectile exact impact position
        if (projectileImpact != null) {
            return fromImpactY(victim, projectileImpact.y, source);
        }

        // Tier 3: melee geometry (non-projectile living attacker)
        if (source.getDirectEntity() instanceof LivingEntity attacker
                && !(source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile)) {
            return fromAttackerGeometry(victim, attacker);
        }

        // Tier 4: heuristic fallback
        return heuristicFallback(victim, damageId);
    }

    // -------------------------------------------------------------------------
    // Tier 1 – deterministic environmental
    // -------------------------------------------------------------------------

    @Nullable
    private static ImpactResult tryDeterministicEnvironmental(Player victim, String damageId) {
        return switch (damageId) {
            case "fall", "flyintowall"                                  -> legs(victim);
            case "drown", "inwall", "lightningbolt"                     -> head();
            case "starve", "magic", "indirectmagic", "wither",
                 "dragonbreath", "witherskull", "outofworld",
                 "explosion", "explosionplayer"                          -> torso();
            case "hotfloor", "campfire", "stalagmite"                   -> legs(victim);
            case "cactus", "sweetberrybush"                             -> outerLimb(victim);
            case "freeze"                                               -> limb(victim);
            case "infire", "onfire", "lava"                             -> firePart(victim);
            default                                                     -> null;
        };
    }

    // -------------------------------------------------------------------------
    // Tier 2 – projectile Y-position
    // -------------------------------------------------------------------------

    private static ImpactResult fromImpactY(Player victim, double worldY, DamageSource source) {
        float n = normalize(victim, worldY);
        HitFace face = computeFaceFromEntity(victim, source.getEntity());

        if (n > TORSO_HEAD_BOUNDARY) {
            return new ImpactResult(BodyPart.HEAD, partLocalV(n, TORSO_HEAD_BOUNDARY, 1.0f), face);
        }
        if (n > LEG_TORSO_BOUNDARY) {
            return new ImpactResult(BodyPart.TORSO, partLocalV(n, LEG_TORSO_BOUNDARY, TORSO_HEAD_BOUNDARY), face);
        }
        boolean isLeft = victim.getRandom().nextBoolean();
        return new ImpactResult(
                isLeft ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG,
                partLocalV(n, 0.0f, LEG_TORSO_BOUNDARY),
                face);
    }

    // -------------------------------------------------------------------------
    // Tier 3 – attacker geometry
    // -------------------------------------------------------------------------

    private static ImpactResult fromAttackerGeometry(Player victim, LivingEntity attacker) {
        // Estimate impact Y as the midpoint between attacker's foot and eye.
        double impactY = (attacker.getY() + attacker.getEyeY()) * 0.5;
        float n = normalize(victim, impactY);

        // Decompose attacker-relative offset into victim-local axes once.
        Vec3 look = victim.getLookAngle();
        double dx = attacker.getX() - victim.getX();
        double dz = attacker.getZ() - victim.getZ();
        // right vector (90° CW in XZ when viewed from above): (−fz, 0, fx)
        double rightComp   = dx * (-look.z) + dz * look.x;
        double forwardComp = dx *   look.x  + dz * look.z;
        HitFace face = computeFaceFromDirection(rightComp, forwardComp);

        if (n > TORSO_HEAD_BOUNDARY) {
            return new ImpactResult(BodyPart.HEAD, partLocalV(n, TORSO_HEAD_BOUNDARY, 1.0f), face);
        }
        if (n > LEG_TORSO_BOUNDARY) {
            float lv = partLocalV(n, LEG_TORSO_BOUNDARY, TORSO_HEAD_BOUNDARY);
            if (Math.abs(rightComp) > Math.abs(forwardComp) * ARM_LATERAL_FACTOR) {
                // attacker is more to the side than in front/behind → arm hit
                BodyPart arm = rightComp > 0 ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
                return new ImpactResult(arm, lv, face);
            }
            return new ImpactResult(BodyPart.TORSO, lv, face);
        }
        boolean isLeft = victim.getRandom().nextBoolean();
        return new ImpactResult(
                isLeft ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG,
                partLocalV(n, 0.0f, LEG_TORSO_BOUNDARY),
                face);
    }

    // -------------------------------------------------------------------------
    // Tier 4 – heuristic fallback
    // -------------------------------------------------------------------------

    private static ImpactResult heuristicFallback(Player victim, String damageId) {
        // Arrow / trident without stored impact → prefer upper body
        if (damageId.equals("arrow") || damageId.equals("trident")) {
            return upperBody(victim);
        }
        // Weighted random matching the old ROUTING_WEIGHTS (5 torso, 1 head, 2 each arm, 2 each leg)
        int roll = victim.getRandom().nextInt(14);
        float limbV = 0.3f + victim.getRandom().nextFloat() * 0.4f;
        if (roll < 5)  return torso();
        if (roll < 6)  return head();
        if (roll < 8)  return new ImpactResult(BodyPart.LEFT_ARM,  limbV, HitFace.FRONT);
        if (roll < 10) return new ImpactResult(BodyPart.RIGHT_ARM, limbV, HitFace.FRONT);
        if (roll < 12) return new ImpactResult(BodyPart.LEFT_LEG,  limbV, HitFace.FRONT);
        return                 new ImpactResult(BodyPart.RIGHT_LEG, limbV, HitFace.FRONT);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Normalizes worldY to [0, 1] over the player's bounding box height. */
    private static float normalize(Player victim, double worldY) {
        return Math.max(0.0f, Math.min(1.0f, (float) ((worldY - victim.getY()) / PLAYER_HEIGHT)));
    }

    /** Maps normalized global Y into a [0,1] local V within a part's vertical range. */
    private static float partLocalV(float n, float partMin, float partMax) {
        if (partMax <= partMin) return 0.5f;
        return Math.max(0.0f, Math.min(1.0f, (n - partMin) / (partMax - partMin)));
    }

    private static String normId(DamageSource source) {
        return source.getMsgId().toLowerCase(Locale.ROOT).replace("_", "");
    }

    private static ImpactResult legs(Player victim) {
        // fall / hotfloor → lower leg area
        float lv = 0.1f + victim.getRandom().nextFloat() * 0.3f;
        return new ImpactResult(victim.getRandom().nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG, lv, HitFace.FRONT);
    }

    private static ImpactResult head() {
        return new ImpactResult(BodyPart.HEAD, 0.5f, HitFace.FRONT);
    }

    private static ImpactResult torso() {
        return new ImpactResult(BodyPart.TORSO, 0.5f, HitFace.FRONT);
    }

    private static ImpactResult outerLimb(Player victim) {
        int r = victim.getRandom().nextInt(4);
        BodyPart part = switch (r) {
            case 0  -> BodyPart.LEFT_ARM;
            case 1  -> BodyPart.RIGHT_ARM;
            case 2  -> BodyPart.LEFT_LEG;
            default -> BodyPart.RIGHT_LEG;
        };
        float lv = 0.3f + victim.getRandom().nextFloat() * 0.4f;
        return new ImpactResult(part, lv, HitFace.FRONT);
    }

    private static ImpactResult limb(Player victim) {
        // freeze → arms/legs with leg bias
        int r = victim.getRandom().nextInt(6);
        BodyPart part = switch (r) {
            case 0  -> BodyPart.LEFT_ARM;
            case 1  -> BodyPart.RIGHT_ARM;
            case 2, 3 -> BodyPart.LEFT_LEG;
            default -> BodyPart.RIGHT_LEG;
        };
        float lv = 0.3f + victim.getRandom().nextFloat() * 0.4f;
        return new ImpactResult(part, lv, HitFace.FRONT);
    }

    private static ImpactResult firePart(Player victim) {
        // Fire tends to hit lower body first; leg wounds appear near feet
        int r = victim.getRandom().nextInt(8);
        return switch (r) {
            case 0       -> new ImpactResult(BodyPart.HEAD, 0.5f, HitFace.FRONT);
            case 1, 2, 3 -> new ImpactResult(BodyPart.TORSO, 0.3f + victim.getRandom().nextFloat() * 0.4f, HitFace.FRONT);
            case 4       -> new ImpactResult(BodyPart.LEFT_ARM,  0.1f + victim.getRandom().nextFloat() * 0.3f, HitFace.FRONT);
            case 5       -> new ImpactResult(BodyPart.RIGHT_ARM, 0.1f + victim.getRandom().nextFloat() * 0.3f, HitFace.FRONT);
            case 6       -> new ImpactResult(BodyPart.LEFT_LEG,  0.1f + victim.getRandom().nextFloat() * 0.2f, HitFace.FRONT);
            default      -> new ImpactResult(BodyPart.RIGHT_LEG, 0.1f + victim.getRandom().nextFloat() * 0.2f, HitFace.FRONT);
        };
    }

    private static ImpactResult upperBody(Player victim) {
        int r = victim.getRandom().nextInt(6);
        BodyPart part = switch (r) {
            case 0       -> BodyPart.HEAD;
            case 1, 2, 3 -> BodyPart.TORSO;
            case 4       -> BodyPart.LEFT_ARM;
            default      -> BodyPart.RIGHT_ARM;
        };
        return new ImpactResult(part, 0.3f + victim.getRandom().nextFloat() * 0.4f, HitFace.FRONT);
    }

    // -------------------------------------------------------------------------
    // Face helpers
    // -------------------------------------------------------------------------

    /**
     * Computes which face of the victim was struck based on the position of the
     * damage source entity, relative to the victim's look direction.
     */
    private static HitFace computeFaceFromEntity(Player victim, @Nullable Entity source) {
        if (source == null) return HitFace.FRONT;
        Vec3 look = victim.getLookAngle();
        double dx = source.getX() - victim.getX();
        double dz = source.getZ() - victim.getZ();
        double rightComp   = dx * (-look.z) + dz * look.x;
        double forwardComp = dx *   look.x  + dz * look.z;
        return computeFaceFromDirection(rightComp, forwardComp);
    }

    /**
     * Maps decomposed victim-local (right, forward) components to a {@link HitFace}.
     * "Forward" means in the direction the victim is looking.
     */
    private static HitFace computeFaceFromDirection(double rightComp, double forwardComp) {
        if (Math.abs(rightComp) >= Math.abs(forwardComp)) {
            return rightComp > 0 ? HitFace.RIGHT : HitFace.LEFT;
        }
        return forwardComp >= 0 ? HitFace.FRONT : HitFace.BACK;
    }
}
