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

import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;
import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;
import static org.assertj.core.api.Assertions.assertThat;

final class KingbaseOracleJdbcParityCodecUnitTests {

    private static final int VARCHAR2 = 9100;

    private static final int JSONB = 9101;

    private static final int BFILE = 9102;

    private static final int BFILE_ARRAY = 9103;

    private static final int INTERVALDS = 9104;

    private static final int INTERVALDS_ARRAY = 9105;

    private static final int INTERVALYM = 9106;

    private static final int INTERVALYM_ARRAY = 9107;

    private DefaultCodecs codecs;

    @BeforeEach
    void beforeEach() {
        Map<String, Integer> types = new HashMap<>();
        types.put("money", 790);
        types.put("_money", 791);
        types.put("xml", 142);
        types.put("_xml", 143);
        types.put("dsinterval", 7000);
        types.put("_dsinterval", 7001);
        types.put("macaddr", 829);
        types.put("_macaddr", 1040);
        types.put("varchar2", VARCHAR2);
        types.put("jsonb", JSONB);
        types.put("bfile", BFILE);
        types.put("_bfile", BFILE_ARRAY);
        types.put("intervalds", INTERVALDS);
        types.put("_intervalds", INTERVALDS_ARRAY);
        types.put("intervalym", INTERVALYM);
        types.put("_intervalym", INTERVALYM_ARRAY);
        this.codecs = new DefaultCodecs(TEST, false);
        KingbaseOracleTypeOids typeOids = KingbaseOracleTypeOids.withNamedTypes(types);
        List<Codec<?>> oracleCodecs = KingbaseOracleCodecs.create(TEST, typeOids);
        for (int i = oracleCodecs.size() - 1; i >= 0; i--) {
            this.codecs.addFirst(oracleCodecs.get(i));
        }
    }

    @Test
    void shouldExposeJdbcMoneyAsDoubleAndBigDecimal() {
        Object object = this.codecs.decode(ByteBufUtils.encode(TEST, "￥12.34"), 790,
            FORMAT_TEXT, Object.class);
        Object decimal = this.codecs.decode(ByteBufUtils.encode(TEST, "￥12.34"), 790,
            FORMAT_TEXT, BigDecimal.class);

        assertThat(object).isEqualTo(12.34D);
        assertThat(decimal).isEqualTo(new BigDecimal("12.34"));
        assertThat(this.codecs.preferredType(790, FORMAT_TEXT)).isEqualTo(Double.class);
    }

    @Test
    void shouldExposeJdbcXmlAsStringAndMetadata() {
        Object xml = this.codecs.decode(ByteBufUtils.encode(TEST, "<a/>"), 142,
            FORMAT_TEXT, Object.class);

        assertThat(xml).isEqualTo("<a/>");
        assertThat(this.codecs.preferredType(142, FORMAT_TEXT)).isEqualTo(String.class);
    }

    @Test
    void shouldWriteJdbcXmlAndMoneyWithNativeOids() {
        KingbaseOracleTextBinaryCodecs.StringCodec stringCodec =
            new KingbaseOracleTextBinaryCodecs.StringCodec(TEST, KingbaseOracleTypeOids.withNamedTypes(
                java.util.Collections.singletonMap("xml", 142)));
        io.r2dbc.postgresql.client.ParameterAssert.assertThat(stringCodec.encode("<a/>", 142))
            .hasType(142)
            .hasValue(ByteBufUtils.encode(TEST, "<a/>"));

        KingbaseOracleTypeOids typeOids = KingbaseOracleTypeOids.withNamedTypes(
            java.util.Collections.singletonMap("money", 790));
        io.r2dbc.postgresql.client.ParameterAssert.assertThat(
            new KingbaseOracleNumericCodecs.BigDecimalCodec(TEST, typeOids)
                .encode(new BigDecimal("12.34"), 790))
            .hasType(790)
            .hasValue(ByteBufUtils.encode(TEST, "12.34"));

        io.r2dbc.postgresql.client.ParameterAssert.assertThat(
            this.codecs.encode(new KingbaseXml("<root/>")))
            .hasType(142)
            .hasValue(ByteBufUtils.encode(TEST, "<root/>"));
        io.r2dbc.postgresql.client.ParameterAssert.assertThat(
            this.codecs.encode(new KingbaseMoney("98.76")))
            .hasType(790)
            .hasValue(ByteBufUtils.encode(TEST, "98.76"));
    }

    @Test
    void shouldDecodeJdbcMoneyAndXmlArrays() {
        Object money = this.codecs.decode(ByteBufUtils.encode(TEST, "{12.34,56.78}"),
            791, FORMAT_TEXT, Double[].class);
        Object xml = this.codecs.decode(ByteBufUtils.encode(TEST, "{\"<a/>\",\"<b/>\"}"),
            143, FORMAT_TEXT, String[].class);

        assertThat(money).isInstanceOf(Double[].class);
        assertThat((Double[]) money).containsExactly(12.34D, 56.78D);
        assertThat((String[]) xml).containsExactly("<a/>", "<b/>");
    }

