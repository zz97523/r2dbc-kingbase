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

import io.r2dbc.kingbase.KingbaseMoney;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.DefaultCodecs;
import io.r2dbc.postgresql.util.ByteBufUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.RowId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;
import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;
import static org.assertj.core.api.Assertions.assertThat;

final class KingbaseMysqlMoneyAndArrayCodecUnitTests {

    private static final int MONEY = 790;

    private static final int MONEY_ARRAY = 791;

    private static final int ROWID = 6123;

    private static final int ROWID_ARRAY = 6124;

    private DefaultCodecs codecs;

    @BeforeEach
    void beforeEach() {
        Map<String, Integer> types = new HashMap<>();
        types.put("money", MONEY);
        types.put("_money", MONEY_ARRAY);
        types.put("rowid", ROWID);
        types.put("_rowid", ROWID_ARRAY);

        this.codecs = new DefaultCodecs(TEST, false);
        List<Codec<?>> mysqlCodecs = KingbaseMysqlCodecs.create(TEST, KingbaseMysqlTypeOids.withNamedTypes(types));
        for (int i = mysqlCodecs.size() - 1; i >= 0; i--) {
            this.codecs.addFirst(mysqlCodecs.get(i));
        }
    }

    @Test
    void shouldReadMoneyAsJdbcCompatibleTypes() {
        assertThat(this.codecs.decode(ByteBufUtils.encode(TEST, "￥12.34"), MONEY,
            FORMAT_TEXT, Object.class)).isEqualTo(12.34D);
        assertThat(this.codecs.decode(ByteBufUtils.encode(TEST, "￥12.34"), MONEY,
            FORMAT_TEXT, BigDecimal.class)).isEqualTo(new BigDecimal("12.34"));
        io.r2dbc.postgresql.client.ParameterAssert.assertThat(
            this.codecs.encode(new KingbaseMoney("98.76")))
            .hasType(MONEY)
            .hasValue(ByteBufUtils.encode(TEST, "98.76"));
    }

    @Test
    void shouldDecodeAndEncodeResolvedCompatibilityArrays() {
        Double[] money = this.codecs.decode(ByteBufUtils.encode(TEST, "{12.34,56.78}"),
            MONEY_ARRAY, FORMAT_TEXT, Double[].class);
        RowId[] rowIds = this.codecs.decode(ByteBufUtils.encode(TEST, "{AA,BB}"),
            ROWID_ARRAY, FORMAT_TEXT, RowId[].class);

        assertThat(money).containsExactly(12.34D, 56.78D);
        assertThat(rowIds[0].toString()).isEqualTo("AA");
        assertThat(rowIds[1].toString()).isEqualTo("BB");

        io.r2dbc.postgresql.client.ParameterAssert.assertThat(this.codecs.encode(money))
            .hasType(MONEY_ARRAY);
    }

}
