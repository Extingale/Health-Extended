package com.ext.healthextended.logic;

import com.ext.healthextended.data.BodyPart;
import com.ext.healthextended.data.BodyPartHealth;
import com.ext.healthextended.data.HediffDef;
import com.ext.healthextended.data.HitFace;
import com.ext.healthextended.data.PlayerBodyData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Locale;

public final class LocationalHealthLogic {

    private LocationalHealthLogic() {
    }

    /**
     * Result returned from {@link #applyDamage}. Carries the resolved body part,
     * the hediff that was applied, the integer damage amount, and the vertical
     * position within the body part (for wound-mark placement).
     */
    public record DamageResult(BodyPart part, HediffDef hediff, int damage, float localV, HitFace face) {}

    public static float getWeightedCurrent(PlayerBodyData bodyData) {
        float total = 0.0f;
        for (BodyPart part : BodyPart.values()) {
            BodyPartHealth health = bodyData.getHealth(part);
            total += health.getCurrentHp() * part.getOverallWeight();
        }
        return total;
    }

    public static float getWeightedMax(PlayerBodyData bodyData) {
        float total = 0.0f;
        for (BodyPart part : BodyPart.values()) {
            BodyPartHealth health = bodyData.getHealth(part);
            total += health.getMaxHp() * part.getOverallWeight();
        }
        return total;
    }

    public static float getOverallHealthPercent(PlayerBodyData bodyData) {
        float max = getWeightedMax(bodyData);
        if (max <= 0.0f) {
            return 0.0f;
        }
        return getWeightedCurrent(bodyData) / max;
    }

    public static float getProjectedVanillaHealth(PlayerBodyData bodyData, float vanillaMaxHealth) {
        float projected = getOverallHealthPercent(bodyData) * vanillaMaxHealth;
        projected = Math.round(projected * 2.0f) / 2.0f;
        if (isHeadDestroyed(bodyData)) {
            return 0.0f;
        }
        return Mth.clamp(projected, 1.0f, vanillaMaxHealth);
    }

    public static boolean isHeadDestroyed(PlayerBodyData bodyData) {
        return bodyData.getHealth(BodyPart.HEAD).isDestroyed();
    }

    public static BodyPart chooseDamagedPart(Player player, DamageSource source) {
        return HitLocationResolver.resolve(player, source, null).part();
    }

    public static int toLocationalDamage(float vanillaDamageAmount) {
        return Math.max(1, Mth.ceil(vanillaDamageAmount));
    }

    /**
     * Applies locational damage to {@code bodyData} and returns a {@link DamageResult}
     * containing the resolved body part, hediff, damage amount, and wound-mark anchor.
     *
     * @param projectileImpact optional pre-captured projectile impact position
     *                         from {@link com.ext.healthextended.event.ProjectileImpactTracker}
     */
    public static DamageResult applyDamage(PlayerBodyData bodyData, Player player, DamageSource source,
                                           float vanillaDamageAmount, @Nullable Vec3 projectileImpact) {
        int damage = toLocationalDamage(vanillaDamageAmount);
        HitLocationResolver.ImpactResult impact = HitLocationResolver.resolve(player, source, projectileImpact);
        HediffDef hediff = chooseHediff(source, damage);
        String description = buildDamageSourceDescription(source, hediff);
        BodyPart part = impact.part();

        if (isLimbPart(part)) {
            int limbHp = bodyData.getHealth(part).getCurrentHp();
            if (limbHp <= 0) {
                // Limb already destroyed — all damage overflows to torso
                HediffLogic.applyDamage(bodyData.getHealth(BodyPart.TORSO), hediff, damage,
                        player.getRandom(), description);
            } else if (damage > limbHp) {
                // Hit exceeds remaining limb capacity — fill the limb, spill excess to torso
                HediffLogic.applyDamage(bodyData.getHealth(part), hediff, limbHp,
                        player.getRandom(), description);
                HediffLogic.applyDamage(bodyData.getHealth(BodyPart.TORSO), hediff, damage - limbHp,
                        player.getRandom(), description);
            } else {
                HediffLogic.applyDamage(bodyData.getHealth(part), hediff, damage,
                        player.getRandom(), description);
            }
        } else {
            HediffLogic.applyDamage(bodyData.getHealth(part), hediff, damage,
                    player.getRandom(), description);
        }
        return new DamageResult(part, hediff, damage, impact.localV(), impact.face());
    }