    @Test
    void shouldNotInferXmlOrMoneyFromOrdinaryJavaArrays() {
        io.r2dbc.postgresql.client.ParameterAssert.assertThat(
            this.codecs.encode(new String[] { "first", "second" }))
            .hasType(PostgresqlObjectId.TEXT_ARRAY.getObjectId());
        io.r2dbc.postgresql.client.ParameterAssert.assertThat(
            this.codecs.encode(new Double[] { 1.25d, 2.5d }))
            .hasType(PostgresqlObjectId.FLOAT8_ARRAY.getObjectId());
    }

    @Test
    void shouldDecodeIntervalArrayWithJavaElementType() {
        Object value = this.codecs.decode(ByteBufUtils.encode(TEST, "{\"+000000001 02:03:04.000000000\"}"),
            7001, FORMAT_TEXT, Duration[].class);

        assertThat(value).isInstanceOf(Duration[].class);
        assertThat(((Duration[]) value)[0]).isEqualTo(Duration.ofHours(26).plusMinutes(3).plusSeconds(4));
    }

    @Test
    void shouldBindJdbcObjectStyleExtensionValueWithItsNativeOid() {
        KingbaseOracleObject value = new KingbaseOracleObject("macaddr", "08:00:2b:01:02:03");

        io.r2dbc.postgresql.client.ParameterAssert.assertThat(this.codecs.encode(value))
            .hasType(829)
            .hasValue(ByteBufUtils.encode(TEST, "08:00:2b:01:02:03"));
    }

    @Test
    void shouldEncodeAndDecodeJdbcStructStyleValues() {
        KingbaseOracleStruct encoded = new KingbaseOracleStruct("person_type",
            "Alice, A.", 32, null, "quoted \"value\"");
        KingbaseOracleStruct decoded = KingbaseOracleStruct.decode("person_type", encoded.getValue());

        assertThat(encoded.getValue()).isEqualTo("(\"Alice, A.\",32,,\"quoted \\\"value\\\"\")");
        assertThat(Arrays.asList(decoded.getAttributes()))
            .containsExactly("Alice, A.", "32", null, "quoted \"value\"");
    }

    @Test
    void shouldNotInferJsonFromOrdinaryStringContent() {
        io.r2dbc.postgresql.client.ParameterAssert.assertThat(
            this.codecs.encode("{not necessarily json}"))
            .hasType(VARCHAR2)
            .hasValue(ByteBufUtils.encode(TEST, "{not necessarily json}"));
    }

    @Test
    void shouldHandleDynamicJsonbAsTypedOracleObject() {
        Object value = this.codecs.decode(ByteBufUtils.encode(TEST, "{\"value\":1}"),
            JSONB, FORMAT_TEXT, Object.class);
        String text = this.codecs.decode(ByteBufUtils.encode(TEST, "{\"value\":1}"),
            JSONB, FORMAT_TEXT, String.class);

        assertThat(value).isEqualTo(new KingbaseOracleObject("jsonb", "{\"value\":1}"));
        assertThat(text).isEqualTo("{\"value\":1}");
        io.r2dbc.postgresql.client.ParameterAssert.assertThat(
            this.codecs.encode(new KingbaseOracleObject("jsonb", "{\"write\":true}")))
            .hasType(JSONB)
            .hasValue(ByteBufUtils.encode(TEST, "{\"write\":true}"));
    }

    @Test
    void shouldTreatBfileAsReadOnlyLocator() {
        Object value = this.codecs.decode(ByteBufUtils.encode(TEST, "DIR/file.dat"),
            BFILE, FORMAT_TEXT, Object.class);

        assertThat(value).isEqualTo(new KingbaseOracleObject("bfile", "DIR/file.dat"));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
            .isThrownBy(() -> this.codecs.encode(
                new KingbaseOracleObject("bfile", "DIR/file.dat")));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
            .isThrownBy(() -> this.codecs.encode(new KingbaseOracleObject[] {
                new KingbaseOracleObject("bfile", "DIR/file.dat")
            }));
    }

    @Test
    void shouldDecodeBothOracleIntervalArrayAliases() {
        Duration[] durations = this.codecs.decode(
            ByteBufUtils.encode(TEST, "{\"+1 02:03:04\"}"),
            INTERVALDS_ARRAY, FORMAT_TEXT, Duration[].class);
        Period[] periods = this.codecs.decode(ByteBufUtils.encode(TEST, "{\"1-2\"}"),
            INTERVALYM_ARRAY, FORMAT_TEXT, Period[].class);

        assertThat(durations).containsExactly(
            Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4));
        assertThat(periods).containsExactly(Period.of(1, 2, 0));
    }

}
