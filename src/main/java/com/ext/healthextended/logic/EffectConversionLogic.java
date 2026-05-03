package com.ext.healthextended.logic;

import com.ext.healthextended.data.BodyPart;
import com.ext.healthextended.data.BodyPartHealth;
import com.ext.healthextended.data.HediffDef;
import com.ext.healthextended.data.HediffInstance;
import com.ext.healthextended.data.PlayerBodyData;
import com.ext.healthextended.HealthExtended;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class EffectConversionLogic {

    public static final String POISON_EFFECT_ID = "minecraft:poison";
    public static final String WITHER_EFFECT_ID = "minecraft:wither";
    public static final String REGENERATION_EFFECT_ID = "minecraft:regeneration";
    public static final String SPEED_EFFECT_ID = "minecraft:speed";
    public static final String HASTE_EFFECT_ID = "minecraft:haste";
    public static final String STRENGTH_EFFECT_ID = "minecraft:strength";
    public static final String SLOWNESS_EFFECT_ID = "minecraft:slowness";
    public static final String MINING_FATIGUE_EFFECT_ID = "minecraft:mining_fatigue";
    public static final String WEAKNESS_EFFECT_ID = "minecraft:weakness";
    public static final String BLINDNESS_EFFECT_ID = "minecraft:blindness";
    public static final String DARKNESS_EFFECT_ID = "minecraft:darkness";
    public static final String NAUSEA_EFFECT_ID = "minecraft:nausea";
    public static final String HUNGER_EFFECT_ID = "minecraft:hunger";
    public static final String INFESTED_EFFECT_ID = "minecraft:infested";
    public static final String OOZING_EFFECT_ID = "minecraft:oozing";
    public static final String WEAVING_EFFECT_ID = "minecraft:weaving";
    public static final String WIND_CHARGED_EFFECT_ID = "minecraft:wind_charged";
    public static final String BAD_OMEN_EFFECT_ID = "minecraft:bad_omen";
    public static final String RAID_OMEN_EFFECT_ID = "minecraft:raid_omen";
    public static final String TRIAL_OMEN_EFFECT_ID = "minecraft:trial_omen";
    private static final String CONDITION_BROKEN_STATUS_SOURCE_ID = HealthExtended.MODID + ":condition/broken";
    private static final int POISON_PART_CAP_HP = 1;
    private static final float MIN_INSTANT_HEAL_AMOUNT = 4.0f;
    private static final ResourceLocation SPEED_OVERRIDE_ID = ResourceLocation.fromNamespaceAndPath(HealthExtended.MODID, "status_override/speed");
    private static final ResourceLocation SLOWNESS_OVERRIDE_ID = ResourceLocation.fromNamespaceAndPath(HealthExtended.MODID, "status_override/slowness");
    private static final ResourceLocation STRENGTH_OVERRIDE_ID = ResourceLocation.fromNamespaceAndPath(HealthExtended.MODID, "status_override/strength");
    private static final ResourceLocation WEAKNESS_OVERRIDE_ID = ResourceLocation.fromNamespaceAndPath(HealthExtended.MODID, "status_override/weakness");
    private static final ResourceLocation MINING_FATIGUE_OVERRIDE_ID = ResourceLocation.fromNamespaceAndPath(HealthExtended.MODID, "status_override/mining_fatigue");

    private EffectConversionLogic() {
    }

    public static boolean syncVanillaStatusEffects(PlayerBodyData bodyData, Player player) {
        boolean changed = false;
        Set<String> activeEffectIds = new HashSet<>();

        for (MobEffectInstance effectInstance : player.getActiveEffects()) {
            String effectId = getEffectId(effectInstance);
            if (effectId.isEmpty()) {
                continue;
            }

            activeEffectIds.add(effectId);
            if (shouldIgnoreMirroringForConditionDrivenEffect(bodyData, effectId)) {
                changed |= removeMirroredStatusesForEffect(bodyData, effectId);
                continue;
            }
            StatusLocation existing = findMirroredStatus(bodyData, effectId);
            if (existing == null) {
                BodyPart targetPart = chooseTargetPart(player, effectInstance, null, null);
                HediffInstance hediff = HediffInstance.statusEffect(
                        getDisplayName(effectInstance),
                        effectId,
                        effectInstance.getAmplifier(),
                        effectInstance.getDuration(),
                        effectInstance.getDuration(),
                        player.tickCount
                );
                    hediff.setSourceDescription(buildStatusSourceDescription(effectInstance, null));
                bodyData.getHealth(targetPart).getHediffs().add(hediff);
                changed = true;
                continue;
            }

            changed |= existing.instance().syncStatusEffect(
                    getDisplayName(effectInstance),
                    effectId,
                    effectInstance.getAmplifier(),
                    Math.max(existing.instance().getInitialDurationTicks(), effectInstance.getDuration()),
                    effectInstance.getDuration()
            );
        }

        for (BodyPart part : BodyPart.values()) {
            BodyPartHealth health = bodyData.getHealth(part);
            changed |= health.getHediffs().removeIf(hediff -> hediff.isStatusEffect() && !activeEffectIds.contains(hediff.getStatusEffectId()));
        }

        return changed;
    }

    public static boolean syncAddedStatusEffect(PlayerBodyData bodyData, Player player, MobEffectInstance effectInstance, Entity source, BodyPart impactPart) {
        if (effectInstance == null || effectInstance.getEffect().value().isInstantenous()) {
            return false;
        }

        String effectId = getEffectId(effectInstance);
        if (effectId.isEmpty()) {
            return false;
        }

        if (shouldIgnoreMirroringForConditionDrivenEffect(bodyData, effectId)) {
            return removeMirroredStatusesForEffect(bodyData, effectId);
        }

        StatusLocation existing = findMirroredStatus(bodyData, effectId);
        if (existing == null) {
            BodyPart targetPart = chooseTargetPart(player, effectInstance, source, impactPart);
            HediffInstance hediff = HediffInstance.statusEffect(
                    getDisplayName(effectInstance),
                    effectId,
                    effectInstance.getAmplifier(),
                    effectInstance.getDuration(),
                    effectInstance.getDuration(),
                    player.tickCount
            );
            hediff.setSourceDescription(buildStatusSourceDescription(effectInstance, source));
            bodyData.getHealth(targetPart).getHediffs().add(hediff);
            return true;
        }

        existing.instance().setSourceDescription(mergeSourceDescriptions(existing.instance().getSourceDescription(), buildStatusSourceDescription(effectInstance, source)));

        return existing.instance().syncStatusEffect(
                getDisplayName(effectInstance),
                effectId,
                effectInstance.getAmplifier(),
                Math.max(existing.instance().getInitialDurationTicks(), effectInstance.getDuration()),
                effectInstance.getDuration()
        );
    }

    public static boolean handleSpecialPlayerEffectDamage(PlayerBodyData bodyData, Player player, DamageSource source, float vanillaDamageAmount, BodyPart hintedPart) {
        if (isWitherEffectTick(player, source)) {
            StatusLocation status = findStatus(bodyData, WITHER_EFFECT_ID);
            BodyPart targetPart = status == null ? BodyPart.TORSO : status.part();
            HediffLogic.applyStatusDerivedDamage(
                    bodyData.getHealth(targetPart),
                    HediffDef.WITHERED,
                    Math.max(1, Mth.ceil(vanillaDamageAmount)),
                    player.getRandom(),
                    WITHER_EFFECT_ID,
                    status == null ? "Caused by wither" : status.instance().getSourceDescription()
            );
            return true;
        }

        if (isPoisonEffectTick(player, source)) {
            StatusLocation status = findStatus(bodyData, POISON_EFFECT_ID);
            BodyPart targetPart = choosePoisonTargetPart(bodyData, status == null ? BodyPart.TORSO : status.part());
            if (targetPart != null) {
                HediffLogic.applyStatusDerivedCappedDamage(
                        bodyData.getHealth(targetPart),
                        HediffDef.POISONED,
                        Math.max(1, Mth.ceil(vanillaDamageAmount)),
                        POISON_PART_CAP_HP,
                        POISON_EFFECT_ID,
                        status == null ? "Caused by poison" : status.instance().getSourceDescription()
                );
            }
            return true;
        }

        if (isInstantMagicDamage(source)) {
            BodyPart targetPart = hintedPart != null
                    ? hintedPart
                    : chooseDirectEffectTargetPart(player, source.getDirectEntity(), source.getEntity(), true);
            HediffLogic.applyDamage(
                    bodyData.getHealth(targetPart),
                    HediffDef.MAGIC_WOUND,
                    Math.max(1, Mth.ceil(vanillaDamageAmount)),
                    player.getRandom(),
                    // vanilla uses half-hearts, so we round to the nearest half
                    LocationalHealthLogic.buildDamageSourceDescription(source, HediffDef.MAGIC_WOUND)
            );
            return true;
        }

        return false;
    }

    public static boolean handleSpecialPlayerHealing(PlayerBodyData bodyData, Player player, float healAmount) {
        MobEffectInstance regeneration = player.getEffect(MobEffects.REGENERATION);
        if (healAmount <= 0.0f) {
            return false;
        }

        if (regeneration != null && regeneration.getEffect().value().shouldApplyEffectTickThisTick(regeneration.getDuration(), regeneration.getAmplifier())) {
            StatusLocation status = findStatus(bodyData, REGENERATION_EFFECT_ID);
            BodyPart targetPart = status == null ? BodyPart.TORSO : status.part();
            return HediffLogic.applyHealingToPart(bodyData, targetPart, healAmount, player.getRandom());
        }

        if (healAmount >= MIN_INSTANT_HEAL_AMOUNT) {
            return HediffLogic.applyHealingToPart(bodyData, BodyPart.TORSO, healAmount, player.getRandom());
        }

        return false;
    }

    public static boolean isPoisonStatusEffect(HediffInstance hediff) {
        return hediff.isStatusEffect() && POISON_EFFECT_ID.equals(hediff.getStatusEffectId());
    }

    public static boolean isWitherStatusEffect(HediffInstance hediff) {
        return hediff.isStatusEffect() && WITHER_EFFECT_ID.equals(hediff.getStatusEffectId());
    }

    public static boolean removeMilkCurableHediffs(PlayerBodyData bodyData) {
        boolean changed = false;
        for (BodyPart part : BodyPart.values()) {
            changed |= bodyData.getHealth(part).getHediffs().removeIf(HediffInstance::isVanillaStatusDerived);
        }
        return changed;
    }

    public static void applyConditionDrivenVanillaEffects(Player player, PlayerBodyData bodyData) {
        applyConditionDrivenVanillaEffect(player, bodyData, SLOWNESS_EFFECT_ID, MobEffects.MOVEMENT_SLOWDOWN);
        applyConditionDrivenVanillaEffect(player, bodyData, WEAKNESS_EFFECT_ID, MobEffects.WEAKNESS);
        applyConditionDrivenVanillaEffect(player, bodyData, MINING_FATIGUE_EFFECT_ID, MobEffects.DIG_SLOWDOWN);
    }

    public static void applyLocationalStatusOverrides(PlayerBodyData bodyData, Player player) {
        applyAttributeOverride(player, bodyData, MobEffects.MOVEMENT_SPEED, SPEED_EFFECT_ID, getLegEffectFraction(bodyData, SPEED_EFFECT_ID), SPEED_OVERRIDE_ID);
        applyAttributeOverride(player, bodyData, MobEffects.MOVEMENT_SLOWDOWN, SLOWNESS_EFFECT_ID, getLegEffectFraction(bodyData, SLOWNESS_EFFECT_ID), SLOWNESS_OVERRIDE_ID);
        applyAttributeOverride(player, bodyData, MobEffects.DAMAGE_BOOST, STRENGTH_EFFECT_ID, getMainArmEffectFraction(bodyData, STRENGTH_EFFECT_ID, player.getMainArm()), STRENGTH_OVERRIDE_ID);
        applyAttributeOverride(player, bodyData, MobEffects.WEAKNESS, WEAKNESS_EFFECT_ID, getMainArmEffectFraction(bodyData, WEAKNESS_EFFECT_ID, player.getMainArm()), WEAKNESS_OVERRIDE_ID);
        applyAttributeOverride(player, bodyData, MobEffects.DIG_SLOWDOWN, MINING_FATIGUE_EFFECT_ID, getMainArmEffectFraction(bodyData, MINING_FATIGUE_EFFECT_ID, player.getMainArm()), MINING_FATIGUE_OVERRIDE_ID);
    }

    public static boolean syncConditionDrivenStatuses(PlayerBodyData bodyData) {
        boolean changed = false;
        changed |= syncBrokenLimbStatuses(bodyData, BodyPart.LEFT_LEG, true, false);
        changed |= syncBrokenLimbStatuses(bodyData, BodyPart.RIGHT_LEG, true, false);
        changed |= syncBrokenLimbStatuses(bodyData, BodyPart.LEFT_ARM, false, true);
        changed |= syncBrokenLimbStatuses(bodyData, BodyPart.RIGHT_ARM, false, true);
        return changed;
    }

    public static void applyBreakSpeedStatusOverrides(PlayerBodyData bodyData, PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        MobEffectInstance haste = player.getEffect(MobEffects.DIG_SPEED);
        MobEffectInstance miningFatigue = player.getEffect(MobEffects.DIG_SLOWDOWN);
        int hasteAmplifier = getEffectiveAmplifier(bodyData, haste, HASTE_EFFECT_ID);
        int miningFatigueAmplifier = getEffectiveAmplifier(bodyData, miningFatigue, MINING_FATIGUE_EFFECT_ID);
        if (hasteAmplifier < 0 && miningFatigueAmplifier < 0) {
            return;
        }

        float hasteFraction = (float) getMainArmEffectFraction(bodyData, HASTE_EFFECT_ID, player.getMainArm());
        float miningFatigueFraction = (float) getMainArmEffectFraction(bodyData, MINING_FATIGUE_EFFECT_ID, player.getMainArm());
        if ((hasteAmplifier < 0 || hasteFraction >= 0.999f) && (miningFatigueAmplifier < 0 || miningFatigueFraction >= 0.999f)) {
            return;
        }

        float vanillaMultiplier = 1.0f;
        float desiredMultiplier = 1.0f;
        if (hasteAmplifier >= 0) {
            float hasteMultiplier = getHasteMultiplier(hasteAmplifier);
            vanillaMultiplier *= hasteMultiplier;
            desiredMultiplier *= 1.0f + hasteFraction * (hasteMultiplier - 1.0f);
        }
        if (miningFatigueAmplifier >= 0) {
            float fatigueMultiplier = getMiningFatigueMultiplier(miningFatigueAmplifier);
            vanillaMultiplier *= fatigueMultiplier;
            desiredMultiplier *= 1.0f - miningFatigueFraction * (1.0f - fatigueMultiplier);
        }

        if (vanillaMultiplier <= 0.0f) {
            return;
        }

        float adjustedSpeed = event.getNewSpeed() / vanillaMultiplier * desiredMultiplier;
        event.setNewSpeed(adjustedSpeed);
    }

    private static StatusLocation findStatus(PlayerBodyData bodyData, String effectId) {
        for (BodyPart part : BodyPart.values()) {
            for (HediffInstance hediff : bodyData.getHealth(part).getHediffs()) {
                if (hediff.isStatusEffect() && hediff.getStatusEffectId().equals(effectId)) {
                    return new StatusLocation(part, hediff);
                }
            }
        }
        return null;
    }

    private static StatusLocation findMirroredStatus(PlayerBodyData bodyData, String effectId) {
        for (BodyPart part : BodyPart.values()) {
            for (HediffInstance hediff : bodyData.getHealth(part).getHediffs()) {
                if (hediff.isStatusEffect() && effectId.equals(hediff.getStatusEffectId()) && !isConditionDerivedStatus(hediff)) {
                    return new StatusLocation(part, hediff);
                }
            }
        }
        return null;
    }

    private static BodyPart chooseTargetPart(Player player, MobEffectInstance effectInstance, Entity source, BodyPart impactPart) {
        String effectId = getEffectId(effectInstance);
        if (SPEED_EFFECT_ID.equals(effectId) || SLOWNESS_EFFECT_ID.equals(effectId)) {
            return chooseLimbTargetPart(impactPart, true, player);
        }
        if (HASTE_EFFECT_ID.equals(effectId) || MINING_FATIGUE_EFFECT_ID.equals(effectId) || STRENGTH_EFFECT_ID.equals(effectId) || WEAKNESS_EFFECT_ID.equals(effectId)) {
            return chooseArmTargetPart(impactPart, player);
        }
        if (BLINDNESS_EFFECT_ID.equals(effectId) || DARKNESS_EFFECT_ID.equals(effectId) || NAUSEA_EFFECT_ID.equals(effectId)) {
            return BodyPart.HEAD;
        }
        if (HUNGER_EFFECT_ID.equals(effectId)) {
            return BodyPart.TORSO;
        }
        if (isSystemicTorsoEffect(effectId)) {
            return BodyPart.TORSO;
        }

        if (impactPart != null) {
            return impactPart;
        }

        if (source != null) {
            return LocationalHealthLogic.chooseStatusTargetPart(player, source, isHarmful(effectInstance));
        }

        return LocationalHealthLogic.chooseEnvironmentStatusTargetPart(player, isHarmful(effectInstance));
    }

    private static BodyPart choosePoisonTargetPart(PlayerBodyData bodyData, BodyPart anchoredPart) {
        BodyPart[] order = {
                anchoredPart,
                BodyPart.TORSO,
                BodyPart.HEAD,
                BodyPart.LEFT_ARM,
                BodyPart.RIGHT_ARM,
                BodyPart.LEFT_LEG,
                BodyPart.RIGHT_LEG
        };

        Set<BodyPart> seen = new HashSet<>();
        for (BodyPart part : order) {
            if (!seen.add(part)) {
                continue;
            }
            if (bodyData.getHealth(part).getConsumedHp(HediffDef.POISONED) < POISON_PART_CAP_HP) {
                return part;
            }
        }

        return null;
    }

    private static boolean isPoisonEffectTick(Player player, DamageSource source) {
        if (!hasVanillaEffect(player, MobEffects.POISON, POISON_EFFECT_ID)) {
            return false;
        }

        String damageId = getDamageId(source);
        return "magic".equals(damageId) && isEffectTickSource(source);
    }

    private static boolean isWitherEffectTick(Player player, DamageSource source) {
        if (!hasVanillaEffect(player, MobEffects.WITHER, WITHER_EFFECT_ID)) {
            return false;
        }

        return "wither".equals(getDamageId(source)) && isEffectTickSource(source);
    }

    private static boolean isInstantMagicDamage(DamageSource source) {
        String damageId = getDamageId(source);
        if (!("magic".equals(damageId) || "indirectmagic".equals(damageId))) {
            return false;
        }

        return !isEffectTickSource(source) && (source.getDirectEntity() != null || source.getEntity() != null);
    }

    private static BodyPart chooseDirectEffectTargetPart(Player player, Entity directEntity, Entity sourceEntity, boolean harmful) {
        if (directEntity != null) {
            return LocationalHealthLogic.chooseStatusTargetPart(player, directEntity, harmful);
        }
        if (sourceEntity != null) {
            return LocationalHealthLogic.chooseStatusTargetPart(player, sourceEntity, harmful);
        }
        return LocationalHealthLogic.chooseEnvironmentStatusTargetPart(player, harmful);
    }

    private static boolean isHarmful(MobEffectInstance effectInstance) {
        return effectInstance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL;
    }

    private static BodyPart chooseLimbTargetPart(BodyPart impactPart, boolean legsOnly, Player player) {
        if (impactPart == BodyPart.LEFT_LEG || impactPart == BodyPart.RIGHT_LEG) {
            return impactPart;
        }
        if (!legsOnly && (impactPart == BodyPart.LEFT_ARM || impactPart == BodyPart.RIGHT_ARM)) {
            return impactPart;
        }
        return player.getRandom().nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
    }

    private static BodyPart chooseArmTargetPart(BodyPart impactPart, Player player) {
        if (impactPart == BodyPart.LEFT_ARM || impactPart == BodyPart.RIGHT_ARM) {
            return impactPart;
        }
        return player.getRandom().nextBoolean() ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM;
    }

    private static float getMiningFatigueMultiplier(int amplifier) {
        return switch (amplifier) {
            case 0 -> 0.3F;
            case 1 -> 0.09F;
            case 2 -> 0.0027F;
            default -> 8.1E-4F;
        };
    }

    private static float getHasteMultiplier(int amplifier) {
        return 1.0f + 0.2f * (amplifier + 1);
    }

    private static double getLegEffectFraction(PlayerBodyData bodyData, String effectId) {
        // Broken-leg slowness should be full-strength even if only one leg is broken.
        if (SLOWNESS_EFFECT_ID.equals(effectId)
                && (hasConditionStatusOnPart(bodyData, effectId, BodyPart.LEFT_LEG)
                || hasConditionStatusOnPart(bodyData, effectId, BodyPart.RIGHT_LEG))) {
            return 1.0;
        }

        double fraction = 0.0;
        if (hasStatusOnPart(bodyData, effectId, BodyPart.LEFT_LEG)) {
            fraction += 0.5;
        }
        if (hasStatusOnPart(bodyData, effectId, BodyPart.RIGHT_LEG)) {
            fraction += 0.5;
        }
        return Math.min(1.0, fraction);
    }

    private static double getMainArmEffectFraction(PlayerBodyData bodyData, String effectId, HumanoidArm mainArm) {
        BodyPart mainArmPart = mainArm == HumanoidArm.LEFT ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM;
        return hasStatusOnPart(bodyData, effectId, mainArmPart) ? 1.0 : 0.0;
    }

    private static boolean hasStatusOnPart(PlayerBodyData bodyData, String effectId, BodyPart part) {
        for (HediffInstance hediff : bodyData.getHealth(part).getHediffs()) {
            if (hediff.isStatusEffect() && effectId.equals(hediff.getStatusEffectId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasConditionStatusOnPart(PlayerBodyData bodyData, String effectId, BodyPart part) {
        for (HediffInstance hediff : bodyData.getHealth(part).getHediffs()) {
            if (hediff.isStatusEffect()
                    && effectId.equals(hediff.getStatusEffectId())
                    && isConditionDerivedStatus(hediff)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldIgnoreMirroringForConditionDrivenEffect(PlayerBodyData bodyData, String effectId) {
        if (!SLOWNESS_EFFECT_ID.equals(effectId)
                && !WEAKNESS_EFFECT_ID.equals(effectId)
                && !MINING_FATIGUE_EFFECT_ID.equals(effectId)) {
            return false;
        }

        for (BodyPart part : BodyPart.values()) {
            if (hasConditionStatusOnPart(bodyData, effectId, part)) {
                return true;
            }
        }
        return false;
    }

    private static boolean removeMirroredStatusesForEffect(PlayerBodyData bodyData, String effectId) {
        boolean changed = false;
        for (BodyPart part : BodyPart.values()) {
            BodyPartHealth health = bodyData.getHealth(part);
            changed |= health.getHediffs().removeIf(hediff ->
                    hediff.isStatusEffect()
                            && effectId.equals(hediff.getStatusEffectId())
                            && !isConditionDerivedStatus(hediff)
            );
        }
        return changed;
    }

    private static boolean isSystemicTorsoEffect(String effectId) {
        return INFESTED_EFFECT_ID.equals(effectId)
                || OOZING_EFFECT_ID.equals(effectId)
                || WEAVING_EFFECT_ID.equals(effectId)
                || WIND_CHARGED_EFFECT_ID.equals(effectId)
                || BAD_OMEN_EFFECT_ID.equals(effectId)
                || RAID_OMEN_EFFECT_ID.equals(effectId)
                || TRIAL_OMEN_EFFECT_ID.equals(effectId);
    }

    private static void applyAttributeOverride(Player player, PlayerBodyData bodyData, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, String effectId, double desiredFraction, ResourceLocation overrideId) {
        MobEffectInstance instance = player.getEffect(effect);
        int amplifier = getEffectiveAmplifier(bodyData, instance, effectId);
        Map<net.minecraft.core.Holder<Attribute>, AttributeModifier> vanillaModifiers = new HashMap<>();
        effect.value().createModifiers(Math.max(0, amplifier), vanillaModifiers::put);

        for (Map.Entry<net.minecraft.core.Holder<Attribute>, AttributeModifier> entry : vanillaModifiers.entrySet()) {
            AttributeInstance attributeInstance = player.getAttributes().getInstance(entry.getKey());
            if (attributeInstance == null) {
                continue;
            }

            ResourceLocation modifierId = buildOverrideId(overrideId, entry.getKey());
            if (amplifier < 0 || desiredFraction >= 0.999) {
                attributeInstance.removeModifier(modifierId);
                continue;
            }

            AttributeModifier vanillaModifier = entry.getValue();
            double cancelAmount = -vanillaModifier.amount() * (1.0 - desiredFraction);
            if (Math.abs(cancelAmount) <= 0.000001) {
                attributeInstance.removeModifier(modifierId);
                continue;
            }

            attributeInstance.addOrUpdateTransientModifier(new AttributeModifier(modifierId, cancelAmount, vanillaModifier.operation()));
        }
    }

    private static ResourceLocation buildOverrideId(ResourceLocation baseId, net.minecraft.core.Holder<Attribute> attribute) {
        ResourceKey<Attribute> key = attribute.unwrapKey().orElseThrow();
        String path = key.location().getPath().replace('/', '_');
        return ResourceLocation.fromNamespaceAndPath(baseId.getNamespace(), baseId.getPath() + "/" + path);
    }

    private static boolean hasVanillaEffect(Player player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, String effectId) {
        MobEffectInstance instance = player.getEffect(effect);
        if (instance != null) {
            return true;
        }

        return findMirroredStatus(player.getData(com.ext.healthextended.registry.ModAttachmentTypes.PLAYER_BODY_DATA), effectId) != null;
    }

    private static int getEffectiveAmplifier(PlayerBodyData bodyData, MobEffectInstance instance, String effectId) {
        int amplifier = instance == null ? -1 : instance.getAmplifier();
        for (BodyPart part : BodyPart.values()) {
            for (HediffInstance hediff : bodyData.getHealth(part).getHediffs()) {
                if (hediff.isStatusEffect() && effectId.equals(hediff.getStatusEffectId())) {
                    amplifier = Math.max(amplifier, hediff.getAmplifier());
                }
            }
        }
        return amplifier;
    }

    private static boolean syncBrokenLimbStatuses(PlayerBodyData bodyData, BodyPart part, boolean addSlowness, boolean addArmPenalties) {
        BodyPartHealth health = bodyData.getHealth(part);
        boolean broken = health.getHediff(HediffDef.BROKEN) != null;
        boolean changed = false;
        changed |= syncConditionStatus(health, broken && addSlowness, SLOWNESS_EFFECT_ID, "Slowness", 0, "Caused by Broken Limb", 0L);
        changed |= syncConditionStatus(health, broken && addArmPenalties, WEAKNESS_EFFECT_ID, "Weakness", 0, "Caused by Broken Limb", 0L);
        changed |= syncConditionStatus(health, broken && addArmPenalties, MINING_FATIGUE_EFFECT_ID, "Mining Fatigue", 0, "Caused by Broken Limb", 1L);
        return changed;
    }

    private static boolean syncConditionStatus(BodyPartHealth health, boolean shouldExist, String effectId, String displayName, int amplifier, String sourceDescription, long displayOrder) {
        HediffInstance existing = null;
        for (HediffInstance hediff : health.getHediffs()) {
            if (hediff.isStatusEffect() && effectId.equals(hediff.getStatusEffectId()) && isConditionDerivedStatus(hediff)) {
                existing = hediff;
                break;
            }
        }

        if (!shouldExist) {
            return existing != null && health.getHediffs().remove(existing);
        }

        if (existing != null) {
            boolean changed = false;
            changed |= existing.setSourceDescription(sourceDescription);
            changed |= existing.setDerivedStatusEffectId(CONDITION_BROKEN_STATUS_SOURCE_ID);
            return changed;
        }

        HediffInstance created = HediffInstance.statusEffect(displayName, effectId, amplifier, Integer.MAX_VALUE, Integer.MAX_VALUE, displayOrder);
        created.setDerivedStatusEffectId(CONDITION_BROKEN_STATUS_SOURCE_ID);
        created.setSourceDescription(sourceDescription);
        health.getHediffs().add(created);
        return true;
    }

    private static boolean isConditionDerivedStatus(HediffInstance hediff) {
        return CONDITION_BROKEN_STATUS_SOURCE_ID.equals(hediff.getDerivedStatusEffectId());
    }

    private static void applyConditionDrivenVanillaEffect(Player player, PlayerBodyData bodyData, String effectId, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        int amplifier = -1;
        for (BodyPart part : BodyPart.values()) {
            for (HediffInstance hediff : bodyData.getHealth(part).getHediffs()) {
                if (hediff.isStatusEffect()
                        && effectId.equals(hediff.getStatusEffectId())
                        && isConditionDerivedStatus(hediff)) {
                    amplifier = Math.max(amplifier, hediff.getAmplifier());
                }
            }
        }

        if (amplifier < 0) {
            return;
        }

        // Keep a short, continuously refreshed vanilla effect so the top-right icon
        // and vanilla mechanics are active without permanently owning the effect slot.
        player.addEffect(new MobEffectInstance(effect, 40, amplifier, false, false, true));
    }

    private static boolean isEffectTickSource(DamageSource source) {
        Entity sourceEntity = source.getEntity();
        Entity directEntity = source.getDirectEntity();
        return sourceEntity == null && directEntity == null;
    }

    private static String getDamageId(DamageSource source) {
        return source.getMsgId().toLowerCase(Locale.ROOT).replace("_", "");
    }

    private static String getEffectId(MobEffectInstance effectInstance) {
        ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effectInstance.getEffect().value());
        return key == null ? "" : key.toString();
    }

    private static String getDisplayName(MobEffectInstance effectInstance) {
        return Component.translatable(effectInstance.getDescriptionId()).getString();
    }

    private static String buildStatusSourceDescription(MobEffectInstance effectInstance, Entity source) {
        if (source != null) {
            String name = source.getName().getString();
            if (!name.isBlank()) {
                return (isHarmful(effectInstance) ? "Inflicted by " : "Granted by ") + name;
            }
        }

        String effectId = getEffectId(effectInstance);
        if (effectId.isEmpty()) {
            return "";
        }

        return switch (effectId) {
            case BAD_OMEN_EFFECT_ID -> "Carried as a bad omen";
            case RAID_OMEN_EFFECT_ID -> "Carried as a raid omen";
            case TRIAL_OMEN_EFFECT_ID -> "Carried as a trial omen";
            case INFESTED_EFFECT_ID -> "Infested by unstable energy";
            case OOZING_EFFECT_ID -> "Oozing with unstable energy";
            case WEAVING_EFFECT_ID -> "Wrapped in unstable threads";
            case WIND_CHARGED_EFFECT_ID -> "Charged with unstable wind";
            default -> "";
        };
    }

    private static String mergeSourceDescriptions(String existing, String next) {
        String current = existing == null ? "" : existing.trim();
        String incoming = next == null ? "" : next.trim();
        if (incoming.isEmpty()) {
            return current;
        }
        if (current.isEmpty()) {
            return incoming;
        }
        for (String line : current.split("\\n")) {
            if (line.equals(incoming)) {
                return current;
            }
        }
        return current + "\n" + incoming;
    }

    private record StatusLocation(BodyPart part, HediffInstance instance) {
    }
}