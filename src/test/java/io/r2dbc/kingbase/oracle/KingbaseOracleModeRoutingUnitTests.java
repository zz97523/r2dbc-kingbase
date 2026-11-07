/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */

package io.r2dbc.kingbase.oracle;

import io.r2dbc.postgresql.client.ParameterAssert;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.DefaultCodecs;
import io.r2dbc.postgresql.util.ByteBufUtils;
import io.r2dbc.spi.Parameters;
import io.r2dbc.spi.R2dbcType;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BYTEA;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;
import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;
import static org.assertj.core.api.Assertions.assertThat;

final class KingbaseOracleModeRoutingUnitTests {

    private static final int ORACLE_DATE = 9_701;

    private static final int ORACLE_TIMESTAMP = 9_702;

    @Test
    void routesTemporalBindsThroughOracleTypes() {
        DefaultCodecs codecs = oracleCodecs();
        LocalTime time = LocalTime.of(10, 30, 15, 123_456_789);

        ParameterAssert.assertThat(codecs.encode(time))
            .hasType(ORACLE_TIMESTAMP)
            .hasValue(ByteBufUtils.encode(TEST, "1970-01-01 10:30:15.123456789"));
        ParameterAssert.assertThat(codecs.encode(Parameters.in(R2dbcType.TIMESTAMP, time)))
            .hasType(ORACLE_TIMESTAMP)
            .hasValue(ByteBufUtils.encode(TEST, "1970-01-01 10:30:15.123456789"));
        ParameterAssert.assertThat(codecs.encodeNull(LocalTime.class))
            .hasType(ORACLE_TIMESTAMP);
    }

    @Test
    void mapsExplicitDateWithoutDiscardingTimeOfDay() {
        DefaultCodecs codecs = oracleCodecs();
        LocalDateTime value = LocalDateTime.of(2026, 8, 1, 10, 30, 15, 123_456_000);

        ParameterAssert.assertThat(codecs.encode(Parameters.in(R2dbcType.DATE, value)))
            .hasType(ORACLE_DATE)
            .hasValue(ByteBufUtils.encode(TEST, "2026-08-01 10:30:15.123456"));
    }

    @Test
    void usesOracleBinaryCodecForDefaultByteaReads() {
        DefaultCodecs codecs = oracleCodecs();

        assertThat(codecs.preferredType(BYTEA.getObjectId(), FORMAT_TEXT))
            .isEqualTo(ByteBuffer.class);
        ByteBuffer decoded = codecs.decode(ByteBufUtils.encode(TEST, "\\000\\001\\377\\052"),
            BYTEA.getObjectId(), FORMAT_TEXT, ByteBuffer.class);
        byte[] bytes = new byte[decoded.remaining()];
        decoded.get(bytes);
        assertThat(bytes).containsExactly(0, 1, 0xff, 0x2a);
    }

    private DefaultCodecs oracleCodecs() {
        Map<String, Integer> types = new LinkedHashMap<>();
        types.put("date", ORACLE_DATE);
        types.put("timestamp", ORACLE_TIMESTAMP);
        types.put("blob", 9_703);
        KingbaseOracleTypeOids typeOids = KingbaseOracleTypeOids.withNamedTypes(types);
        DefaultCodecs codecs = new DefaultCodecs(TEST, false);
        codecs.setR2dbcTypeMapper(typeOids::r2dbcType);
        List<Codec<?>> oracleCodecs = KingbaseOracleCodecs.create(TEST, typeOids);
        for (int i = oracleCodecs.size() - 1; i >= 0; i--) {
            codecs.addFirst(oracleCodecs.get(i));
        }
        return codecs;
    }
}

