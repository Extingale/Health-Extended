package com.ext.healthextended.data;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public enum HediffDef {
    STATUS_EFFECT("Status Effect", 0.0f, 0, false, false),
    BRUISE("Bruise", 0.16f, 2, true, true),
    BITE("Bite", 0.08f, 3, true, true),
    CUT("Cut", 0.12f, 3, true, true),
    BURN("Burn", 0.10f, 3, true, true),
    FROSTBITE("Frostbite", 0.07f, 2, true, true),
    POISONED("Poisoned", 0.10f, 2, true, true),
    WITHERED("Withered", 0.06f, 3, true, true),
    MAGIC_WOUND("Magic Wound", 0.08f, 3, true, true),
    SUFFOCATION("Suffocation", 0.08f, 10, true, true),
    STARVATION("Starvation", 0.04f, 19, false, true),
    HEART_ATTACK("Heart Attack", 0.0f, 0, false, false),
    BROKEN("Broken Limb", 0.0f, 0, false, false);

    public static final Codec<HediffDef> CODEC = Codec.STRING.xmap(HediffDef::valueOf, HediffDef::name);
    public static final StreamCodec<RegistryFriendlyByteBuf, HediffDef> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HediffDef decode(RegistryFriendlyByteBuf buf) {
            return buf.readEnum(HediffDef.class);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, HediffDef value) {
            buf.writeEnum(value);
        }
    };

    private final String displayName;
    private final float healPerHealthPoint;
    private final int maxHpLossAtFullSeverity;
    private final boolean canDestroyPart;
    private final boolean healsNaturally;

    HediffDef(String displayName, float healPerHealthPoint, int maxHpLossAtFullSeverity, boolean canDestroyPart, boolean healsNaturally) {
        this.displayName = displayName;
        this.healPerHealthPoint = healPerHealthPoint;
        this.maxHpLossAtFullSeverity = maxHpLossAtFullSeverity;
        this.canDestroyPart = canDestroyPart;
        this.healsNaturally = healsNaturally;
    }

    public String getDisplayName() {
        return displayName;
    }

    public float getHealPerHealthPoint() {
        return healPerHealthPoint;
    }

    public int getMaxHpLossAtFullSeverity() {
        return maxHpLossAtFullSeverity;
    }

    public boolean canDestroyPart() {
        return canDestroyPart;
    }

    public boolean healsNaturally() {
        return healsNaturally;
    }
}