package com.ext.healthextended.logic;

import com.ext.healthextended.data.BodyPart;
import com.ext.healthextended.data.BodyPartHealth;
import com.ext.healthextended.data.HediffDef;
import com.ext.healthextended.data.HediffInstance;
import com.ext.healthextended.data.PlayerBodyData;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

public final class HediffLogic {

    // Heart Attack suffocation is applied 1/s from PlayerEventHandler.
    // At 2 HP head max, a 0.1 severity step reaches full loss in ~10 seconds i think, unless you die first from somethng else anyways
    private static final float HEART_ATTACK_SUFFOCATION_PER_TICK = 0.1f;
    private static final float STARVATION_GROWTH_PER_TICK = 0.02f;

    private HediffLogic() {
    }

    // 80% chance to pile onto an existing wound rather than starting a fresh one. seems fair.
    public static void applyDamage(BodyPartHealth health, HediffDef def, int damage, RandomSource random) {
        applyDamage(health, def, damage, random, "");
    }

    public static void applyDamage(BodyPartHealth health, HediffDef def, int damage, RandomSource random, String sourceDescription) {
        if (damage <= 0) {
            return;
        }

        if (def.getMaxHpLossAtFullSeverity() <= 0) {
            if (health.getHediff(def) == null) {
                HediffInstance instance = new HediffInstance(def, 1.0f);
                instance.setSourceDescription(sourceDescription);
                health.getHediffs().add(instance);
            }
            cleanup(health);
            return;
        }

        float severityDelta = damage / (float) Math.max(1, def.getMaxHpLossAtFullSeverity());
        List<HediffInstance> existing = health.getHediffs(def);
        List<HediffInstance> mergeable = getMergeableInstances(existing, health.getMaxHp());
        HediffInstance instance;
        if (existing.isEmpty()) {
            instance = new HediffInstance(def, 0.0f);
            health.getHediffs().add(instance);
        } else if (mergeable.isEmpty()) {
            instance = new HediffInstance(def, 0.0f);
            health.getHediffs().add(instance);
        } else if (existing.size() == 1 || random.nextFloat() < 0.8f) {
            instance = mergeable.get(random.nextInt(mergeable.size()));
        } else {
            instance = new HediffInstance(def, 0.0f);
            health.getHediffs().add(instance);
        }

        instance.setSourceDescription(mergeSourceDescriptions(instance.getSourceDescription(), sourceDescription));
        instance.increaseSeverity(severityDelta, health.getMaxHp());
        cleanup(health);
    }

    public static boolean applyHealing(PlayerBodyData bodyData, float healAmount, RandomSource random) {
        if (healAmount <= 0.0f || !hasHealableHediffs(bodyData)) {
            return false;
        }

        boolean healedAnything = false;
        float remaining = healAmount;
        while (remaining > 0.0001f) {
            BodyPart part = selectHealingBodyPart(bodyData, random);
            if (part == null) {
                break;
            }

            BodyPartHealth health = bodyData.getHealth(part);
            HediffInstance target = selectHealingHediff(health, random);
            if (target == null) {
                break;
            }

            float step = Math.min(1.0f, remaining);
            target.reduceSeverity(step * target.getDefinition().getHealPerHealthPoint());
            cleanup(health);
            healedAnything = true;
            remaining -= step;
        }

        return healedAnything;
    }

    public static boolean applyHealingToPart(PlayerBodyData bodyData, BodyPart part, float healAmount, RandomSource random) {
        if (healAmount <= 0.0f) {
            return false;
        }

        BodyPartHealth health = bodyData.getHealth(part);
        if (!hasHealableHediff(health)) {
            return applyHealing(bodyData, healAmount, random);
        }

        boolean healedAnything = false;
        float remaining = healAmount;
        while (remaining > 0.0001f) {
            HediffInstance target = selectHealingHediff(health, random);
            if (target == null) {
                break;
            }

            float step = Math.min(1.0f, remaining);
            target.reduceSeverity(step * target.getDefinition().getHealPerHealthPoint());
            cleanup(health);
            healedAnything = true;
            remaining -= step;
        }

        if (remaining > 0.0001f) {
            healedAnything |= applyHealing(bodyData, remaining, random);
        }

        return healedAnything;
    }

    public static void applyCappedDamage(BodyPartHealth health, HediffDef def, int damage, int maxTotalConsumedHp) {
        if (damage <= 0 || maxTotalConsumedHp <= 0) {
            return;
        }

        int currentConsumed = health.getConsumedHp(def);
        int remainingCapacity = Math.max(0, maxTotalConsumedHp - currentConsumed);
        if (remainingCapacity <= 0) {
            return;
        }

        HediffInstance target = health.getHediff(def);
        if (target == null) {
            target = new HediffInstance(def, 0.0f);
            health.getHediffs().add(target);
        }

        int appliedDamage = Math.min(damage, remainingCapacity);
        float severityDelta = appliedDamage / (float) Math.max(1, def.getMaxHpLossAtFullSeverity());
        target.increaseSeverity(severityDelta, health.getMaxHp());
        cleanup(health);
    }

