/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import java.util.Arrays;

public enum ArtemisVersion {
    VERSION_2_10(2100, null),
    VERSION_2_11(2110, null),
    VERSION_2_12(2120, null),
    VERSION_2_13(2130, null),
    VERSION_2_14(2140, null),
    VERSION_2_15(2150, null),
    VERSION_2_16(2160, null),
    VERSION_2_17(2170, null),
    VERSION_2_18(2180, null),
    VERSION_2_19(2190, null),
    VERSION_2_20(2200, null),
    VERSION_2_21(2210, 7100),
    VERSION_2_22(2220, null),
    VERSION_2_23(2230, null),
    VERSION_2_24(2240, null),
    VERSION_2_25(2250, null),
    VERSION_2_26(2260, null),
    VERSION_2_27(2270, null),
    VERSION_2_28(2280, 7110),
    VERSION_2_29(2290, null),
    VERSION_2_30(2300, null),
    VERSION_2_31(2310, null),
    VERSION_2_32(2320, null),
    VERSION_2_33(2330, 7120),
    VERSION_2_34(2340, null),
    VERSION_2_35(2350, null),
    VERSION_2_36(2360, null);

    private final int versionNumber;
    private final Integer amqBrokerVersion;

    ArtemisVersion(int version, Integer amqBrokerVersion) {
        this.versionNumber = version;
        this.amqBrokerVersion = amqBrokerVersion;
    }

    public int getVersionNumber() {
        return versionNumber;
    }
    public Integer getAmqBrokerNumber() {
        return amqBrokerVersion;
    }

    private int getOrdinalCustomPosition() {
        return this.ordinal() * 10 + 2100;
    }

    public static ArtemisVersion getByOrdinal(int versionNumber) {
        return Arrays.stream(ArtemisVersion.values())
                .filter(e -> e.getOrdinalCustomPosition() == versionNumber)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Invalid ordinal"));
    }
}
