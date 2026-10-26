/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */

package io.r2dbc.kingbase.oracle;

import io.r2dbc.kingbase.KingbaseMoney;
import io.r2dbc.kingbase.KingbaseXml;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.DefaultCodecs;
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.postgresql.util.ByteBufUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Period;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.r2dbc.postgresql.client.ParameterAssert.assertThat;
import static io.r2dbc.postgresql.message.Format.FORMAT_BINARY;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;
import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;

final class KingbaseOracleCompatibilityCodecUnitTests {

    private static final int MONEY = 790;

    private static final int MONEY_ARRAY = 791;

    private static final int XML = 142;

    private static final int XML_ARRAY = 143;

    private static final int DSINTERVAL = 7000;

    private static final int DSINTERVAL_ARRAY = 7001;

    private static final int YMINTERVAL = 7002;

    private static final int YMINTERVAL_ARRAY = 7003;

    private static final int MACADDR = 829;

    private static final int MACADDR_ARRAY = 1040;

    private static final int PERSON = 12000;

    private static final int PERSON_ARRAY = 12001;

    private static final int BIT = PostgresqlObjectId.BIT.getObjectId();

    private DefaultCodecs codecs;

    private KingbaseOracleTypeOids typeOids;

    @BeforeEach
    void beforeEach() {
        Map<String, Integer> types = new HashMap<>();
        types.put("money", MONEY);
        types.put("_money", MONEY_ARRAY);
        types.put("xml", XML);
        types.put("_xml", XML_ARRAY);
        types.put("dsinterval", DSINTERVAL);
        types.put("_dsinterval", DSINTERVAL_ARRAY);
        types.put("yminterval", YMINTERVAL);
        types.put("_yminterval", YMINTERVAL_ARRAY);
        types.put("macaddr", MACADDR);
        types.put("_macaddr", MACADDR_ARRAY);
        types.put("bit", BIT);
        this.typeOids = KingbaseOracleTypeOids.withNamedTypes(types);
        this.typeOids.registerCompositeType("person_type", PERSON, PERSON_ARRAY);

        this.codecs = new DefaultCodecs(TEST, false);
        List<Codec<?>> oracleCodecs = KingbaseOracleCodecs.create(TEST, this.typeOids);
        for (int i = oracleCodecs.size() - 1; i >= 0; i--) {
            this.codecs.addFirst(oracleCodecs.get(i));
        }
    }

    @Test
    void moneyAndXmlUseJdbcCompatibleMappings() {
        Object money = this.codecs.decode(ByteBufUtils.encode(TEST, "￥12.34"),
            MONEY, FORMAT_TEXT, Object.class);
        BigDecimal decimal = this.codecs.decode(ByteBufUtils.encode(TEST, "￥12.34"),
            MONEY, FORMAT_TEXT, BigDecimal.class);
        Object xml = this.codecs.decode(ByteBufUtils.encode(TEST, "<a/>"),
            XML, FORMAT_TEXT, Object.class);

        org.assertj.core.api.Assertions.assertThat(money).isEqualTo(12.34D);
        org.assertj.core.api.Assertions.assertThat(decimal).isEqualTo(new BigDecimal("12.34"));
        org.assertj.core.api.Assertions.assertThat(xml).isEqualTo("<a/>");
        assertThat(this.codecs.encode(new KingbaseMoney("98.76"))).hasType(MONEY);
        assertThat(this.codecs.encode(new KingbaseXml("<root/>"))).hasType(XML);
    }

    @Test
    void specialArraysUseResolvedOidsAndJavaElementTypes() {
        Double[] money = this.codecs.decode(ByteBufUtils.encode(TEST, "{12.34,56.78}"),
            MONEY_ARRAY, FORMAT_TEXT, Double[].class);
        String[] xml = this.codecs.decode(ByteBufUtils.encode(TEST, "{\"<a/>\",\"<b/>\"}"),
            XML_ARRAY, FORMAT_TEXT, String[].class);
        Duration[] interval = this.codecs.decode(ByteBufUtils.encode(TEST,
            "{\"+000000001 02:03:04.000000000\"}"), DSINTERVAL_ARRAY, FORMAT_TEXT,
            Duration[].class);

        org.assertj.core.api.Assertions.assertThat(money).containsExactly(12.34D, 56.78D);
        org.assertj.core.api.Assertions.assertThat(xml).containsExactly("<a/>", "<b/>");
        org.assertj.core.api.Assertions.assertThat(interval)
            .containsExactly(Duration.ofHours(26).plusMinutes(3).plusSeconds(4));
        assertThat(this.codecs.encode(money)).hasType(MONEY_ARRAY);
        assertThat(this.codecs.encode(xml)).hasType(XML_ARRAY);
    }

