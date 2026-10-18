package com.maxenonyme.highseas.block;

import net.minecraft.util.StringRepresentable;

public enum SailCorner implements StringRepresentable {
    NONE("none", 0, 0),
    UPPER_POSITIVE("upper_positive", 1, 1),
    UPPER_NEGATIVE("upper_negative", -1, 1),
    LOWER_POSITIVE("lower_positive", 1, -1),
    LOWER_NEGATIVE("lower_negative", -1, -1);

    public static final SailCorner[] CUTS = { UPPER_POSITIVE, UPPER_NEGATIVE, LOWER_POSITIVE, LOWER_NEGATIVE };

    private final String name;
    public final int h;
    public final int v;

    SailCorner(String name, int h, int v) {
        this.name = name;
        this.h = h;
        this.v = v;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
