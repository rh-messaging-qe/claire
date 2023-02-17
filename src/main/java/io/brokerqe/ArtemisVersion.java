/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import java.util.Arrays;

public enum ArtemisVersion {
    VERSION_2_20(220),
    VERSION_2_21(221),
    VERSION_2_22(222),
    VERSION_2_23(223),
    VERSION_2_24(224),
    VERSION_2_25(225),
    VERSION_2_26(226),
    VERSION_2_27(227),
    VERSION_2_28(228);

    private final int versionNumber;

    ArtemisVersion(int version) {
        this.versionNumber = version;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    private int getOrdinalCustomPosition() {
        return this.ordinal() + 220;
    }

    public static ArtemisVersion getByOrdinal(int versionNumber) {
        return Arrays.stream(ArtemisVersion.values())
                .filter(e -> e.getOrdinalCustomPosition() == versionNumber)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Invalid ordinal"));
    }
}
