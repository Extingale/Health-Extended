package com.ext.healthextended.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public class HediffInstance {

    public static final Codec<HediffInstance> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    HediffDef.CODEC.fieldOf("def").forGetter(HediffInstance::getDefinition),
                Codec.FLOAT.fieldOf("severity").forGetter(HediffInstance::getSeverity),
                Codec.STRING.optionalFieldOf("displayName", "").forGetter(HediffInstance::getDisplayNameOverride),
                Codec.STRING.optionalFieldOf("statusEffectId", "").forGetter(HediffInstance::getStatusEffectId),
                Codec.STRING.optionalFieldOf("derivedStatusEffectId", "").forGetter(HediffInstance::getDerivedStatusEffectId),
                Codec.STRING.optionalFieldOf("sourceDescription", "").forGetter(HediffInstance::getSourceDescription),
                Codec.INT.optionalFieldOf("amplifier", 0).forGetter(HediffInstance::getAmplifier),
                Codec.INT.optionalFieldOf("initialDurationTicks", 0).forGetter(HediffInstance::getInitialDurationTicks),
                Codec.INT.optionalFieldOf("remainingDurationTicks", 0).forGetter(HediffInstance::getRemainingDurationTicks),
                Codec.LONG.optionalFieldOf("displayOrder", 0L).forGetter(HediffInstance::getDisplayOrder)
            ).apply(instance, HediffInstance::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, HediffInstance> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HediffInstance decode(RegistryFriendlyByteBuf buf) {
            return new HediffInstance(
                    HediffDef.STREAM_CODEC.decode(buf),
                    buf.readFloat(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarLong()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, HediffInstance value) {
            HediffDef.STREAM_CODEC.encode(buf, value.definition);
            buf.writeFloat(value.severity);
            buf.writeUtf(value.displayNameOverride);
            buf.writeUtf(value.statusEffectId);
            buf.writeUtf(value.derivedStatusEffectId);
            buf.writeUtf(value.sourceDescription);
            buf.writeVarInt(value.amplifier);
            buf.writeVarInt(value.initialDurationTicks);
            buf.writeVarInt(value.remainingDurationTicks);
            buf.writeVarLong(value.displayOrder);
        }
    };

    private final HediffDef definition;
    private float severity;
    private String displayNameOverride;
    private String statusEffectId;
    private String derivedStatusEffectId;
    private String sourceDescription;
    private int amplifier;
    private int initialDurationTicks;
    private int remainingDurationTicks;
    private long displayOrder;

    public HediffInstance(HediffDef definition, float severity) {
        this(definition, severity, "", "", "", 0, 0, 0, 0L);
    }

    public HediffInstance(HediffDef definition, float severity, String displayNameOverride, String statusEffectId, String derivedStatusEffectId, int amplifier, int initialDurationTicks, int remainingDurationTicks, long displayOrder) {
        this(definition, severity, displayNameOverride, statusEffectId, derivedStatusEffectId, "", amplifier, initialDurationTicks, remainingDurationTicks, displayOrder);
    }

    public HediffInstance(HediffDef definition, float severity, String displayNameOverride, String statusEffectId, String derivedStatusEffectId, String sourceDescription, int amplifier, int initialDurationTicks, int remainingDurationTicks, long displayOrder) {
        this.definition = definition;
        this.severity = Mth.clamp(severity, 0.0f, 1.0f);
        this.displayNameOverride = displayNameOverride == null ? "" : displayNameOverride;
        this.statusEffectId = statusEffectId == null ? "" : statusEffectId;
        this.derivedStatusEffectId = derivedStatusEffectId == null ? "" : derivedStatusEffectId;
        this.sourceDescription = sourceDescription == null ? "" : sourceDescription;
        this.amplifier = Math.max(0, amplifier);
        this.initialDurationTicks = Math.max(0, initialDurationTicks);
        this.remainingDurationTicks = Math.max(0, remainingDurationTicks);
        this.displayOrder = Math.max(0L, displayOrder);
    }

    public static HediffInstance statusEffect(String displayName, String statusEffectId, int amplifier, int initialDurationTicks, int remainingDurationTicks, long displayOrder) {
        return new HediffInstance(
                HediffDef.STATUS_EFFECT,
                computeStatusSeverity(initialDurationTicks, remainingDurationTicks),
                displayName,
                statusEffectId,
                "",
                amplifier,
                initialDurationTicks,
                remainingDurationTicks,
                displayOrder
        );
    }

    public HediffDef getDefinition() {
        return definition;
    }

    public float getSeverity() {
        return severity;
    }

    public String getDisplayName() {
        return displayNameOverride.isBlank() ? definition.getDisplayName() : displayNameOverride;
    }

    public String getDisplayNameOverride() {
        return displayNameOverride;
    }

    public boolean isStatusEffect() {
        return !statusEffectId.isBlank();
    }

    public String getStatusEffectId() {
        return statusEffectId;
    }

    public String getDerivedStatusEffectId() {
        return derivedStatusEffectId;
    }

    public String getSourceDescription() {
        return sourceDescription;
    }

    public boolean isVanillaStatusDerived() {
        return isStatusEffect() || !derivedStatusEffectId.isBlank();
    }

    public int getAmplifier() {
        return amplifier;
    }

    public int getInitialDurationTicks() {
        return initialDurationTicks;
    }

    public int getRemainingDurationTicks() {
        return remainingDurationTicks;
    }

    public long getDisplayOrder() {
        return displayOrder;
    }

    public boolean syncStatusEffect(String displayName, String effectId, int amplifier, int initialDurationTicks, int remainingDurationTicks) {
        boolean changed = false;

        String nextDisplayName = displayName == null ? "" : displayName;
        String nextEffectId = effectId == null ? "" : effectId;
        int nextAmplifier = Math.max(0, amplifier);
        int nextInitialDuration = Math.max(0, initialDurationTicks);
        int nextRemainingDuration = Math.max(0, remainingDurationTicks);
        float nextSeverity = computeStatusSeverity(nextInitialDuration, nextRemainingDuration);

        if (!displayNameOverride.equals(nextDisplayName)) {
            displayNameOverride = nextDisplayName;
            changed = true;
        }
        if (!statusEffectId.equals(nextEffectId)) {
            statusEffectId = nextEffectId;
            changed = true;
        }
        if (this.amplifier != nextAmplifier) {
            this.amplifier = nextAmplifier;
            changed = true;
        }
        if (this.initialDurationTicks != nextInitialDuration) {
            this.initialDurationTicks = nextInitialDuration;
            changed = true;
        }
        if (this.remainingDurationTicks != nextRemainingDuration) {
            this.remainingDurationTicks = nextRemainingDuration;
            changed = true;
        }
        if (Math.abs(this.severity - nextSeverity) > 0.0001f) {
            this.severity = nextSeverity;
            changed = true;
        }

        return changed;
    }

    public boolean setDerivedStatusEffectId(String derivedStatusEffectId) {
        String nextDerivedStatusEffectId = derivedStatusEffectId == null ? "" : derivedStatusEffectId;
        if (this.derivedStatusEffectId.equals(nextDerivedStatusEffectId)) {
            return false;
        }

        this.derivedStatusEffectId = nextDerivedStatusEffectId;
        return true;
    }

    public boolean setSourceDescription(String sourceDescription) {
        String nextSourceDescription = sourceDescription == null ? "" : sourceDescription;
        if (this.sourceDescription.equals(nextSourceDescription)) {
            return false;
        }

        this.sourceDescription = nextSourceDescription;
        return true;
    }

    public void increaseSeverity(float amount, int partMaxHp) {
        severity = Mth.clamp(severity + Math.max(0.0f, amount), 0.0f, getMaxSeverity(partMaxHp));
    }

    public void reduceSeverity(float amount) {
        severity = Math.max(0.0f, severity - Math.max(0.0f, amount));
    }

    public boolean isHealed() {
        if (isStatusEffect()) {
            return remainingDurationTicks <= 0;
        }
        return severity <= 0.0001f;
    }

    public boolean isAtMaxSeverity(int partMaxHp) {
        return severity >= getMaxSeverity(partMaxHp) - 0.0001f;
    }

    public int getConsumedHp(int partMaxHp) {
        if (severity <= 0.0f || partMaxHp <= 0 || definition.getMaxHpLossAtFullSeverity() <= 0) {
            return 0;
        }

        int consumed;
        if (definition == HediffDef.SUFFOCATION) {
            consumed = Mth.floor(partMaxHp * severity);
        } else {
            consumed = Mth.ceil(definition.getMaxHpLossAtFullSeverity() * severity);
            if (consumed <= 0) {
                consumed = 1;
            }
        }
        int maxLoss = definition.canDestroyPart() ? partMaxHp : Math.max(0, partMaxHp - 1);
        return Mth.clamp(consumed, 0, maxLoss);
    }

    public int getMaxConsumableHp(int partMaxHp) {
        if (partMaxHp <= 0 || definition.getMaxHpLossAtFullSeverity() <= 0) {
            return 0;
        }

        if (definition == HediffDef.SUFFOCATION) {
            return partMaxHp;
        }

        int maxLoss = definition.canDestroyPart() ? partMaxHp : Math.max(0, partMaxHp - 1);
        return Mth.clamp(definition.getMaxHpLossAtFullSeverity(), 0, maxLoss);
    }

    private float getMaxSeverity(int partMaxHp) {
        if (definition.getMaxHpLossAtFullSeverity() <= 0) {
            return 1.0f;
        }
        if (definition.canDestroyPart() || partMaxHp <= 1) {
            return 1.0f;
        }
        return Math.min(1.0f, (float) (partMaxHp - 1) / definition.getMaxHpLossAtFullSeverity());
    }

    private static float computeStatusSeverity(int initialDurationTicks, int remainingDurationTicks) {
        if (initialDurationTicks <= 0 || remainingDurationTicks <= 0) {
            return 0.0f;
        }
        return Mth.clamp(remainingDurationTicks / (float) initialDurationTicks, 0.0f, 1.0f);
    }
}