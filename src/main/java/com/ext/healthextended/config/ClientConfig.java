package com.ext.healthextended.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ANCHOR_PANEL_RIGHT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("ui");
        ANCHOR_PANEL_RIGHT = builder
                .comment("If true, inventory health panel is anchored to the right side of the inventory.")
            .define("anchorPanelRight", true);
        builder.pop();

        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    public static boolean anchorPanelRight() {
        return ANCHOR_PANEL_RIGHT.get();
    }
}