    public static void applyStatusDerivedDamage(BodyPartHealth health, HediffDef def, int damage, RandomSource random, String sourceEffectId) {
        applyStatusDerivedDamage(health, def, damage, random, sourceEffectId, "");
    }

    public static void applyStatusDerivedDamage(BodyPartHealth health, HediffDef def, int damage, RandomSource random, String sourceEffectId, String sourceDescription) {
        if (damage <= 0) {
            return;
        }

        if (def.getMaxHpLossAtFullSeverity() <= 0) {
            if (health.getHediff(def) == null) {
                HediffInstance instance = new HediffInstance(def, 1.0f);
                instance.setDerivedStatusEffectId(sourceEffectId);
                instance.setSourceDescription(sourceDescription);
                health.getHediffs().add(instance);
            }
            cleanup(health);
            return;
        }

        float severityDelta = damage / (float) Math.max(1, def.getMaxHpLossAtFullSeverity());
        List<HediffInstance> existing = health.getHediffs(def);
        List<HediffInstance> mergeable = getMergeableInstances(existing, health.getMaxHp());
        HediffInstance instance;
        if (existing.isEmpty()) {
            instance = new HediffInstance(def, 0.0f);
            health.getHediffs().add(instance);
        } else if (mergeable.isEmpty()) {
            instance = new HediffInstance(def, 0.0f);
            health.getHediffs().add(instance);
        } else if (existing.size() == 1 || random.nextFloat() < 0.8f) {
            instance = mergeable.get(random.nextInt(mergeable.size()));
        } else {
            instance = new HediffInstance(def, 0.0f);
            health.getHediffs().add(instance);
        }

        instance.setDerivedStatusEffectId(sourceEffectId);
        instance.setSourceDescription(mergeSourceDescriptions(instance.getSourceDescription(), sourceDescription));
        instance.increaseSeverity(severityDelta, health.getMaxHp());
        cleanup(health);
    }

    public static void applyStatusDerivedCappedDamage(BodyPartHealth health, HediffDef def, int damage, int maxTotalConsumedHp, String sourceEffectId) {
        applyStatusDerivedCappedDamage(health, def, damage, maxTotalConsumedHp, sourceEffectId, "");
    }

    public static void applyStatusDerivedCappedDamage(BodyPartHealth health, HediffDef def, int damage, int maxTotalConsumedHp, String sourceEffectId, String sourceDescription) {
        if (damage <= 0 || maxTotalConsumedHp <= 0) {
            return;
        }

        int currentConsumed = health.getConsumedHp(def);
        int remainingCapacity = Math.max(0, maxTotalConsumedHp - currentConsumed);
        if (remainingCapacity <= 0) {
            return;
        }

        HediffInstance target = health.getHediff(def);
        if (target == null) {
            target = new HediffInstance(def, 0.0f);
            health.getHediffs().add(target);
        }

        target.setDerivedStatusEffectId(sourceEffectId);
        target.setSourceDescription(mergeSourceDescriptions(target.getSourceDescription(), sourceDescription));
        int appliedDamage = Math.min(damage, remainingCapacity);
        float severityDelta = appliedDamage / (float) Math.max(1, def.getMaxHpLossAtFullSeverity());
        target.increaseSeverity(severityDelta, health.getMaxHp());
        cleanup(health);
    }

    public static boolean applySpecialSeverity(PlayerBodyData bodyData, BodyPart part, HediffDef def, float severityDelta, RandomSource random) {
        return applySpecialSeverity(bodyData, part, def, severityDelta, random, "");
    }

    public static boolean applySpecialSeverity(PlayerBodyData bodyData, BodyPart part, HediffDef def, float severityDelta, RandomSource random, String sourceDescription) {
        if (severityDelta <= 0.0f) {
            return false;
        }

        BodyPartHealth health = bodyData.getHealth(part);
        int beforeConsumed = health.getConsumedHp();
        applySpecialSeverity(health, def, severityDelta, random, sourceDescription);
        cleanup(health);
        return health.getConsumedHp() != beforeConsumed || health.getHediff(def) != null;
    }

    public static boolean updateSpecialHediffs(PlayerBodyData bodyData, int tickCount) {
        boolean changed = false;

        BodyPartHealth torso = bodyData.getHealth(BodyPart.TORSO);
        changed |= syncBinaryCondition(torso, HediffDef.HEART_ATTACK, torso.isDestroyed(), "Triggered by total torso failure");
        changed |= syncBinaryCondition(bodyData.getHealth(BodyPart.LEFT_ARM), HediffDef.BROKEN, bodyData.getHealth(BodyPart.LEFT_ARM).isDestroyed(), "Caused by left arm failure");
        changed |= syncBinaryCondition(bodyData.getHealth(BodyPart.RIGHT_ARM), HediffDef.BROKEN, bodyData.getHealth(BodyPart.RIGHT_ARM).isDestroyed(), "Caused by right arm failure");
        changed |= syncBinaryCondition(bodyData.getHealth(BodyPart.LEFT_LEG), HediffDef.BROKEN, bodyData.getHealth(BodyPart.LEFT_LEG).isDestroyed(), "Caused by left leg failure");
        changed |= syncBinaryCondition(bodyData.getHealth(BodyPart.RIGHT_LEG), HediffDef.BROKEN, bodyData.getHealth(BodyPart.RIGHT_LEG).isDestroyed(), "Caused by right leg failure");

        return changed;
    }

