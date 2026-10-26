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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.r2dbc.postgresql.client.EncodedParameter;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.postgresql.util.ByteBufUtils;
import io.r2dbc.spi.Blob;
import io.r2dbc.spi.Clob;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BYTEA;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.encodeText;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.nullParameter;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.resolveBinaryBindOid;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.resolveBindOid;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.resolveTextBindOid;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerTypeOids.equalsPg;
import static io.r2dbc.postgresql.message.Format.FORMAT_BINARY;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;

/**
 * Clob / Blob codecs for Kingbase SQL Server mode on the PostgreSQL wire.
 */
final class KingbaseSqlServerLobCodecs {

    private KingbaseSqlServerLobCodecs() {
    }

    static List<Codec<?>> create(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
        List<Codec<?>> list = new ArrayList<>(2);
        list.add(new ClobCodec(allocator, typeOids));
        list.add(new BlobCodec(allocator, typeOids));
        return list;
    }

    /**
     * Large text as {@link Clob} (TEXT/VARCHAR on PG wire).
     */
    static final class ClobCodec extends KingbaseSqlServerCodecSupport.AbstractSqlServerCodec<Clob> {

        ClobCodec(ByteBufAllocator allocator) {
            this(allocator, KingbaseSqlServerTypeOids.postgresqlDefaults());
        }

        ClobCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, Clob.class, KingbaseSqlServerType.TEXT, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == Clob.class
                && this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isText(dataType) || this.typeOids.isBinary(dataType));
        }

        @Override
        Clob doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Clob> type) {
            String text;
            if (equalsPg(dataType, BYTEA) || this.typeOids.isBinary(dataType)) {
                text = new String(readBytes(buffer, format), StandardCharsets.UTF_8);
            } else {
                text = ByteBufUtils.decode(buffer);
            }
            return Clob.from(Mono.just(text));
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeText());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            Clob clob = (Clob) value;
            int oid = resolveTextBindOid(dataType, this.typeOids.encodeText());
            return new EncodedParameter(FORMAT_TEXT, oid,
                Flux.from(clob.stream())
                    .reduce(new StringBuilder(), StringBuilder::append)
                    .defaultIfEmpty(new StringBuilder())
                    .map(sb -> ByteBufUtils.encode(this.allocator, sb.toString()))
                    .concatWith(Flux.from(clob.discard()).then(Mono.empty())));
        }
    }

    /**
     * Large binary as {@link Blob} (BYTEA on PG wire).
     */
    static final class BlobCodec extends KingbaseSqlServerCodecSupport.AbstractSqlServerCodec<Blob> {

        BlobCodec(ByteBufAllocator allocator) {
            this(allocator, KingbaseSqlServerTypeOids.postgresqlDefaults());
        }

        BlobCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, Blob.class, KingbaseSqlServerType.VARBINARY, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == Blob.class
                && this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isBinary(dataType) || this.typeOids.isText(dataType));
        }

        @Override
        Blob doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Blob> type) {
            byte[] bytes = readBytes(buffer, format);
            return Blob.from(Mono.just(ByteBuffer.wrap(bytes)));
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeBinary());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            Blob blob = (Blob) value;
            int oid = resolveBinaryBindOid(dataType, this.typeOids.encodeBinary());
            return new EncodedParameter(FORMAT_BINARY, oid,
                Flux.from(blob.stream())
                    .collectList()
                    .flatMapMany(chunks -> {
                        int size = 0;
                        for (ByteBuffer chunk : chunks) {
                            size += chunk.remaining();
                        }
                        byte[] bytes = new byte[size];
                        int offset = 0;
                        for (ByteBuffer chunk : chunks) {
                            int len = chunk.remaining();
                            chunk.get(bytes, offset, len);
                            offset += len;
                        }
                        return Flux.just(this.allocator.buffer(bytes.length).writeBytes(bytes))
                            .concatWith(Flux.from(blob.discard()).then(Mono.empty()));
                    }));
        }

        @Override
        public EncodedParameter encodeNull() {
            return nullParameter(this.typeOids.encodeBinary());
        }
    }

    private static byte[] readBytes(ByteBuf buffer, Format format) {
        if (format == FORMAT_BINARY) {
            return ByteBufUtil.getBytes(buffer);
        }
        return KingbaseSqlServerCodecSupport.decodeBinaryText(ByteBufUtils.decode(buffer));
    }

}
