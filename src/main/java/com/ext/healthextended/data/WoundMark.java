package com.ext.healthextended.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * A single wound mark on a player's body.
 *
 * @param part        the body part that was hit
 * @param type        visual category (determines tint color)
 * @param severity    0–1 fraction: how severe the wound is at creation
 * @param localV      0–1 position within the body part vertically (0 = bottom, 1 = top)
 * @param face        which face of the body part was struck
 * @param createdTick level game-time tick when the wound was created
 */
public record WoundMark(
        BodyPart part,
        WoundVisualType type,
        float severity,
        float localV,
        HitFace face,
        long createdTick
) {

    public static final Codec<WoundMark> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.xmap(BodyPart::valueOf, BodyPart::name)
                    .fieldOf("part").forGetter(WoundMark::part),
            Codec.STRING.xmap(WoundVisualType::valueOf, WoundVisualType::name)
                    .fieldOf("type").forGetter(WoundMark::type),
            Codec.FLOAT.fieldOf("severity").forGetter(WoundMark::severity),
            Codec.FLOAT.fieldOf("local_v").forGetter(WoundMark::localV),
            Codec.STRING.xmap(HitFace::valueOf, HitFace::name)
                    .fieldOf("face").forGetter(WoundMark::face),
            Codec.LONG.fieldOf("created_tick").forGetter(WoundMark::createdTick)
    ).apply(inst, WoundMark::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, WoundMark> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WoundMark decode(RegistryFriendlyByteBuf buf) {
            BodyPart part = buf.readEnum(BodyPart.class);
            WoundVisualType type = buf.readEnum(WoundVisualType.class);
            float severity = buf.readFloat();
            float localV = buf.readFloat();
            HitFace face = buf.readEnum(HitFace.class);
            long createdTick = buf.readLong();
            return new WoundMark(part, type, severity, localV, face, createdTick);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, WoundMark value) {
            buf.writeEnum(value.part());
            buf.writeEnum(value.type());
            buf.writeFloat(value.severity());
            buf.writeFloat(value.localV());
            buf.writeEnum(value.face());
            buf.writeLong(value.createdTick());
        }
    };
}
