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

import io.netty.buffer.ByteBufAllocator;
import io.r2dbc.postgresql.codec.Codec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds the Kingbase Oracle-mode codec set for the PostgreSQL wire protocol.
 */
final class KingbaseOracleCodecs {

    private KingbaseOracleCodecs() {
    }

    static List<Codec<?>> create(ByteBufAllocator allocator) {
        return create(allocator, KingbaseOracleTypeOids.postgresqlDefaults());
    }

    static List<Codec<?>> create(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
        List<Codec<?>> codecs = new ArrayList<>();

        codecs.addAll(KingbaseOracleNumericCodecs.create(allocator, typeOids));
        codecs.add(new KingbaseOracleBooleanCodec(allocator, typeOids));
        codecs.addAll(KingbaseOracleTemporalCodecs.create(allocator, typeOids));
        codecs.add(new KingbaseOracleTextBinaryCodecs.StringCodec(allocator, typeOids));
        codecs.add(new KingbaseOracleTextBinaryCodecs.RowIdCodec(allocator, typeOids));
        codecs.add(new KingbaseOracleTextBinaryCodecs.EnumCodec(allocator, typeOids));
        codecs.addAll(KingbaseOracleLobCodecs.create(allocator, typeOids));
        codecs.add(new KingbaseOracleTextBinaryCodecs.ByteArrayCodec(allocator, typeOids));
        codecs.add(new KingbaseOracleTextBinaryCodecs.ByteBufferCodec(allocator, typeOids));
        codecs.addAll(KingbaseOracleSpecialCodecs.create(allocator, typeOids));

        return Collections.unmodifiableList(codecs);
    }

}
