/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.r2dbc.kingbase.mysql;

import io.r2dbc.postgresql.client.ParameterAssert;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.DefaultCodecs;
import io.r2dbc.spi.Parameters;
import io.r2dbc.spi.R2dbcType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;

final class KingbaseMysqlModeRoutingUnitTests {

    private static final int TINYINT = 8_100;
    private static final int BIT = 4_655;
    private static final int DATE = 7_944;
    private static final int TIME = 7_950;
    private static final int DATETIME = 7_952;
    private static final int TIMESTAMP = 7_954;
    private static final int YEAR = 7_025;
    private static final int VARBINARY = 3_384;
    private static final int BLOB = 8_013;

    @Test
    void routesInferredJavaValuesThroughMysqlTypes() {
        DefaultCodecs codecs = mysqlCodecs();

        assertType(codecs, Byte.valueOf((byte) 1), TINYINT);
        assertType(codecs, Boolean.TRUE, TINYINT);
        assertType(codecs, LocalDate.of(2026, 8, 1), DATE);
        assertType(codecs, LocalTime.of(10, 30, 15, 123_456_000), TIME);
        assertType(codecs, LocalDateTime.of(2026, 8, 1, 10, 30, 15, 123_456_000), DATETIME);
        assertType(codecs, Instant.parse("2026-08-01T02:30:15.123456Z"), TIMESTAMP);
        assertType(codecs, Year.of(2026), YEAR);
        assertType(codecs, new byte[]{0, 1, (byte) 255}, VARBINARY);
    }

    @Test
    void routesExplicitR2dbcTypesThroughMysqlTypes() {
        DefaultCodecs codecs = mysqlCodecs();

        assertType(codecs, Parameters.in(R2dbcType.TINYINT, Byte.valueOf((byte) 1)), TINYINT);
        assertType(codecs, Parameters.in(R2dbcType.BOOLEAN, Boolean.TRUE), TINYINT);
        assertType(codecs, Parameters.in(R2dbcType.DATE, LocalDate.of(2026, 8, 1)), DATE);
        assertType(codecs, Parameters.in(R2dbcType.TIME, LocalTime.NOON), TIME);
        assertType(codecs, Parameters.in(R2dbcType.TIMESTAMP,
            LocalDateTime.of(2026, 8, 1, 10, 30)), DATETIME);
        assertType(codecs, Parameters.in(R2dbcType.TIMESTAMP_WITH_TIME_ZONE,
            Instant.parse("2026-08-01T02:30:15Z")), TIMESTAMP);
        assertType(codecs, Parameters.in(R2dbcType.VARBINARY, new byte[]{1}), VARBINARY);
        assertType(codecs, Parameters.in(R2dbcType.BLOB, new byte[]{1}), BLOB);
    }

    @Test
    void routesTypedNullsThroughMysqlTypes() {
        DefaultCodecs codecs = mysqlCodecs();

        assertType(codecs, Parameters.in(R2dbcType.DATE, null), DATE);
        assertType(codecs, Parameters.in(R2dbcType.BOOLEAN, null), TINYINT);
        assertType(codecs, Parameters.in(R2dbcType.VARBINARY, null), VARBINARY);
        ParameterAssert.assertThat(codecs.encodeNull(LocalDate.class)).hasType(DATE);
        ParameterAssert.assertThat(codecs.encodeNull(LocalTime.class)).hasType(TIME);
        ParameterAssert.assertThat(codecs.encodeNull(LocalDateTime.class)).hasType(DATETIME);
        ParameterAssert.assertThat(codecs.encodeNull(Byte.class)).hasType(TINYINT);
        ParameterAssert.assertThat(codecs.encodeNull(Boolean.class)).hasType(TINYINT);
        ParameterAssert.assertThat(codecs.encodeNull(byte[].class)).hasType(VARBINARY);
    }

    private static void assertType(DefaultCodecs codecs, Object value, int expectedOid) {
        ParameterAssert.assertThat(codecs.encode(value)).hasType(expectedOid);
    }

    private static DefaultCodecs mysqlCodecs() {
        Map<String, Integer> types = new LinkedHashMap<>();
        types.put("tinyint", TINYINT);
        types.put("bit", BIT);
        types.put("date", DATE);
        types.put("time", TIME);
        types.put("datetime", DATETIME);
        types.put("timestamp", TIMESTAMP);
        types.put("year", YEAR);
        types.put("varbinary", VARBINARY);
        types.put("blob", BLOB);

        KingbaseMysqlTypeOids typeOids = KingbaseMysqlTypeOids.withNamedTypes(types);
        DefaultCodecs codecs = new DefaultCodecs(TEST, false);
        codecs.setR2dbcTypeMapper(typeOids::r2dbcType);
        List<Codec<?>> mysqlCodecs = KingbaseMysqlCodecs.create(TEST, typeOids);
        for (int i = mysqlCodecs.size() - 1; i >= 0; i--) {
            codecs.addFirst(mysqlCodecs.get(i));
        }
        return codecs;
    }
}
