package com.ext.healthextended.data;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * Visual category for a wound mark.
 *
 * <p>Each type declares one or more weighted texture variants. When a wound is
 * created the {@link com.ext.healthextended.client.WoundDecalManager} calls
 * {@link #pickVariant} with a deterministic seed derived from the mark so that
 * the same wound always renders the same variant, while rare easter-egg textures
 * can be given a low weight to appear only occasionally.</p>
 *
 * <p>Texture files are expected at:
 * {@code assets/healthextended/textures/wounds/<name>.png}</p>
 */
public enum WoundVisualType {

    BRUISE(
        v("bruise1", 10),
        v("bruise2", 10),
        v("bruise_amogus", 1)   // sussy
    ),
    BITE(
        v("bite1", 10),
        v("bite2", 10)
    ),
    CUT(
        v("cut1", 10),
        v("cut2", 10)
    ),
    BURN(
        v("burn1", 10),
        v("burn2", 10)
    ),
    FROSTBITE(
        v("frostbite1", 10),
        v("frostbite2", 10)
    ),
    POISONED(
        v("poison1", 10),
        v("poison2", 10)
    ),
    WITHERED(
        v("withered1", 10),
        v("withered2", 10)
    ),
    MAGIC_WOUND(
        v("magic1", 10),
        v("magic2", 10)
    );

    // -------------------------------------------------------------------------

    /**
     * A texture variant with an associated integer weight.
     * Higher weight = more likely to be selected.
     */
    public record WeightedVariant(ResourceLocation texture, int weight) {}

    /** All variants for this wound type, in declaration order. */
    public final List<WeightedVariant> variants;

    /** Sum of all variant weights — cached to avoid recomputation. */
    public final int totalWeight;

    WoundVisualType(WeightedVariant... variants) {
        this.variants    = List.of(variants);
        this.totalWeight = Arrays.stream(variants).mapToInt(WeightedVariant::weight).sum();
    }

    /**
     * Deterministically picks a variant texture for the given wound seed.
     *
     * <p>Use {@code mark.createdTick() ^ (long) mark.part().ordinal()} as the
     * seed so the same wound always shows the same variant across frames.</p>
     */
    public ResourceLocation pickVariant(long seed) {
        int pick = (int) (Math.abs(seed) % totalWeight);
        int cumulative = 0;
        for (WeightedVariant v : variants) {
            cumulative += v.weight;
            if (pick < cumulative) return v.texture;
        }
        return variants.get(0).texture; // unreachable, satisfies compiler
    }

    // -------------------------------------------------------------------------

    /** Shorthand constructor for a variant entry. */
    private static WeightedVariant v(String name, int weight) {
        return new WeightedVariant(
                ResourceLocation.fromNamespaceAndPath("healthextended",
                        "textures/wounds/" + name + ".png"),
                weight);
    }

    /**
     * Maps a {@link HediffDef} to its corresponding wound visual type,
     * or {@code null} for hediffs that have no visible wound mark
     * (e.g. starvation, heart attack).
     */
    @Nullable
    public static WoundVisualType fromHediff(HediffDef def) {
        return switch (def) {
            case BRUISE -> BRUISE;
            case BITE -> BITE;
            case CUT -> CUT;
            case BURN -> BURN;
            case FROSTBITE -> FROSTBITE;
            case POISONED -> POISONED;
            case WITHERED -> WITHERED;
            case MAGIC_WOUND -> MAGIC_WOUND;
            default -> null;
        };
    }
}
