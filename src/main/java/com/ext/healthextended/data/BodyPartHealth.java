package com.ext.healthextended.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class BodyPartHealth {

    public static final Codec<BodyPartHealth> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("maxHp").forGetter(BodyPartHealth::getMaxHp),
                    HediffInstance.CODEC.listOf().optionalFieldOf("hediffs", List.of()).forGetter(BodyPartHealth::getHediffs)
            ).apply(instance, BodyPartHealth::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BodyPartHealth> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BodyPartHealth decode(RegistryFriendlyByteBuf buf) {
            int maxHp = buf.readVarInt();
            int hediffCount = buf.readVarInt();
            List<HediffInstance> hediffs = new ArrayList<>(hediffCount);
            for (int index = 0; index < hediffCount; index++) {
                hediffs.add(HediffInstance.STREAM_CODEC.decode(buf));
            }
            return new BodyPartHealth(maxHp, hediffs);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, BodyPartHealth value) {
            buf.writeVarInt(value.maxHp);
            buf.writeVarInt(value.hediffs.size());
            for (HediffInstance hediff : value.hediffs) {
                HediffInstance.STREAM_CODEC.encode(buf, hediff);
            }
        }
    };

    private final int maxHp;
    private final List<HediffInstance> hediffs;

    public BodyPartHealth(int maxHp, List<HediffInstance> hediffs) {
        this.maxHp = maxHp;
        this.hediffs = new ArrayList<>(hediffs);
    }

    public BodyPartHealth(int maxHp) {
        this(maxHp, new ArrayList<>());
    }

    public float getHealthPercent() {
        if (maxHp <= 0) return 0f;
        return (float) getCurrentHp() / maxHp;
    }

    public boolean isDamaged() {
        return getCurrentHp() < maxHp;
    }

    public boolean isDestroyed() {
        return getCurrentHp() <= 0;
    }

    public int getCurrentHp() {
        return Math.max(0, maxHp - getConsumedHp());
    }

    public int getConsumedHp() {
        int total = 0;
        for (HediffInstance hediff : hediffs) {
            total += hediff.getConsumedHp(maxHp);
        }
        return Mth.clamp(total, 0, maxHp);
    }

    public int getMaxHp() {
        return maxHp;
    }

    public List<HediffInstance> getHediffs() {
        return hediffs;
    }

    public HediffInstance getHediff(HediffDef def) {
        for (HediffInstance hediff : hediffs) {
            if (hediff.getDefinition() == def) {
                return hediff;
            }
        }
        return null;
    }

    public List<HediffInstance> getHediffs(HediffDef def) {
        List<HediffInstance> matches = new ArrayList<>();
        for (HediffInstance hediff : hediffs) {
            if (hediff.getDefinition() == def) {
                matches.add(hediff);
            }
        }
        return matches;
    }

    public int getConsumedHp(HediffDef def) {
        int total = 0;
        for (HediffInstance hediff : hediffs) {
            if (hediff.getDefinition() == def) {
                total += hediff.getConsumedHp(maxHp);
            }
        }
        return Mth.clamp(total, 0, maxHp);
    }

    public int getMaxConsumableHp(HediffDef def) {
        int total = 0;
        for (HediffInstance hediff : hediffs) {
            if (hediff.getDefinition() == def) {
                total += hediff.getMaxConsumableHp(maxHp);
            }
        }
        return Mth.clamp(total, 0, maxHp);
    }
}
