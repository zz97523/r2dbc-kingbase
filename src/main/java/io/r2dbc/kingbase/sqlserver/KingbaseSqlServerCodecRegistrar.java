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

import io.netty.buffer.ByteBufAllocator;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.CodecRegistry;
import io.r2dbc.postgresql.extension.CodecRegistrar;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers SQL Server-mode codecs for KingbaseES on the PostgreSQL wire.
 * <p>
 * Loads type OIDs from {@code pg_catalog.pg_type} first (Kingbase SQL Server-mode types often use
 * non-standard OIDs such as {@code date=7944}), then registers codecs with
 * {@link CodecRegistry#addFirst}.
 */
public final class KingbaseSqlServerCodecRegistrar implements CodecRegistrar {

    @Override
    public Publisher<Void> register(PostgresqlConnection connection, ByteBufAllocator allocator, CodecRegistry registry) {
        return KingbaseSqlServerTypeOids.load(connection)
            .doOnNext(typeOids -> {
                List<Codec<?>> codecs = KingbaseSqlServerCodecs.create(allocator, typeOids);
                List<Codec<?>> preferred = new ArrayList<>();
                List<Codec<?>> others = new ArrayList<>();
                for (Codec<?> codec : codecs) {
                    if (isPreferred(codec)) {
                        preferred.add(codec);
                    } else {
                        others.add(codec);
                    }
                }
                for (int i = others.size() - 1; i >= 0; i--) {
                    registry.addFirst(others.get(i));
                }
                // Register preferred codecs last so CachedCodecLookup warms Java types before stock PG codecs.
                for (int i = preferred.size() - 1; i >= 0; i--) {
                    registry.addFirst(preferred.get(i));
                }
            })
            .then();
    }

    private static boolean isPreferred(Codec<?> codec) {
        return codec instanceof KingbaseSqlServerBooleanCodec
            || codec instanceof KingbaseSqlServerTextBinaryCodecs.StringCodec
            || codec instanceof KingbaseSqlServerTemporalCodecs.LocalDateCodec
            || codec instanceof KingbaseSqlServerTemporalCodecs.LocalDateTimeCodec
            || codec instanceof KingbaseSqlServerTemporalCodecs.LocalTimeCodec
            || codec instanceof KingbaseSqlServerLobCodecs.BlobCodec
            || codec instanceof KingbaseSqlServerTextBinaryCodecs.ByteArrayCodec
            || codec instanceof KingbaseSqlServerTextBinaryCodecs.ByteBufferCodec;
    }

}
