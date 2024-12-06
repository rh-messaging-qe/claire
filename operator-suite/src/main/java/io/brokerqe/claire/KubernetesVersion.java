/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import java.util.Arrays;

public enum KubernetesVersion {

//    https://access.redhat.com/solutions/4870701
    VERSION_1_23(123), // 4.10
    VERSION_1_24(124), // 4.11
    VERSION_1_25(125), // 4.12
    VERSION_1_26(126), // 4.13
    VERSION_1_27(127), // 4.14
    VERSION_1_28(128), // 4.15
    VERSION_1_29(129), // 4.16
    VERSION_1_30(130), // 4.17
    VERSION_1_31(131), // 4.18
    VERSION_1_32(132), // 4.19
    VERSION_1_33(133), // 4.20
    VERSION_1_34(134), // 4.21
    VERSION_1_35(135); // 4.22

    private final int versionNumber;

    KubernetesVersion(int version) {
        this.versionNumber = version;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    private int getOrdinalCustomPosition() {
        return this.ordinal() + 123;
    }

    public static KubernetesVersion getByOrdinal(int versionNumber) {
        return Arrays.stream(KubernetesVersion.values())
                .filter(e -> e.getOrdinalCustomPosition() == versionNumber)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Invalid ordinal"));
    }
}
