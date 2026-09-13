package com.vanguard.accountsync.util;

import java.util.UUID;

public final class AccountNumberGenerator {

    private static final String PREFIX = "NEWT-";

    private AccountNumberGenerator() {
    }

    public static String generate() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return PREFIX + System.currentTimeMillis() + "-" + suffix;
    }
}
