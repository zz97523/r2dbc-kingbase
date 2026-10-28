/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

import java.nio.ByteBuffer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.r2dbc.postgresql.client.ParameterAssert.assertThat;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;
import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;

final class KingbaseSqlServerCodecUnitTests {

    private static final int DATE = 7754;

    private static final int TIME = 7755;

    private static final int DATETIME2 = 7690;

    private static final int VARBINARY = 5024;

    private static final int ROWVERSION = 5043;

    private static final int BIT = 7915;

    private static final int TINYINT = 5026;

    private static final int XML = 7654;

    private static final int XML_ARRAY = 12082;

    private static final int MONEY = 5069;

    private static final int MONEY_ARRAY = 5070;

    private static final int SMALLMONEY = 15069;

    private static final int DECIMAL = 5067;

    private static final int DECIMAL_ARRAY = 15067;

    private static final int VARCHARBYTE = 8018;

    private static final int VARCHARBYTE_ARRAY = 8019;

    private static final int NVARCHAR = 5041;

    private static final int NVARCHAR_ARRAY = 5042;

    private DefaultCodecs codecs;

    @BeforeEach
    void beforeEach() {
        Map<String, Integer> types = new HashMap<>();
        types.put("date", DATE);
        types.put("time", TIME);
        types.put("datetime2", DATETIME2);
        types.put("varbinary", VARBINARY);
        types.put("timestamp", ROWVERSION);
        types.put("bit", BIT);
        types.put("tinyint", TINYINT);
        types.put("xml", XML);
        types.put("_xml", XML_ARRAY);
        types.put("money", MONEY);
        types.put("_money", MONEY_ARRAY);
        types.put("smallmoney", SMALLMONEY);
        types.put("decimal", DECIMAL);
        types.put("_decimal", DECIMAL_ARRAY);
        types.put("varcharbyte", VARCHARBYTE);
        types.put("_varcharbyte", VARCHARBYTE_ARRAY);
        types.put("nvarchar", NVARCHAR);
        types.put("_nvarchar", NVARCHAR_ARRAY);

        this.codecs = new DefaultCodecs(TEST, false);
        List<Codec<?>> sqlServerCodecs = KingbaseSqlServerCodecs.create(
            TEST, KingbaseSqlServerTypeOids.withNamedTypes(types));
        for (int i = sqlServerCodecs.size() - 1; i >= 0; i--) {
            this.codecs.addFirst(sqlServerCodecs.get(i));
        }
    }

    @Test
    void shouldEncodeTemporalNullsWithResolvedSqlServerOids() {
        assertThat(this.codecs.encodeNull(LocalDate.class)).hasType(DATE);
        assertThat(this.codecs.encodeNull(LocalTime.class)).hasType(TIME);
        assertThat(this.codecs.encodeNull(LocalDateTime.class)).hasType(DATETIME2);
    }

    @Test
    void shouldEncodeBinaryNullWithResolvedSqlServerOid() {
        assertThat(this.codecs.encodeNull(ByteBuffer.class)).hasType(VARBINARY);
    }

    @Test
    void shouldUseBooleanAsDefaultJavaTypeForSqlServerBit() {
        Object zero = this.codecs.decode(ByteBufUtils.encode(TEST, "0"), BIT, FORMAT_TEXT, Object.class);
        Object one = this.codecs.decode(ByteBufUtils.encode(TEST, "1"), BIT, FORMAT_TEXT, Object.class);

        org.assertj.core.api.Assertions.assertThat(zero).isEqualTo(false);
        org.assertj.core.api.Assertions.assertThat(one).isEqualTo(true);
    }

    @Test
    void shouldUseJdbcCompatibleDefaultJavaTypeForDynamicNumericOid() {
        Object value = this.codecs.decode(ByteBufUtils.encode(TEST, "255"),
            TINYINT, FORMAT_TEXT, Object.class);

        org.assertj.core.api.Assertions.assertThat(value)
            .isEqualTo(Short.valueOf((short) 255))
            .isInstanceOf(Short.class);
        org.assertj.core.api.Assertions.assertThat(this.codecs.preferredType(TINYINT, FORMAT_TEXT))
            .isEqualTo(Short.class);
    }

    @Test
    void shouldNotUseBooleanAsDefaultJavaTypeForOtherSqlServerTypes() {
        Object text = this.codecs.decode(ByteBufUtils.encode(TEST, "kingbase"),
            PostgresqlObjectId.VARCHAR.getObjectId(), FORMAT_TEXT, Object.class);
        Object number = this.codecs.decode(ByteBufUtils.encode(TEST, "7"),
            PostgresqlObjectId.INT4.getObjectId(), FORMAT_TEXT, Object.class);
        Object unknownText = this.codecs.decode(ByteBufUtils.encode(TEST, "kingbase"),
            NVARCHAR, FORMAT_TEXT, Object.class);
        Object varchar = this.codecs.decode(ByteBufUtils.encode(TEST, "varcharbyte"),
            VARCHARBYTE, FORMAT_TEXT, Object.class);

        org.assertj.core.api.Assertions.assertThat(text).isEqualTo("kingbase");
        org.assertj.core.api.Assertions.assertThat(number).isEqualTo(7);
        org.assertj.core.api.Assertions.assertThat(unknownText).isEqualTo("kingbase");
        org.assertj.core.api.Assertions.assertThat(varchar).isEqualTo("varcharbyte");
        org.assertj.core.api.Assertions.assertThat(this.codecs.preferredType(VARCHARBYTE, FORMAT_TEXT))
            .isEqualTo(String.class);
    }