    public static float getHeartAttackSuffocationPerTick() {
        return HEART_ATTACK_SUFFOCATION_PER_TICK;
    }

    public static boolean updateStarvation(PlayerBodyData bodyData, boolean starving, int tickCount) {
        if (!starving || tickCount % 40 != 0) {
            return false;
        }

        BodyPartHealth torso = bodyData.getHealth(BodyPart.TORSO);
        applySpecialSeverity(torso, HediffDef.STARVATION, STARVATION_GROWTH_PER_TICK, randomForTick(tickCount, torso.getMaxHp() * 31), "Caused by starvation");
        cleanup(torso);
        return true;
    }

    public static boolean hasHealableHediffs(PlayerBodyData bodyData) {
        for (BodyPart part : BodyPart.values()) {
            if (hasHealableHediff(bodyData.getHealth(part))) {
                return true;
            }
        }
        return false;
    }

    private static void cleanup(BodyPartHealth health) {
        health.getHediffs().removeIf(HediffInstance::isHealed);
    }

    private static BodyPart selectHealingBodyPart(PlayerBodyData bodyData, RandomSource random) {
        List<BodyPart> choices = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int totalWeight = 0;

        for (BodyPart part : BodyPart.values()) {
            if (!hasHealableHediff(bodyData.getHealth(part))) {
                continue;
            }

            int weight = switch (part) {
                case TORSO -> 4;
                case HEAD -> 3;
                default -> 1;
            };

            choices.add(part);
            weights.add(weight);
            totalWeight += weight;
        }

        if (totalWeight <= 0) {
            return null;
        }

        int roll = random.nextInt(totalWeight);
        for (int index = 0; index < choices.size(); index++) {
            roll -= weights.get(index);
            if (roll < 0) {
                return choices.get(index);
            }
        }

        return choices.getFirst();
    }

    private static HediffInstance selectHealingHediff(BodyPartHealth health, RandomSource random) {
        List<HediffInstance> healable = new ArrayList<>();
        for (HediffInstance instance : health.getHediffs()) {
            if (instance.getDefinition().healsNaturally() && instance.getDefinition().getHealPerHealthPoint() > 0.0f) {
                healable.add(instance);
            }
        }

        if (healable.isEmpty()) {
            return null;
        }

        int totalWeight = 0;
        for (HediffInstance instance : healable) {
            totalWeight += Math.max(1, Math.round(instance.getSeverity() * 100.0f));
        }

        int roll = random.nextInt(totalWeight);
        for (HediffInstance instance : healable) {
            roll -= Math.max(1, Math.round(instance.getSeverity() * 100.0f));
            if (roll < 0) {
                return instance;
            }
        }

        return healable.getFirst();
    }

    private static void applySpecialSeverity(BodyPartHealth health, HediffDef def, float severityDelta, RandomSource random, String sourceDescription) {
        List<HediffInstance> existing = health.getHediffs(def);
        List<HediffInstance> mergeable = getMergeableInstances(existing, health.getMaxHp());

        HediffInstance target;
        if (existing.isEmpty() || mergeable.isEmpty()) {
            target = new HediffInstance(def, 0.0f);
            health.getHediffs().add(target);
        } else if (existing.size() == 1 || random.nextFloat() < 0.8f) {
            target = mergeable.get(random.nextInt(mergeable.size()));
        } else {
            target = new HediffInstance(def, 0.0f);
            health.getHediffs().add(target);
        }

        target.setSourceDescription(mergeSourceDescriptions(target.getSourceDescription(), sourceDescription));
        target.increaseSeverity(severityDelta, health.getMaxHp());
    }

    private static boolean hasHealableHediff(BodyPartHealth health) {
        for (HediffInstance instance : health.getHediffs()) {
            if (instance.getDefinition().healsNaturally() && instance.getDefinition().getHealPerHealthPoint() > 0.0f) {
                return true;
            }
        }
        return false;
    }

    private static boolean syncBinaryCondition(BodyPartHealth health, HediffDef def, boolean active, String sourceDescription) {
        if (active) {
            if (health.getHediff(def) == null) {
                HediffInstance instance = new HediffInstance(def, 1.0f);
                instance.setSourceDescription(sourceDescription);
                health.getHediffs().add(instance);
                return true;
            }
            return false;
        }

        return health.getHediffs().removeIf(hediff -> hediff.getDefinition() == def);
    }

    private static List<HediffInstance> getMergeableInstances(List<HediffInstance> instances, int partMaxHp) {
        List<HediffInstance> mergeable = new ArrayList<>();
        for (HediffInstance instance : instances) {
            if (!instance.isAtMaxSeverity(partMaxHp)) {
                mergeable.add(instance);
            }
        }
        return mergeable;
    }

    private static RandomSource randomForTick(int tickCount, int salt) {
        return RandomSource.create((((long) tickCount) << 32) ^ salt);
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
}