    @Test
    void genericObjectAndArrayRetainNativeTypeOid() {
        KingbaseOracleObject value =
            new KingbaseOracleObject("macaddr", "08:00:2b:01:02:03");
        KingbaseOracleObject[] values = {value};

        assertThat(this.codecs.encode(value)).hasType(MACADDR);
        assertThat(this.codecs.encode(values)).hasType(MACADDR_ARRAY);

        Object decoded = this.codecs.decode(ByteBufUtils.encode(TEST, "08:00:2b:01:02:03"),
            MACADDR, FORMAT_TEXT, Object.class);
        org.assertj.core.api.Assertions.assertThat(decoded).isEqualTo(value);
    }

    @Test
    void structAndStructArrayUseDiscoveredCompositeOids() {
        KingbaseOracleStruct value = new KingbaseOracleStruct("person_type",
            "Alice, A.", 32, null, "quoted \"value\"");

        assertThat(this.codecs.encode(value)).hasType(PERSON);
        assertThat(this.codecs.encode(new KingbaseOracleStruct[]{value})).hasType(PERSON_ARRAY);

        KingbaseOracleStruct decoded = this.codecs.decode(
            ByteBufUtils.encode(TEST, value.getValue()), PERSON, FORMAT_TEXT,
            KingbaseOracleStruct.class);
        org.assertj.core.api.Assertions.assertThat(Arrays.asList(decoded.getAttributes()))
            .containsExactly("Alice, A.", "32", null, "quoted \"value\"");
    }

    @Test
    void bitUsesBooleanAsDefaultForTextAndBinary() {
        Object text = this.codecs.decode(ByteBufUtils.encode(TEST, "1"),
            BIT, FORMAT_TEXT, Object.class);
        Object binary = this.codecs.decode(TEST.buffer(5).writeInt(1).writeByte(0x80),
            BIT, FORMAT_BINARY, Object.class);

        org.assertj.core.api.Assertions.assertThat(text).isEqualTo(true);
        org.assertj.core.api.Assertions.assertThat(binary).isEqualTo(true);
        assertThat(new KingbaseOracleBooleanCodec(TEST, this.typeOids).encode(true, BIT))
            .hasType(BIT)
            .hasValue(ByteBufUtils.encode(TEST, "1"));
    }

    @Test
    void intervalUsesDurationAsDefaultAndResolvedOid() {
        Duration expected = Duration.ofDays(1).plusHours(2).plusMinutes(3)
            .plusSeconds(4).plusMillis(500);
        Object decoded = this.codecs.decode(ByteBufUtils.encode(TEST, "+01 02:03:04.500000"),
            DSINTERVAL, FORMAT_TEXT, Object.class);

        org.assertj.core.api.Assertions.assertThat(decoded).isEqualTo(expected);
        assertThat(this.codecs.encode(Duration.ofSeconds(1))).hasType(DSINTERVAL);
        assertThat(this.codecs.encodeNull(Duration.class)).hasType(DSINTERVAL);
    }

    @Test
    void yearMonthIntervalUsesPeriodAndNormalizedWireFormat() {
        Object decoded = this.codecs.decode(ByteBufUtils.encode(TEST, "-1-2"),
            YMINTERVAL, FORMAT_TEXT, Object.class);

        org.assertj.core.api.Assertions.assertThat(decoded).isEqualTo(Period.of(-1, -2, 0));
        assertThat(this.codecs.encode(Period.ofMonths(14)))
            .hasType(YMINTERVAL)
            .hasValue(ByteBufUtils.encode(TEST, "1-2"));
        assertThat(this.codecs.encodeNull(Period.class)).hasType(YMINTERVAL);
        assertThat(this.codecs.encode(new Period[]{Period.ofMonths(14)}))
            .hasType(YMINTERVAL_ARRAY);
    }

}