    @Test
    void shouldDecodeAndPreferStringForSqlServerXml() {
        Object decoded = this.codecs.decode(ByteBufUtils.encode(TEST, "<person id=\"1\"/>"),
            XML, FORMAT_TEXT, Object.class);

        org.assertj.core.api.Assertions.assertThat(decoded)
            .isEqualTo("<person id=\"1\"/>")
            .isInstanceOf(String.class);
        org.assertj.core.api.Assertions.assertThat(this.codecs.preferredType(XML, FORMAT_TEXT))
            .isEqualTo(String.class);
    }

    @Test
    void shouldEncodeExplicitlyTypedSqlServerXmlWithResolvedOid() {
        KingbaseSqlServerTextBinaryCodecs.StringCodec stringCodec =
            new KingbaseSqlServerTextBinaryCodecs.StringCodec(TEST,
                KingbaseSqlServerTypeOids.withNamedTypes(java.util.Collections.singletonMap("xml", XML)));
        assertThat(stringCodec.encode("<person/>", XML))
            .hasType(XML)
            .hasValue(ByteBufUtils.encode(TEST, "<person/>"));
    }

    @Test
    void shouldReadSqlServerMoneyAsJdbcCompatibleTypes() {
        Object value = this.codecs.decode(ByteBufUtils.encode(TEST, "12.34"),
            MONEY, FORMAT_TEXT, Object.class);
        BigDecimal decimal = this.codecs.decode(ByteBufUtils.encode(TEST, "12.34"),
            MONEY, FORMAT_TEXT, BigDecimal.class);

        org.assertj.core.api.Assertions.assertThat(value).isEqualTo(new BigDecimal("12.34"));
        org.assertj.core.api.Assertions.assertThat(decimal).isEqualTo(new BigDecimal("12.34"));
        Object smallMoney = this.codecs.decode(ByteBufUtils.encode(TEST, "5.67"),
            SMALLMONEY, FORMAT_TEXT, Object.class);
        org.assertj.core.api.Assertions.assertThat(smallMoney)
            .isEqualTo(new BigDecimal("5.67"))
            .isInstanceOf(BigDecimal.class);
        assertThat(this.codecs.encode(new KingbaseMoney("98.76")))
            .hasType(MONEY)
            .hasValue(ByteBufUtils.encode(TEST, "98.76"));
        assertThat(this.codecs.encode(new KingbaseXml("<root/>")))
            .hasType(XML)
            .hasValue(ByteBufUtils.encode(TEST, "<root/>"));
    }

    @Test
    void shouldDecodeSpecialArraysWithoutHijackingOrdinaryJavaArrays() {
        BigDecimal[] money = this.codecs.decode(ByteBufUtils.encode(TEST, "{12.34,56.78}"),
            MONEY_ARRAY, FORMAT_TEXT, BigDecimal[].class);
        String[] xml = this.codecs.decode(ByteBufUtils.encode(TEST, "{\"<a/>\",\"<b/>\"}"),
            XML_ARRAY, FORMAT_TEXT, String[].class);

        org.assertj.core.api.Assertions.assertThat(money)
            .containsExactly(new BigDecimal("12.34"), new BigDecimal("56.78"));
        org.assertj.core.api.Assertions.assertThat(xml).containsExactly("<a/>", "<b/>");
        // Java component type alone cannot distinguish MONEY[] from DECIMAL[] or XML[] from VARCHAR[].
        assertThat(this.codecs.encode(money)).hasType(DECIMAL_ARRAY);
        assertThat(this.codecs.encode(xml)).hasType(NVARCHAR_ARRAY);
        assertThat(this.codecs.encode(new KingbaseMoney[]{
            new KingbaseMoney("12.34"), new KingbaseMoney("56.78")})).hasType(MONEY_ARRAY);
        assertThat(this.codecs.encode(new KingbaseXml[]{
            new KingbaseXml("<a/>"), new KingbaseXml("<b/>")})).hasType(XML_ARRAY);
    }

    @Test
    void shouldTreatSqlServerTimestampAsBinaryRowVersion() {
        byte[] expected = new byte[]{0, 0, 0, 1, 0, 0, 0, 2};
        Object decoded = this.codecs.decode(ByteBufUtils.encode(TEST, "\\x0000000100000002"),
            ROWVERSION, FORMAT_TEXT, Object.class);

        org.assertj.core.api.Assertions.assertThat(decoded)
            .isInstanceOf(byte[].class)
            .isEqualTo(expected);
        org.assertj.core.api.Assertions.assertThat(this.codecs.preferredType(ROWVERSION, FORMAT_TEXT))
            .isEqualTo(byte[].class);
    }

    @Test
    void shouldKeepPgTimestampTemporalAndSqlServerMoneyOidWhenCatalogRowsAreLoadedLater() {
        KingbaseSqlServerTypeOids typeOids = KingbaseSqlServerTypeOids.postgresqlDefaults();
        typeOids.registerType("timestamp", ROWVERSION, "sys");
        typeOids.registerType("money", MONEY, "sys");
        typeOids.registerType("timestamp", PostgresqlObjectId.TIMESTAMP.getObjectId(), "pg_catalog");
        typeOids.registerType("money", PostgresqlObjectId.MONEY.getObjectId(), "pg_catalog");

        org.assertj.core.api.Assertions.assertThat(typeOids.isRowVersion(ROWVERSION)).isTrue();
        org.assertj.core.api.Assertions.assertThat(typeOids.isTimestamp(ROWVERSION)).isFalse();
        org.assertj.core.api.Assertions.assertThat(typeOids.isTimestamp(
            PostgresqlObjectId.TIMESTAMP.getObjectId())).isTrue();
        org.assertj.core.api.Assertions.assertThat(typeOids.encodeMoney()).isEqualTo(MONEY);
    }
}
