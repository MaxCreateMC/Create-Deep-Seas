package com.maxenonyme.highseas.client;

public class SailContraptionContext {

    public static final ThreadLocal<Boolean> BAKING = ThreadLocal.withInitial(() -> Boolean.FALSE);
}