    /** Legacy overload kept for call-sites that do not have a stored projectile impact. */
    public static BodyPart applyDamage(PlayerBodyData bodyData, Player player, DamageSource source,
                                       float vanillaDamageAmount) {
        return applyDamage(bodyData, player, source, vanillaDamageAmount, null).part();
    }

    public static String buildDamageSourceDescription(DamageSource source, HediffDef hediff) {
        String damageId = getDamageId(source);
        String sourceName = getSourceName(source);

        if (!sourceName.isBlank()) {
            if (hediff == HediffDef.BITE) {
                return "Bitten by " + sourceName;
            }
            if (hediff == HediffDef.CUT) {
                return "Cut by " + sourceName;
            }
            if (hediff == HediffDef.MAGIC_WOUND) {
                return "Afflicted by " + sourceName;
            }
            if (hediff == HediffDef.WITHERED) {
                return "Withered by " + sourceName;
            }
            if (hediff == HediffDef.BRUISE) {
                return "Hit by " + sourceName;
            }
            if (hediff == HediffDef.BURN) {
                return "Burned by " + sourceName;
            }
        }

        return switch (damageId) {
            case "fall" -> "Caused by a fall";
            case "starve" -> "Caused by starvation";
            case "drown" -> "Caused by drowning";
            case "inwall" -> "Caused by being stuck in a wall";
            case "outofworld" -> "Caused by the void";
            case "magic", "indirectmagic" -> "Caused by magic";
            case "wither", "witherskull" -> "Caused by wither damage";
            case "dragonbreath" -> "Caused by dragon breath";
            case "freeze" -> "Caused by freezing";
            case "infire", "onfire" -> "Caused by fire";
            case "lava" -> "Burned by lava";
            case "campfire" -> "Burned by a campfire";
            case "hotfloor" -> "Burned by a magma block";
            case "lightningbolt" -> "Struck by lightning";
            case "cactus" -> "Pricked by a cactus";
            case "sweetberrybush" -> "Scratched by a sweet berry bush";
            case "flyintowall" -> "Caused by flying into a wall";
            case "explosion", "explosionplayer" -> "Caught in an explosion";
            case "stalagmite" -> "Impaled by a stalagmite";
            case "thorns" -> "Reflected by thorns";
            default -> hediff.getDisplayName();
        };
    }

    public static BodyPart chooseStatusTargetPart(Player player, Entity source, boolean harmful) {
        if (source instanceof AbstractArrow || source instanceof ThrownTrident) {
            return chooseUpperBodyPart(player);
        }

        if (source instanceof LivingEntity) {
            return chooseUpperBodyPart(player);
        }

        return chooseEnvironmentStatusTargetPart(player, harmful);
    }

    public static BodyPart chooseEnvironmentStatusTargetPart(Player player, boolean harmful) {
        if (harmful) {
            return player.getRandom().nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
        }

        return BodyPart.TORSO;
    }

