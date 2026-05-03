package com.ext.healthextended.logic;

import com.ext.healthextended.data.BodyPart;
import com.ext.healthextended.data.BodyPartHealth;
import com.ext.healthextended.data.HediffDef;
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

import java.util.Locale;

public final class LocationalHealthLogic {

    private static final BodyPart[] ROUTING_PARTS = {
            BodyPart.TORSO,
            BodyPart.HEAD,
            BodyPart.LEFT_ARM,
            BodyPart.RIGHT_ARM,
            BodyPart.LEFT_LEG,
            BodyPart.RIGHT_LEG
    };

    // placeholder weights for now, wont be needed after true hit location detectoin exists
    private static final int[] ROUTING_WEIGHTS = {5, 1, 2, 2, 2, 2};

    private LocationalHealthLogic() {
    }

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
        String damageId = getDamageId(source);

        if (isAny(damageId, "hotfloor", "campfire", "cactus", "sweetberrybush", "stalagmite")) {
            return chooseBlockContactPart(player, damageId);
        }

        if (isAny(damageId, "fall", "starve", "drown", "inwall", "outofworld", "magic", "indirectmagic", "wither", "dragonbreath", "mobattack", "playerattack")) {
            return switch (damageId) {
                case "fall" -> player.getRandom().nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
                case "starve", "magic", "indirectmagic", "wither", "dragonbreath", "outofworld" -> BodyPart.TORSO;
                case "drown", "inwall" -> BodyPart.HEAD;
                default -> chooseUpperBodyPart(player);
            };
        }

        if (isAny(damageId, "freeze")) {
            return chooseLimbPart(player, true);
        }

        if (isAny(damageId, "infire", "onfire", "lava", "lightningbolt")) {
            if ("lightningbolt".equals(damageId)) {
                return BodyPart.HEAD;
            }
            return chooseFirePart(player);
        }

        if (isAny(damageId, "flyintowall", "explosion", "explosion.player")) {
            return BodyPart.TORSO;
        }

        Entity directEntity = source.getDirectEntity();
        if (directEntity instanceof AbstractArrow || directEntity instanceof ThrownTrident || isAny(damageId, "arrow", "trident")) {
            return chooseUpperBodyPart(player);
        }

        int total = 0;
        for (int weight : ROUTING_WEIGHTS) {
            total += weight;
        }

        int roll = player.getRandom().nextInt(total);
        for (int index = 0; index < ROUTING_PARTS.length; index++) {
            roll -= ROUTING_WEIGHTS[index];
            if (roll < 0) {
                return ROUTING_PARTS[index];
            }
        }

        return BodyPart.TORSO;
    }

    public static int toLocationalDamage(float vanillaDamageAmount) {
        return Math.max(1, Mth.ceil(vanillaDamageAmount));
    }

    public static BodyPart applyDamage(PlayerBodyData bodyData, Player player, DamageSource source, float vanillaDamageAmount) {
        int damage = toLocationalDamage(vanillaDamageAmount);
        BodyPart part = chooseDamagedPart(player, source);
        HediffDef hediff = chooseHediff(source, damage);
        HediffLogic.applyDamage(bodyData.getHealth(part), hediff, damage, player.getRandom(), buildDamageSourceDescription(source, hediff));
        return part;
    }

    public static String buildDamageSourceDescription(DamageSource source, HediffDef hediff) {
        String damageId = getDamageId(source);
        String sourceName = getSourceName(source);

        if (!sourceName.isBlank()) {
            if (hediff == HediffDef.BITE) {
                return "Bitten by " + sourceName;
            }
            if (hediff == HediffDef.STAB) {
                return "Stabbed by " + sourceName;
            }
            if (hediff == HediffDef.CUT || hediff == HediffDef.DEEP_CUT) {
                return "Cut by " + sourceName;
            }
            if (hediff == HediffDef.MAGIC_WOUND) {
                return "Afflicted by " + sourceName;
            }
            if (hediff == HediffDef.WITHERED) {
                return "Withered by " + sourceName;
            }
            if (hediff == HediffDef.BRUISE || hediff == HediffDef.CRUSH || hediff == HediffDef.FRACTURE) {
                return "Hit by " + sourceName;
            }
            if (hediff == HediffDef.BURN || hediff == HediffDef.ELECTRICAL_BURN) {
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
            if (damage >= 7) {
                return HediffDef.CRUSH;
            }
            if (damage >= 4) {
                return HediffDef.FRACTURE;
            }
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
            return HediffDef.ELECTRICAL_BURN;
        }
        if (isAny(damageId, "magic", "indirectmagic", "dragonbreath", "thorns")) {
            return HediffDef.MAGIC_WOUND;
        }
        if (isAny(damageId, "wither", "witherSkull")) {
            return HediffDef.WITHERED;
        }
        if (isAny(damageId, "cactus", "sweetberrybush")) {
            return "cactus".equals(damageId) ? HediffDef.PIERCING_WOUND : HediffDef.MINOR_SCRATCH;
        }
        if (isAny(damageId, "flyintowall")) {
            return damage >= 6 ? HediffDef.CRUSH : HediffDef.BRUISE;
        }
        if (isAny(damageId, "explosion", "explosion.player")) {
            return damage >= 6 ? HediffDef.CRUSH : HediffDef.BRUISE;
        }

        if (directEntity instanceof ThrownTrident || "trident".equals(damageId)) {
            return HediffDef.STAB;
        }
        if (directEntity instanceof AbstractArrow || "arrow".equals(damageId)) {
            return HediffDef.STAB;
        }

        if (attacker instanceof Zombie || attacker instanceof Spider || attacker instanceof CaveSpider || attacker instanceof Wolf || attacker instanceof Bee) {
            return HediffDef.BITE;
        }

        if (attacker instanceof LivingEntity livingAttacker) {
            ItemStack weapon = livingAttacker.getMainHandItem();
            if (weapon.getItem() instanceof SwordItem || weapon.getItem() instanceof AxeItem) {
                return damage >= 5 ? HediffDef.DEEP_CUT : HediffDef.CUT;
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