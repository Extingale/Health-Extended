package com.ext.healthextended.data;

public enum BodyPart {
    HEAD("Head", 2, 4),
    TORSO("Torso", 6, 3),
    LEFT_ARM("Left Arm", 4, 1),
    RIGHT_ARM("Right Arm", 4, 1),
    LEFT_LEG("Left Leg", 4, 1),
    RIGHT_LEG("Right Leg", 4, 1);

    private final String displayName;
    private final int defaultMaxHp;
    private final int overallWeight;

    BodyPart(String displayName, int defaultMaxHp, int overallWeight) {
        this.displayName = displayName;
        this.defaultMaxHp = defaultMaxHp;
        this.overallWeight = overallWeight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultMaxHp() {
        return defaultMaxHp;
    }

    public int getOverallWeight() {
        return overallWeight;
    }
}
