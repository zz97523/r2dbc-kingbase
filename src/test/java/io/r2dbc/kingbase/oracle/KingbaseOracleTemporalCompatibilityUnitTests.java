/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */

package io.r2dbc.kingbase.oracle;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class KingbaseOracleTemporalCompatibilityUnitTests {

    @Test
    void parsesWholeHourMinuteAndSecondOffsets() {
        assertThat(KingbaseOracleTemporalCodecs.parseOffsetDateTime(
            "2024-02-29 23:59:59.123000+08"))
            .isEqualTo(OffsetDateTime.parse("2024-02-29T23:59:59.123+08:00"));
        assertThat(KingbaseOracleTemporalCodecs.parseOffsetDateTime(
            "2024-02-29T23:59:59.123000+08:30"))
            .isEqualTo(OffsetDateTime.parse("2024-02-29T23:59:59.123+08:30"));
        assertThat(KingbaseOracleTemporalCodecs.parseOffsetDateTime(
            "2024-02-29T23:59:59.123000-03:30:15"))
            .isEqualTo(OffsetDateTime.parse("2024-02-29T23:59:59.123-03:30:15"));
    }

    @Test
    void usesUtcWhenTimestampHasNoOffset() {
        OffsetDateTime value = KingbaseOracleTemporalCodecs.parseOffsetDateTime(
            "2024-02-29 23:59:59.123000");

        assertThat(value.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(value.toLocalDateTime().toString()).isEqualTo("2024-02-29T23:59:59.123");
    }

    @Test
    void parsesWholeHourOffsetForOracleTime() {
        assertThat(KingbaseOracleTemporalCodecs.parseOffsetTime("11:04:05+08"))
            .isEqualTo(OffsetTime.parse("11:04:05+08:00"));
    }

    @Test
    void keepsOracleTemporalBindTypesIndependent() {
        Map<String, Integer> types = new LinkedHashMap<>();
        types.put("date", 9_701);
        types.put("timestamp", 9_702);
        types.put("timestampltz", 9_703);
        KingbaseOracleTypeOids typeOids = KingbaseOracleTypeOids.withNamedTypes(types);

        assertThat(typeOids.encodeOid(KingbaseOracleType.DATE)).isEqualTo(9_701);
        assertThat(typeOids.encodeOid(KingbaseOracleType.TIMESTAMP)).isEqualTo(9_702);
        assertThat(typeOids.encodeOid(KingbaseOracleType.TIMESTAMP_LTZ)).isEqualTo(9_703);
    }

}
