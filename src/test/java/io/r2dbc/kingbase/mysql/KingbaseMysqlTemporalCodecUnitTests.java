/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.r2dbc.kingbase.mysql;

import io.r2dbc.postgresql.util.ByteBufUtils;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static io.r2dbc.postgresql.client.ParameterAssert.assertThat;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;
import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;

final class KingbaseMysqlTemporalCodecUnitTests {

    private static final int DATETIME = 9_501;

    private static final int TIME = 9_502;

    private final KingbaseMysqlTypeOids typeOids = typeOids();

    @Test
    void shouldRetainDatetimeMicrosecondsAndUseResolvedOid() {
        KingbaseMysqlTemporalCodecs.LocalDateTimeCodec codec =
            new KingbaseMysqlTemporalCodecs.LocalDateTimeCodec(TEST, this.typeOids);
        LocalDateTime value = LocalDateTime.of(2026, 10, 27, 12, 34, 56, 123_456_000);

        assertThat(codec.encode(value))
            .hasType(DATETIME)
            .hasValue(ByteBufUtils.encode(TEST, "2026-10-27 12:34:56.123456"));
        assertThat(codec.encodeNull()).hasType(DATETIME);
    }

    @Test
    void shouldRetainDurationFractionSignAndHoursBeyondOneDay() {
        KingbaseMysqlTemporalCodecs.DurationCodec codec =
            new KingbaseMysqlTemporalCodecs.DurationCodec(TEST, this.typeOids);
        Duration value = Duration.ofHours(27).plusMinutes(2).plusSeconds(3).plusNanos(123_456_000);

        assertThat(codec.encode(value))
            .hasType(TIME)
            .hasValue(ByteBufUtils.encode(TEST, "27:02:03.123456"));
        org.assertj.core.api.Assertions.assertThat(codec.decode(
            ByteBufUtils.encode(TEST, "-27:02:03.123456"), TIME, FORMAT_TEXT, Duration.class))
            .isEqualTo(value.negated());
    }

    private static KingbaseMysqlTypeOids typeOids() {
        Map<String, Integer> types = new HashMap<>();
        types.put("datetime", DATETIME);
        types.put("time", TIME);
        return KingbaseMysqlTypeOids.withNamedTypes(types);
    }

}
