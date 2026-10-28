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

package io.r2dbc.kingbase.oracle;

import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.DefaultCodecs;
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.postgresql.util.ByteBufUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.r2dbc.postgresql.client.ParameterAssert.assertThat;
import static io.r2dbc.postgresql.message.Format.FORMAT_BINARY;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;
import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;

final class KingbaseOracleOfficialTypeCodecUnitTests {

    private static final int DSINTERVAL = 7000;

    private static final int BIT = PostgresqlObjectId.BIT.getObjectId();

    private DefaultCodecs codecs;

    private KingbaseOracleTypeOids typeOids;

    @BeforeEach
    void beforeEach() {
        Map<String, Integer> types = new HashMap<>();
        types.put("dsinterval", DSINTERVAL);
        types.put("bit", BIT);
        this.typeOids = KingbaseOracleTypeOids.withNamedTypes(types);

        this.codecs = new DefaultCodecs(TEST, false);
        List<Codec<?>> oracleCodecs = KingbaseOracleCodecs.create(TEST, this.typeOids);
        for (int i = oracleCodecs.size() - 1; i >= 0; i--) {
            this.codecs.addFirst(oracleCodecs.get(i));
        }
    }

    @Test
    void shouldUseBooleanAsDefaultJavaTypeForOracleBit() {
        Object zero = this.codecs.decode(ByteBufUtils.encode(TEST, "0"), BIT, FORMAT_TEXT, Object.class);
        Object one = this.codecs.decode(ByteBufUtils.encode(TEST, "1"), BIT, FORMAT_TEXT, Object.class);

        org.assertj.core.api.Assertions.assertThat(zero).isEqualTo(false);
        org.assertj.core.api.Assertions.assertThat(one).isEqualTo(true);
        org.assertj.core.api.Assertions.assertThat(this.codecs.preferredType(BIT, FORMAT_TEXT))
            .isEqualTo(Boolean.class);
    }

    @Test
    void shouldDecodeOracleBitBinaryFormat() {
        Object decoded = this.codecs.decode(TEST.buffer(5).writeInt(1).writeByte(0x80),
            BIT, FORMAT_BINARY, Object.class);

        org.assertj.core.api.Assertions.assertThat(decoded).isEqualTo(true);
    }

    @Test
    void shouldEncodeOracleBitAsZeroOrOne() {
        KingbaseOracleBooleanCodec codec = new KingbaseOracleBooleanCodec(TEST, this.typeOids);

        assertThat(codec.encode(true, BIT))
            .hasType(BIT)
            .hasFormat(FORMAT_TEXT)
            .hasValue(ByteBufUtils.encode(TEST, "1"));
    }

    @Test
    void shouldUseDurationAsDefaultJavaTypeForDsInterval() {
        Duration expected = Duration.ofDays(1).plusHours(2).plusMinutes(3)
            .plusSeconds(4).plusMillis(500);
        Object decoded = this.codecs.decode(ByteBufUtils.encode(TEST, "+01 02:03:04.500000"),
            DSINTERVAL, FORMAT_TEXT, Object.class);

        org.assertj.core.api.Assertions.assertThat(decoded).isEqualTo(expected);
        org.assertj.core.api.Assertions.assertThat(this.codecs.preferredType(DSINTERVAL, FORMAT_TEXT))
            .isEqualTo(Duration.class);
    }

    @Test
    void shouldEncodeDsIntervalWithResolvedOid() {
        assertThat(this.codecs.encode(Duration.ofSeconds(1))).hasType(DSINTERVAL);
        assertThat(this.codecs.encodeNull(Duration.class)).hasType(DSINTERVAL);
    }
}
