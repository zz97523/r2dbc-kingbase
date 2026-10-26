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

}
