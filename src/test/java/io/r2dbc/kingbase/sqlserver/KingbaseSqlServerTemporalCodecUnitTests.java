/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.r2dbc.kingbase.sqlserver;

import io.r2dbc.postgresql.util.ByteBufUtils;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;

import static io.r2dbc.postgresql.client.ParameterAssert.assertThat;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;
import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;

final class KingbaseSqlServerTemporalCodecUnitTests {

    private static final int TIME = 9_601;

    private final KingbaseSqlServerTemporalCodecs.DurationCodec codec =
        new KingbaseSqlServerTemporalCodecs.DurationCodec(TEST,
            KingbaseSqlServerTypeOids.withNamedTypes(Collections.singletonMap("time", TIME)));

    @Test
    void shouldRetainDurationFraction() {
        Duration value = Duration.ofHours(12).plusMinutes(2).plusSeconds(3).plusNanos(123_456_000);

        assertThat(this.codec.encode(value))
            .hasType(TIME)
            .hasValue(ByteBufUtils.encode(TEST, "12:02:03.123456"));
        org.assertj.core.api.Assertions.assertThat(this.codec.decode(
            ByteBufUtils.encode(TEST, "12:02:03.123456"), TIME, FORMAT_TEXT, Duration.class))
            .isEqualTo(value);
    }

}
