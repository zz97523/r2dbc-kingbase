/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.r2dbc.kingbase.sqlserver;

import io.r2dbc.postgresql.client.ParameterAssert;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.DefaultCodecs;
import io.r2dbc.spi.Parameters;
import io.r2dbc.spi.R2dbcType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;

final class KingbaseSqlServerModeRoutingUnitTests {

    private static final int TINYINT = 5_026;
    private static final int SMALLINT = 5_061;
    private static final int INTEGER = 5_063;
    private static final int BIGINT = 5_065;
    private static final int REAL = 6_069;
    private static final int FLOAT = 6_071;
    private static final int NUMERIC = 5_067;
    private static final int BIT = 7_915;
    private static final int DATE = 7_754;
    private static final int TIME = 7_755;
    private static final int DATETIME2 = 7_690;
    private static final int NVARCHAR = 5_041;
    private static final int VARBINARY = 5_024;
    private static final int UNIQUEIDENTIFIER = 4_193;

    @Test
    void routesInferredJavaValuesThroughSqlServerTypes() {
        DefaultCodecs codecs = sqlServerCodecs();

        assertType(codecs, Byte.valueOf((byte) 1), TINYINT);
        assertType(codecs, Short.valueOf((short) 1), SMALLINT);
        assertType(codecs, Integer.valueOf(1), INTEGER);
        assertType(codecs, Long.valueOf(1), BIGINT);
        assertType(codecs, Float.valueOf(1), REAL);
        assertType(codecs, Double.valueOf(1), FLOAT);
        assertType(codecs, BigDecimal.ONE, NUMERIC);
        assertType(codecs, Boolean.TRUE, BIT);
        assertType(codecs, LocalDate.of(2026, 8, 1), DATE);
        assertType(codecs, LocalTime.of(10, 30, 15, 123_456_700), TIME);
        assertType(codecs, LocalDateTime.of(2026, 8, 1, 10, 30, 15, 123_456_700), DATETIME2);
        assertType(codecs, "Kingbase", NVARCHAR);
        assertType(codecs, new byte[]{0, 1, (byte) 255}, VARBINARY);
        assertType(codecs, UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), UNIQUEIDENTIFIER);
    }

    @Test
    void routesExplicitR2dbcTypesThroughSqlServerTypes() {
        DefaultCodecs codecs = sqlServerCodecs();

        assertType(codecs, Parameters.in(R2dbcType.SMALLINT, Short.valueOf((short) 1)), SMALLINT);
        assertType(codecs, Parameters.in(R2dbcType.INTEGER, Integer.valueOf(1)), INTEGER);
        assertType(codecs, Parameters.in(R2dbcType.BIGINT, Long.valueOf(1)), BIGINT);
        assertType(codecs, Parameters.in(R2dbcType.NUMERIC, BigDecimal.ONE), NUMERIC);
        assertType(codecs, Parameters.in(R2dbcType.REAL, Float.valueOf(1)), REAL);
        assertType(codecs, Parameters.in(R2dbcType.DOUBLE, Double.valueOf(1)), FLOAT);
        assertType(codecs, Parameters.in(R2dbcType.BOOLEAN, Boolean.TRUE), BIT);
        assertType(codecs, Parameters.in(R2dbcType.DATE, LocalDate.of(2026, 8, 1)), DATE);
        assertType(codecs, Parameters.in(R2dbcType.TIME, LocalTime.NOON), TIME);
        assertType(codecs, Parameters.in(R2dbcType.TIMESTAMP,
            LocalDateTime.of(2026, 8, 1, 10, 30)), DATETIME2);
        assertType(codecs, Parameters.in(R2dbcType.NVARCHAR, "Kingbase"), NVARCHAR);
        assertType(codecs, Parameters.in(R2dbcType.VARBINARY, new byte[]{1}), VARBINARY);
    }

    @Test
    void routesTypedNullsThroughSqlServerTypes() {
        DefaultCodecs codecs = sqlServerCodecs();

        assertType(codecs, Parameters.in(R2dbcType.DATE, null), DATE);
        assertType(codecs, Parameters.in(R2dbcType.NUMERIC, null), NUMERIC);
        assertType(codecs, Parameters.in(R2dbcType.NVARCHAR, null), NVARCHAR);
        assertType(codecs, Parameters.in(R2dbcType.VARBINARY, null), VARBINARY);
        ParameterAssert.assertThat(codecs.encodeNull(LocalDate.class)).hasType(DATE);
        ParameterAssert.assertThat(codecs.encodeNull(LocalTime.class)).hasType(TIME);
        ParameterAssert.assertThat(codecs.encodeNull(LocalDateTime.class)).hasType(DATETIME2);
        ParameterAssert.assertThat(codecs.encodeNull(Short.class)).hasType(SMALLINT);
        ParameterAssert.assertThat(codecs.encodeNull(BigDecimal.class)).hasType(NUMERIC);
        ParameterAssert.assertThat(codecs.encodeNull(String.class)).hasType(NVARCHAR);
        ParameterAssert.assertThat(codecs.encodeNull(byte[].class)).hasType(VARBINARY);
    }

    private static void assertType(DefaultCodecs codecs, Object value, int expectedOid) {
        ParameterAssert.assertThat(codecs.encode(value)).hasType(expectedOid);
    }

    private static DefaultCodecs sqlServerCodecs() {
        Map<String, Integer> types = new LinkedHashMap<>();
        types.put("tinyint", TINYINT);
        types.put("smallint", SMALLINT);
        types.put("int", INTEGER);
        types.put("bigint", BIGINT);
        types.put("real", REAL);
        types.put("float", FLOAT);
        types.put("numeric", NUMERIC);
        types.put("bit", BIT);
        types.put("date", DATE);
        types.put("time", TIME);
        types.put("datetime2", DATETIME2);
        types.put("nvarchar", NVARCHAR);
        types.put("varbinary", VARBINARY);
        types.put("uniqueidentifier", UNIQUEIDENTIFIER);

        KingbaseSqlServerTypeOids typeOids = KingbaseSqlServerTypeOids.withNamedTypes(types);
        DefaultCodecs codecs = new DefaultCodecs(TEST, false);
        codecs.setR2dbcTypeMapper(typeOids::r2dbcType);
        List<Codec<?>> sqlServerCodecs = KingbaseSqlServerCodecs.create(TEST, typeOids);
        for (int i = sqlServerCodecs.size() - 1; i >= 0; i--) {
            codecs.addFirst(sqlServerCodecs.get(i));
        }
        return codecs;
    }
}
