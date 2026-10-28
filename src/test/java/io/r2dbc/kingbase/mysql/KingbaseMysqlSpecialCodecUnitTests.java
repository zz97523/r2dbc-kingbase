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

package io.r2dbc.kingbase.mysql;

import io.netty.buffer.ByteBuf;
import io.r2dbc.kingbase.KingbaseXml;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.DefaultCodecs;
import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.postgresql.codec.Point;
import io.r2dbc.postgresql.util.ByteBufUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.r2dbc.postgresql.message.Format.FORMAT_BINARY;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;
import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;
import static org.assertj.core.api.Assertions.assertThat;

final class KingbaseMysqlSpecialCodecUnitTests {

    private static final int ENUM = 9101;

    private static final int SET = 9102;

    private static final int JSON = 9103;

    private static final int JSONB = 9104;

    private static final int XML = 9105;

    private static final int POINT = 9106;

    private static final int POINT_ARRAY = 9107;

    private DefaultCodecs codecs;

    @BeforeEach
    void beforeEach() {
        Map<String, Integer> types = new HashMap<>();
        types.put("enum", ENUM);
        types.put("set", SET);
        types.put("json", JSON);
        types.put("jsonb", JSONB);
        types.put("xml", XML);
        types.put("point", POINT);
        types.put("_point", POINT_ARRAY);

        KingbaseMysqlTypeOids typeOids = KingbaseMysqlTypeOids.withNamedTypes(types);
        assertThat(typeOids.isEnum(ENUM)).isTrue();
        assertThat(typeOids.isSet(SET)).isTrue();
        assertThat(typeOids.isJson(JSON)).isTrue();
        assertThat(typeOids.isJsonb(JSONB)).isTrue();
        assertThat(typeOids.isXml(XML)).isTrue();
        assertThat(typeOids.isPoint(POINT)).isTrue();

        this.codecs = new DefaultCodecs(TEST, false);
        List<Codec<?>> mysqlCodecs = KingbaseMysqlCodecs.create(TEST, typeOids);
        for (int i = mysqlCodecs.size() - 1; i >= 0; i--) {
            this.codecs.addFirst(mysqlCodecs.get(i));
        }
    }

    @Test
    void shouldHandleNativeEnumAndSetOids() {
        MysqlValue enumValue = this.codecs.decode(ByteBufUtils.encode(TEST, "BLUE"), ENUM,
            FORMAT_TEXT, MysqlValue.class);
        String[] setArray = this.codecs.decode(ByteBufUtils.encode(TEST, "red,blue"), SET,
            FORMAT_TEXT, String[].class);
        Set<?> set = this.codecs.decode(ByteBufUtils.encode(TEST, "red,blue"), SET,
            FORMAT_TEXT, Set.class);

        assertThat(enumValue).isEqualTo(MysqlValue.BLUE);
        assertThat(setArray).containsExactly("red", "blue");
        assertThat(set).isEqualTo(new LinkedHashSet<>(java.util.Arrays.asList("red", "blue")));
        io.r2dbc.postgresql.client.ParameterAssert.assertThat(
            this.codecs.encode(new LinkedHashSet<>(java.util.Arrays.asList("red", "blue"))))
            .hasValue(ByteBufUtils.encode(TEST, "red,blue"));
    }

    @Test
    void shouldHandleDynamicJsonAndJsonbOids() {
        Json json = (Json) this.codecs.decode(ByteBufUtils.encode(TEST, "{\"name\":\"kingbase\"}"),
            JSON, FORMAT_TEXT, Object.class);
        String jsonText = this.codecs.decode(ByteBufUtils.encode(TEST, "[1,2]"), JSON,
            FORMAT_TEXT, String.class);
        ByteBuf binaryJsonb = TEST.buffer().writeByte(1)
            .writeBytes("{\"ok\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Json jsonb = this.codecs.decode(binaryJsonb, JSONB, FORMAT_BINARY, Json.class);

        assertThat(json.asString()).isEqualTo("{\"name\":\"kingbase\"}");
        assertThat(jsonText).isEqualTo("[1,2]");
        assertThat(jsonb.asString()).isEqualTo("{\"ok\":true}");
        io.r2dbc.postgresql.client.ParameterAssert.assertThat(
            this.codecs.encode(Json.of("{\"write\":true}")))
            .hasType(JSONB)
            .hasValue(ByteBufUtils.encode(TEST, "{\"write\":true}"));
    }

    @Test
    void shouldHandleXmlAndPointWithResolvedOids() {
        Object xml = this.codecs.decode(ByteBufUtils.encode(TEST, "<root/>"), XML,
            FORMAT_TEXT, Object.class);
        Point point = (Point) this.codecs.decode(ByteBufUtils.encode(TEST, "(1.5,-2.25)"), POINT,
            FORMAT_TEXT, Object.class);
        Point[] points = this.codecs.decode(ByteBufUtils.encode(TEST, "{\"(1,2)\",\"(3,4)\"}"),
            POINT_ARRAY, FORMAT_TEXT, Point[].class);

        assertThat(xml).isEqualTo("<root/>");
        assertThat(point).isEqualTo(Point.of(1.5, -2.25));
        assertThat(points).containsExactly(Point.of(1, 2), Point.of(3, 4));
        io.r2dbc.postgresql.client.ParameterAssert.assertThat(
            this.codecs.encode(new KingbaseXml("<write/>")))
            .hasType(XML)
            .hasValue(ByteBufUtils.encode(TEST, "<write/>"));
        io.r2dbc.postgresql.client.ParameterAssert.assertThat(this.codecs.encode(Point.of(5, 6)))
            .hasType(POINT);
    }

    private enum MysqlValue {
        RED, BLUE
    }
}
