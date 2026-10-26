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

import io.netty.buffer.ByteBufAllocator;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.CodecRegistry;
import io.r2dbc.postgresql.extension.CodecRegistrar;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Registers MySQL-mode codecs for KingbaseES on the PostgreSQL wire.
 * <p>
 * Loads type OIDs from {@code pg_catalog.pg_type} first (Kingbase MySQL-mode types often use
 * non-standard OIDs such as {@code date=7944}), then registers codecs with
 * {@link CodecRegistry#addFirst}.
 */
public final class KingbaseMysqlCodecRegistrar implements CodecRegistrar {

    @Override
    public Publisher<Void> register(PostgresqlConnection connection, ByteBufAllocator allocator, CodecRegistry registry) {
        return KingbaseMysqlTypeOids.load(connection)
            .doOnNext(typeOids -> {
                List<Codec<?>> codecs = KingbaseMysqlCodecs.create(allocator, typeOids);
                for (int i = codecs.size() - 1; i >= 0; i--) {
                    registry.addFirst(codecs.get(i));
                }
            })
            .then();
    }

}
