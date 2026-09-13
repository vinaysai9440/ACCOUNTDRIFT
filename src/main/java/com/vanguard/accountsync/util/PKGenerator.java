package com.vanguard.accountsync.util;

import java.time.LocalDate;

public final class PKGenerator {

    private static final String DELIMITER = "|";

    private PKGenerator() {
    }

    public static String generate(String vastAccountNumber, String portId, LocalDate createdDate) {
        return vastAccountNumber + DELIMITER + portId + DELIMITER + createdDate;
    }

    public static String vastPortKey(String vastAccountNumber, String portId) {
        return vastAccountNumber + DELIMITER + portId;
    }
}
