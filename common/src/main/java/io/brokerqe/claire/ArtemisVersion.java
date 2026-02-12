/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import java.util.Arrays;

public enum ArtemisVersion {
    VERSION_2_10(2100),
    VERSION_2_11(2110),
    VERSION_2_12(2120),
    VERSION_2_13(2130),
    VERSION_2_14(2140),
    VERSION_2_15(2150),
    VERSION_2_16(2160),
    VERSION_2_17(2170),
    VERSION_2_18(2180),
    VERSION_2_19(2190),
    VERSION_2_20(2200),
    VERSION_2_21(2210), // 7.10.0
    VERSION_2_22(2220),
    VERSION_2_23(2230),
    VERSION_2_24(2240),
    VERSION_2_25(2250),
    VERSION_2_26(2260),
    VERSION_2_27(2270),
    VERSION_2_28(2280), // 7.11.0
    VERSION_2_29(2290),
    VERSION_2_30(2300),
    VERSION_2_31(2310),
    VERSION_2_32(2320),
    VERSION_2_33(2330), // 7.12.0
    VERSION_2_34(2340),
    VERSION_2_35(2350),
    VERSION_2_36(2360),
    VERSION_2_37(2370),
    VERSION_2_38(2390),
    VERSION_2_39(2390),
    VERSION_2_40(2400), // 7.13.0
    VERSION_2_41(2410),
    VERSION_2_42(2420),
    VERSION_2_43(2430),
    VERSION_2_44(2440),
    VERSION_2_45(2450),
    VERSION_2_46(2460),
    VERSION_2_47(2470),
    VERSION_2_48(2480),
    VERSION_2_49(2490),
    VERSION_2_50(2500),
    VERSION_2_51(2510), // 7.14.0
    VERSION_2_52(2520),
    VERSION_2_53(2530),
    VERSION_2_54(2540),
    VERSION_2_55(2550),
    VERSION_2_56(2560),
    VERSION_2_57(2570),
    VERSION_2_58(2580),
    VERSION_2_59(2590),
    VERSION_2_60(2600),
    VERSION_2_61(2610),
    VERSION_2_62(2620),
    VERSION_2_63(2630),
    VERSION_2_64(2640),
    VERSION_2_65(2650),
    VERSION_2_66(2660),
    VERSION_2_67(2670),
    VERSION_2_68(2680),
    VERSION_2_69(2690),
    VERSION_2_70(2700),
    VERSION_2_71(2710),
    VERSION_2_72(2720),
    VERSION_2_73(2730),
    VERSION_2_74(2740),
    VERSION_2_75(2750),
    VERSION_2_76(2760),
    VERSION_2_77(2770),
    VERSION_2_78(2780),
    VERSION_2_79(2790),
    VERSION_2_80(2800);

    private final int versionNumber;

    ArtemisVersion(int version) {
        this.versionNumber = version;
    }

    public int getVersionNumber() {
        return versionNumber;
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
