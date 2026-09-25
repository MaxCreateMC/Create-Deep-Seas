package com.maxenonyme.highseas.client;

public final class SailContraptionContext {
    private SailContraptionContext() {
    }

    public static final ThreadLocal<Boolean> BAKING = ThreadLocal.withInitial(() -> Boolean.FALSE);
}
