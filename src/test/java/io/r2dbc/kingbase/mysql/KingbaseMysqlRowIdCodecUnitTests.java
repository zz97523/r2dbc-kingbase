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

import java.sql.RowId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;
import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;
import static org.assertj.core.api.Assertions.assertThat;

final class KingbaseMysqlRowIdCodecUnitTests {

    private static final int ROWID = 6123;

    private DefaultCodecs codecs;

    @BeforeEach
    void beforeEach() {
        Map<String, Integer> types = new HashMap<>();
        types.put("rowid", ROWID);
        KingbaseMysqlTypeOids typeOids = KingbaseMysqlTypeOids.withNamedTypes(types);

        this.codecs = new DefaultCodecs(TEST, false);
        List<Codec<?>> mysqlCodecs = KingbaseMysqlCodecs.create(TEST, typeOids);
        for (int i = mysqlCodecs.size() - 1; i >= 0; i--) {
            this.codecs.addFirst(mysqlCodecs.get(i));
        }
    }

    @Test
    void shouldDecodeRowIdUsingKingbaseOid() {
        RowId decoded = this.codecs.decode(ByteBufUtils.encode(TEST, "AAAPecAABAAABxQAAA"),
            ROWID, FORMAT_TEXT, RowId.class);

        assertThat(decoded).isInstanceOf(KingbaseMysqlRowId.class);
        assertThat(decoded.toString()).isEqualTo("AAAPecAABAAABxQAAA");
    }

    @Test
    void shouldEncodeRowIdUsingResolvedKingbaseOid() {
        RowId value = new KingbaseMysqlRowId("AAAPecAABAAABxQAAA");

        io.r2dbc.postgresql.client.ParameterAssert.assertThat(this.codecs.encode(value))
            .hasType(ROWID)
            .hasValue(ByteBufUtils.encode(TEST, "AAAPecAABAAABxQAAA"));
        io.r2dbc.postgresql.client.ParameterAssert.assertThat(this.codecs.encodeNull(RowId.class))
            .hasType(ROWID);
    }

}
