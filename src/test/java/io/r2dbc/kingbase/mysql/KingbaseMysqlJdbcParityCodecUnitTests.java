/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */

package io.r2dbc.kingbase.mysql;

import io.r2dbc.kingbase.KingbaseMoney;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.DefaultCodecs;
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.postgresql.util.ByteBufUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.RowId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.r2dbc.postgresql.client.ParameterAssert.assertThat;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;
import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;

final class KingbaseMysqlJdbcParityCodecUnitTests {

    private static final int MONEY = 790;

    private static final int MONEY_ARRAY = 791;

    private static final int ROWID = 6123;

    private static final int ROWID_ARRAY = 6124;

    private static final int INT_UNSIGNED = 7082;

    private static final int BIGINT_UNSIGNED = 7084;

    private DefaultCodecs codecs;

    private KingbaseMysqlTypeOids typeOids;

    @BeforeEach
    void beforeEach() {
        Map<String, Integer> types = new HashMap<>();
        types.put("money", MONEY);
        types.put("_money", MONEY_ARRAY);
        types.put("rowid", ROWID);
        types.put("_rowid", ROWID_ARRAY);
        types.put("int unsigned", INT_UNSIGNED);
        types.put("bigint unsigned", BIGINT_UNSIGNED);
        this.typeOids = KingbaseMysqlTypeOids.withNamedTypes(types);

        this.codecs = new DefaultCodecs(TEST, false);
        List<Codec<?>> mysqlCodecs = KingbaseMysqlCodecs.create(TEST, this.typeOids);
        for (int i = mysqlCodecs.size() - 1; i >= 0; i--) {
            this.codecs.addFirst(mysqlCodecs.get(i));
        }
    }

    @Test
    void moneyUsesJdbcCompatibleTypesAndNativeOid() {
        org.assertj.core.api.Assertions.assertThat(this.codecs.decode(
            ByteBufUtils.encode(TEST, "￥12.34"), MONEY, FORMAT_TEXT, Object.class))
            .isEqualTo(12.34D);
        org.assertj.core.api.Assertions.assertThat(this.codecs.decode(
            ByteBufUtils.encode(TEST, "￥12.34"), MONEY, FORMAT_TEXT, BigDecimal.class))
            .isEqualTo(new BigDecimal("12.34"));
        assertThat(this.codecs.encode(new KingbaseMoney("98.76")))
            .hasType(MONEY)
            .hasValue(ByteBufUtils.encode(TEST, "98.76"));
    }

    @Test
    void rowIdUsesResolvedOid() {
        RowId decoded = this.codecs.decode(ByteBufUtils.encode(TEST, "AAAPecAABAAABxQAAA"),
            ROWID, FORMAT_TEXT, RowId.class);

        org.assertj.core.api.Assertions.assertThat(decoded)
            .isInstanceOf(KingbaseMysqlRowId.class)
            .hasToString("AAAPecAABAAABxQAAA");
        assertThat(this.codecs.encode(new KingbaseMysqlRowId("AAAPecAABAAABxQAAA")))
            .hasType(ROWID);
        assertThat(this.codecs.encodeNull(RowId.class)).hasType(ROWID);
    }

    @Test
    void unsignedTypesUseWideDefaultJavaTypes() {
        Object unsignedInt = this.codecs.decode(ByteBufUtils.encode(TEST, "4294967295"),
            INT_UNSIGNED, FORMAT_TEXT, Object.class);
        BigInteger maximum = new BigInteger("18446744073709551615");
        Object unsignedBigint = this.codecs.decode(ByteBufUtils.encode(TEST, maximum.toString()),
            BIGINT_UNSIGNED, FORMAT_TEXT, Object.class);

        org.assertj.core.api.Assertions.assertThat(unsignedInt).isEqualTo(4294967295L);
        org.assertj.core.api.Assertions.assertThat(unsignedBigint).isEqualTo(maximum);
        assertThat(this.codecs.encode(maximum)).hasType(BIGINT_UNSIGNED);
        assertThat(this.codecs.encodeNull(BigInteger.class)).hasType(BIGINT_UNSIGNED);
    }

    @Test
    void compatibilityArraysUseResolvedArrayOids() {
        Double[] money = this.codecs.decode(ByteBufUtils.encode(TEST, "{12.34,56.78}"),
            MONEY_ARRAY, FORMAT_TEXT, Double[].class);
        RowId[] rowIds = this.codecs.decode(ByteBufUtils.encode(TEST, "{AA,BB}"),
            ROWID_ARRAY, FORMAT_TEXT, RowId[].class);

        org.assertj.core.api.Assertions.assertThat(money).containsExactly(12.34D, 56.78D);
        org.assertj.core.api.Assertions.assertThat(rowIds).extracting(Object::toString)
            .containsExactly("AA", "BB");
        assertThat(this.codecs.encode(money)).hasType(MONEY_ARRAY);
        assertThat(this.codecs.encode(rowIds)).hasType(ROWID_ARRAY);
    }

    @Test
    void binaryObjectDefaultsToByteBuffer() {
        Object decoded = this.codecs.decode(ByteBufUtils.encode(TEST, "\\000\\001\\377"),
            PostgresqlObjectId.BYTEA.getObjectId(), FORMAT_TEXT, Object.class);

        org.assertj.core.api.Assertions.assertThat(decoded).isInstanceOf(ByteBuffer.class);
        ByteBuffer buffer = ((ByteBuffer) decoded).duplicate();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        org.assertj.core.api.Assertions.assertThat(bytes).containsExactly(0, 1, (byte) 255);
    }

}