    private static HediffDef chooseHediff(DamageSource source, int damage) {
        String damageId = getDamageId(source);
        Entity directEntity = source.getDirectEntity();
        Entity attacker = source.getEntity();

        if ("fall".equals(damageId)) {
            return HediffDef.BRUISE;
        }

        if (isAny(damageId, "starve")) {
            return HediffDef.STARVATION;
        }
        if (isAny(damageId, "drown", "inwall")) {
            return HediffDef.SUFFOCATION;
        }
        if (isAny(damageId, "freeze")) {
            return HediffDef.FROSTBITE;
        }
        if (isAny(damageId, "infire", "onfire", "lava", "campfire", "hotfloor")) {
            return HediffDef.BURN;
        }
        if (isAny(damageId, "lightningbolt")) {
            return HediffDef.BURN;
        }
        if (isAny(damageId, "magic", "indirectmagic", "dragonbreath", "thorns")) {
            return HediffDef.MAGIC_WOUND;
        }
        if (isAny(damageId, "wither", "witherSkull")) {
            return HediffDef.WITHERED;
        }
        if (isAny(damageId, "cactus", "sweetberrybush")) {
            return HediffDef.CUT;
        }
        if (isAny(damageId, "flyintowall")) {
            return HediffDef.BRUISE;
        }
        if (isAny(damageId, "explosion", "explosion.player")) {
            return HediffDef.BRUISE;
        }

        if (directEntity instanceof ThrownTrident || "trident".equals(damageId)) {
            return HediffDef.CUT;
        }
        if (directEntity instanceof AbstractArrow || "arrow".equals(damageId)) {
            return HediffDef.CUT;
        }

        if (attacker instanceof Zombie || attacker instanceof Spider || attacker instanceof CaveSpider || attacker instanceof Wolf || attacker instanceof Bee) {
            return HediffDef.BITE;
        }

        if (attacker instanceof LivingEntity livingAttacker) {
            ItemStack weapon = livingAttacker.getMainHandItem();
            if (weapon.getItem() instanceof SwordItem || weapon.getItem() instanceof AxeItem) {
                return HediffDef.CUT;
            }
        }

        if (isAny(damageId, "mobattack", "playerattack", "sting")) {
            return HediffDef.BRUISE;
        }

        return HediffDef.BRUISE;
    }

    private static String getDamageId(DamageSource source) {
        return source.getMsgId().toLowerCase(Locale.ROOT).replace("_", "");
    }

    private static String getSourceName(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker != null) {
            return attacker.getName().getString();
        }

        Entity directEntity = source.getDirectEntity();
        if (directEntity != null) {
            return directEntity.getName().getString();
        }

        return "";
    }

    private static boolean isAny(String value, String... options) {
        for (String option : options) {
            if (value.equals(option.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLimbPart(BodyPart part) {
        return part == BodyPart.LEFT_ARM || part == BodyPart.RIGHT_ARM
                || part == BodyPart.LEFT_LEG || part == BodyPart.RIGHT_LEG;
    }

    private static BodyPart chooseUpperBodyPart(Player player) {
        int roll = player.getRandom().nextInt(6);
        return switch (roll) {
            case 0 -> BodyPart.HEAD;
            case 1, 2, 3 -> BodyPart.TORSO;
            case 4 -> BodyPart.LEFT_ARM;
            default -> BodyPart.RIGHT_ARM;
        };
    }

    private static BodyPart chooseLimbPart(Player player, boolean preferLegs) {
        int roll = player.getRandom().nextInt(preferLegs ? 6 : 4);
        if (preferLegs) {
            return switch (roll) {
                case 0 -> BodyPart.LEFT_ARM;
                case 1 -> BodyPart.RIGHT_ARM;
                case 2, 3 -> BodyPart.LEFT_LEG;
                default -> BodyPart.RIGHT_LEG;
            };
        }
        return switch (roll) {
            case 0 -> BodyPart.LEFT_ARM;
            case 1 -> BodyPart.RIGHT_ARM;
            case 2 -> BodyPart.LEFT_LEG;
            default -> BodyPart.RIGHT_LEG;
        };
    }

    private static BodyPart chooseFirePart(Player player) {
        int roll = player.getRandom().nextInt(8);
        return switch (roll) {
            case 0 -> BodyPart.HEAD;
            case 1, 2, 3 -> BodyPart.TORSO;
            case 4 -> BodyPart.LEFT_ARM;
            case 5 -> BodyPart.RIGHT_ARM;
            case 6 -> BodyPart.LEFT_LEG;
            default -> BodyPart.RIGHT_LEG;
        };
    }

    private static BodyPart chooseBlockContactPart(Player player, String damageId) {
        return switch (damageId) {
            case "hotfloor", "campfire", "stalagmite" -> player.getRandom().nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
            case "cactus", "sweetberrybush" -> chooseOuterLimbPart(player);
            default -> BodyPart.TORSO;
        };
    }

    private static BodyPart chooseOuterLimbPart(Player player) {
        return switch (player.getRandom().nextInt(4)) {
            case 0 -> BodyPart.LEFT_ARM;
            case 1 -> BodyPart.RIGHT_ARM;
            case 2 -> BodyPart.LEFT_LEG;
            default -> BodyPart.RIGHT_LEG;
        };
    }
}