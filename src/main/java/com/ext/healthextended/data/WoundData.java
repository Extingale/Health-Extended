package com.ext.healthextended.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds all active wound marks for a single player.
 *
 * <p>Wound marks are cosmetic and ephemeral — this attachment is intentionally
 * not serialized to disk. Marks are emitted by {@link com.ext.healthextended.logic.LocationalHealthLogic}
 * whenever damage is applied and fade out over time via the render layer.</p>
 */
public class WoundData {

    /** Hard cap on simultaneous marks across all body parts. Oldest is dropped when exceeded. */
    private static final int MAX_MARKS = 32;

    /**
     * Ticks after which a mark is pruned regardless of severity.
     * Roughly 4 minutes (severity 1.0). The render layer fades marks well before this.
     */
    public static final long MARK_EXPIRY_TICKS = 4800L;

    public static final StreamCodec<RegistryFriendlyByteBuf, WoundData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WoundData decode(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            WoundData data = new WoundData();
            for (int i = 0; i < count; i++) {
                data.marks.add(WoundMark.STREAM_CODEC.decode(buf));
            }
            return data;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, WoundData value) {
            buf.writeVarInt(value.marks.size());
            for (WoundMark mark : value.marks) {
                WoundMark.STREAM_CODEC.encode(buf, mark);
            }
        }
    };

    private final List<WoundMark> marks = new ArrayList<>();

    public static WoundData createDefault() {
        return new WoundData();
    }

    public List<WoundMark> getMarks() {
        return marks;
    }

    /**
     * Adds a wound mark, dropping the oldest if {@link #MAX_MARKS} is exceeded.
     */
    public void addMark(WoundMark mark) {
        marks.add(mark);
        if (marks.size() > MAX_MARKS) {
            marks.remove(0);
        }
    }

    /**
     * Removes marks whose age exceeds {@link #MARK_EXPIRY_TICKS}.
     *
     * @return {@code true} if any marks were removed
     */
    public boolean pruneExpired(long currentTick) {
        return marks.removeIf(m -> currentTick - m.createdTick() > MARK_EXPIRY_TICKS);
    }

    public void clear() {
        marks.clear();
    }
}
