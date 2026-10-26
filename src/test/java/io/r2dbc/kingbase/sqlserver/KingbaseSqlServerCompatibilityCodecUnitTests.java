/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.r2dbc.kingbase.sqlserver;

import io.r2dbc.kingbase.KingbaseMoney;
import io.r2dbc.kingbase.KingbaseXml;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.DefaultCodecs;
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.postgresql.util.ByteBufUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.r2dbc.postgresql.client.ParameterAssert.assertThat;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;
import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;

final class KingbaseSqlServerCompatibilityCodecUnitTests {

    private static final int MONEY = 5069;

    private static final int MONEY_ARRAY = 5070;

    private static final int XML = 7654;

    private static final int XML_ARRAY = 12082;

    private static final int DATE = 7754;

    private static final int DATE_ARRAY = 12083;

    private DefaultCodecs codecs;

    @BeforeEach
    void beforeEach() {
        Map<String, Integer> types = new HashMap<>();
        types.put("money", MONEY);
        types.put("_money", MONEY_ARRAY);
        types.put("xml", XML);
        types.put("_xml", XML_ARRAY);
        types.put("date", DATE);
        types.put("_date", DATE_ARRAY);

        this.codecs = new DefaultCodecs(TEST, false);
        List<Codec<?>> sqlServerCodecs = KingbaseSqlServerCodecs.create(
            TEST, KingbaseSqlServerTypeOids.withNamedTypes(types));
        for (int i = sqlServerCodecs.size() - 1; i >= 0; i--) {
            this.codecs.addFirst(sqlServerCodecs.get(i));
        }
    }

    @Test
    void shouldUseJdbcCompatibleMoneyAndXmlMappings() {
        Object money = this.codecs.decode(ByteBufUtils.encode(TEST, "12.34"),
            MONEY, FORMAT_TEXT, Object.class);
        BigDecimal decimal = this.codecs.decode(ByteBufUtils.encode(TEST, "12.34"),
            MONEY, FORMAT_TEXT, BigDecimal.class);
        Object xml = this.codecs.decode(ByteBufUtils.encode(TEST, "<person id=\"1\"/>"),
            XML, FORMAT_TEXT, Object.class);

        org.assertj.core.api.Assertions.assertThat(money).isEqualTo(new BigDecimal("12.34"));
        org.assertj.core.api.Assertions.assertThat(decimal).isEqualTo(new BigDecimal("12.34"));
        org.assertj.core.api.Assertions.assertThat(xml).isEqualTo("<person id=\"1\"/>");
        org.assertj.core.api.Assertions.assertThat(this.codecs.preferredType(XML, FORMAT_TEXT))
            .isEqualTo(String.class);

        assertThat(this.codecs.encode(new KingbaseMoney("98.76"))).hasType(MONEY);
        assertThat(this.codecs.encode(new KingbaseXml("<root/>"))).hasType(XML);
    }

    @Test
    void shouldDecodeAndEncodeDynamicallyResolvedArrays() {
        BigDecimal[] money = this.codecs.decode(ByteBufUtils.encode(TEST, "{12.34,56.78}"),
            MONEY_ARRAY, FORMAT_TEXT, BigDecimal[].class);
        String[] xml = this.codecs.decode(ByteBufUtils.encode(TEST, "{\"<a/>\",\"<b/>\"}"),
            XML_ARRAY, FORMAT_TEXT, String[].class);
        LocalDate[] dates = this.codecs.decode(ByteBufUtils.encode(TEST, "{2026-09-23,2026-09-24}"),
            DATE_ARRAY, FORMAT_TEXT, LocalDate[].class);

        org.assertj.core.api.Assertions.assertThat(money)
            .containsExactly(new BigDecimal("12.34"), new BigDecimal("56.78"));
        org.assertj.core.api.Assertions.assertThat(xml).containsExactly("<a/>", "<b/>");
        org.assertj.core.api.Assertions.assertThat(dates)
            .containsExactly(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 24));

        assertThat(this.codecs.encode(money)).hasType(MONEY_ARRAY);
        assertThat(this.codecs.encode(xml)).hasType(XML_ARRAY);
        assertThat(this.codecs.encode(dates)).hasType(DATE_ARRAY);
    }

    @Test
    void shouldRetainArrayOidWhenRegisteringDynamicType() {
        KingbaseSqlServerTypeOids typeOids = KingbaseSqlServerTypeOids.postgresqlDefaults();
        typeOids.registerType("money", MONEY, MONEY_ARRAY, "sys");
        typeOids.registerType("money", PostgresqlObjectId.MONEY.getObjectId(),
            PostgresqlObjectId.MONEY_ARRAY.getObjectId(), "pg_catalog");

        org.assertj.core.api.Assertions.assertThat(typeOids.oid("money")).isEqualTo(MONEY);
        org.assertj.core.api.Assertions.assertThat(typeOids.arrayOid("money")).isEqualTo(MONEY_ARRAY);
        org.assertj.core.api.Assertions.assertThat(typeOids.encodeMoney()).isEqualTo(MONEY);
    }
}
