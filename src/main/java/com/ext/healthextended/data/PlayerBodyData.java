package com.ext.healthextended.data;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.EnumMap;
import java.util.Map;

public class PlayerBodyData {

    public static final Codec<PlayerBodyData> CODEC =
            Codec.unboundedMap(
                    Codec.STRING.xmap(BodyPart::valueOf, BodyPart::name),
                    BodyPartHealth.CODEC
            ).xmap(
                    map -> new PlayerBodyData(new EnumMap<>(map)),
                    data -> new HashMap<>(data.parts)
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerBodyData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PlayerBodyData decode(RegistryFriendlyByteBuf buf) {
            EnumMap<BodyPart, BodyPartHealth> map = new EnumMap<>(BodyPart.class);
            int partCount = buf.readVarInt();
            for (int index = 0; index < partCount; index++) {
                BodyPart part = buf.readEnum(BodyPart.class);
                BodyPartHealth health = BodyPartHealth.STREAM_CODEC.decode(buf);
                map.put(part, health);
            }
            for (BodyPart part : BodyPart.values()) {
                map.putIfAbsent(part, new BodyPartHealth(part.getDefaultMaxHp()));
            }
            return new PlayerBodyData(map);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PlayerBodyData value) {
            buf.writeVarInt(BodyPart.values().length);
            for (BodyPart part : BodyPart.values()) {
                buf.writeEnum(part);
                BodyPartHealth.STREAM_CODEC.encode(buf, value.getHealth(part));
            }
        }
    };

    private final Map<BodyPart, BodyPartHealth> parts;

    private PlayerBodyData(Map<BodyPart, BodyPartHealth> parts) {
        EnumMap<BodyPart, BodyPartHealth> normalized = new EnumMap<>(BodyPart.class);
        for (BodyPart part : BodyPart.values()) {
            BodyPartHealth existing = parts.get(part);
            if (existing == null) {
                normalized.put(part, new BodyPartHealth(part.getDefaultMaxHp()));
                continue;
            }

            int targetMaxHp = part.getDefaultMaxHp();
            if (existing.getMaxHp() != targetMaxHp) {
                normalized.put(part, new BodyPartHealth(targetMaxHp, existing.getHediffs()));
            } else {
                normalized.put(part, existing);
            }
        }
        this.parts = normalized;
    }

    public static PlayerBodyData createDefault() {
        EnumMap<BodyPart, BodyPartHealth> map = new EnumMap<>(BodyPart.class);
        for (BodyPart part : BodyPart.values()) {
            map.put(part, new BodyPartHealth(part.getDefaultMaxHp()));
        }
        return new PlayerBodyData(map);
    }

    public BodyPartHealth getHealth(BodyPart part) {
        return parts.get(part);
    }

    public Map<BodyPart, BodyPartHealth> getParts() {
        return parts;
    }

    public boolean hasVisibleIssue(BodyPart part) {
        BodyPartHealth health = getHealth(part);
        return health.isDamaged() || !health.getHediffs().isEmpty();
    }
}
