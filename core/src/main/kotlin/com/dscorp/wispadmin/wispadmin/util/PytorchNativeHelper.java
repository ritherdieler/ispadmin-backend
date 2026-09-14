package com.dscorp.wispadmin.wispadmin.util;

public final class PytorchNativeHelper {
    private PytorchNativeHelper() {
    }

    public static void load(String path) {
        System.load(path);
    }
}
