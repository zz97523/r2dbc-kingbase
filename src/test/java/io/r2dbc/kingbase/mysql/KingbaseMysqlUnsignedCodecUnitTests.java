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

import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.DefaultCodecs;
import io.r2dbc.postgresql.util.ByteBufUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.r2dbc.postgresql.client.ParameterAssert.assertThat;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;
import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;

final class KingbaseMysqlUnsignedCodecUnitTests {

    private static final int INT_UNSIGNED = 7082;

    private static final int BIGINT_UNSIGNED = 7084;

    private DefaultCodecs codecs;

    private KingbaseMysqlTypeOids typeOids;

    @BeforeEach
    void beforeEach() {
        Map<String, Integer> types = new HashMap<>();
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
    void shouldUseLongAsDefaultJavaTypeForIntUnsigned() {
        Object decoded = this.codecs.decode(ByteBufUtils.encode(TEST, "4294967295"),
            INT_UNSIGNED, FORMAT_TEXT, Object.class);

        org.assertj.core.api.Assertions.assertThat(decoded)
            .isEqualTo(4294967295L)
            .isInstanceOf(Long.class);
        org.assertj.core.api.Assertions.assertThat(this.codecs.preferredType(INT_UNSIGNED, FORMAT_TEXT))
            .isEqualTo(Long.class);
    }

    @Test
    void shouldUseBigIntegerAsDefaultJavaTypeForBigintUnsigned() {
        BigInteger expected = new BigInteger("18446744073709551615");
        Object decoded = this.codecs.decode(ByteBufUtils.encode(TEST, expected.toString()),
            BIGINT_UNSIGNED, FORMAT_TEXT, Object.class);

        org.assertj.core.api.Assertions.assertThat(decoded)
            .isEqualTo(expected)
            .isInstanceOf(BigInteger.class);
        org.assertj.core.api.Assertions.assertThat(this.codecs.preferredType(BIGINT_UNSIGNED, FORMAT_TEXT))
            .isEqualTo(BigInteger.class);
    }

    @Test
    void shouldEncodeUnsignedValuesWithResolvedOids() {
        KingbaseMysqlNumericCodecs.LongCodec longCodec =
            new KingbaseMysqlNumericCodecs.LongCodec(TEST, this.typeOids);

        assertThat(longCodec.encode(4294967295L, INT_UNSIGNED)).hasType(INT_UNSIGNED);
        assertThat(this.codecs.encode(new BigInteger("18446744073709551615")))
            .hasType(BIGINT_UNSIGNED);
        assertThat(this.codecs.encodeNull(BigInteger.class)).hasType(BIGINT_UNSIGNED);
    }
}
