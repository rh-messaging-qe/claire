/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import java.util.Arrays;

public enum ArtemisVersion {
    VERSION_2_20(2200),
    VERSION_2_21(2210),
    VERSION_2_22(2220),
    VERSION_2_23(2230),
    VERSION_2_24(2240),
    VERSION_2_25(2250),
    VERSION_2_26(2260),
    VERSION_2_27(2270),
    VERSION_2_28(2280);

    private final int versionNumber;

    ArtemisVersion(int version) {
        this.versionNumber = version;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    private int getOrdinalCustomPosition() {
        return this.ordinal() * 10 + 2200;
    }

    public static ArtemisVersion getByOrdinal(int versionNumber) {
        return Arrays.stream(ArtemisVersion.values())
                .filter(e -> e.getOrdinalCustomPosition() == versionNumber)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Invalid ordinal"));
    }
}
