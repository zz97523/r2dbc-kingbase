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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.r2dbc.postgresql.client.EncodedParameter;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.CodecMetadata;
import io.r2dbc.postgresql.codec.PostgresTypeIdentifier;
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.postgresql.util.Assert;
import io.r2dbc.postgresql.util.ByteBufUtils;
import io.r2dbc.spi.Blob;
import io.r2dbc.spi.Clob;
import javax.annotation.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BPCHAR;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BYTEA;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TEXT;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.VARCHAR;
import static io.r2dbc.kingbase.mysql.KingbaseMysqlCodecSupport.encodeText;
import static io.r2dbc.kingbase.mysql.KingbaseMysqlCodecSupport.nullParameter;
import static io.r2dbc.kingbase.mysql.KingbaseMysqlCodecSupport.resolveBindOid;
import static io.r2dbc.kingbase.mysql.KingbaseMysqlTypeOids.equalsPg;
import static io.r2dbc.postgresql.message.Format.FORMAT_BINARY;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;

/**
 * Clob / Blob / SET-style codecs for Kingbase MySQL mode on the PostgreSQL wire.
 */
final class KingbaseMysqlLobCodecs {

    private KingbaseMysqlLobCodecs() {
    }

    static List<Codec<?>> create(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
        List<Codec<?>> list = new ArrayList<>(3);
        list.add(new SetCodec(allocator, typeOids));
        list.add(new ClobCodec(allocator, typeOids));
        list.add(new BlobCodec(allocator, typeOids));
        return list;
    }

    /**
     * MySQL {@code SET('a','b')} is comma-separated text on Kingbase PG wire.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static final class SetCodec implements Codec<Object>, CodecMetadata {

        private final ByteBufAllocator allocator;

        private final KingbaseMysqlTypeOids typeOids;

        SetCodec(ByteBufAllocator allocator) {
            this(allocator, KingbaseMysqlTypeOids.postgresqlDefaults());
        }

        SetCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            this.allocator = Assert.requireNonNull(allocator, "allocator must not be null");
            this.typeOids = Assert.requireNonNull(typeOids, "typeOids must not be null");
        }

        @Override
        public Class<?> type() {
            return String[].class;
        }

        @Override
        public Iterable<PostgresTypeIdentifier> getDataTypes() {
            return Arrays.<PostgresTypeIdentifier>asList(VARCHAR, TEXT, BPCHAR);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            if (!(type == String[].class || type == Set.class || Set.class.isAssignableFrom(type))) {
                return false;
            }
            return this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isSet(dataType) || this.typeOids.isText(dataType));
        }

        @Override
        public boolean canEncode(Object value) {
            if (value instanceof CharSequence[]) {
                return true;
            }
            if (value instanceof Set<?>) {
                return isValidSet((Set<?>) value);
            }
            return false;
        }

        @Override
        public boolean canEncodeNull(Class<?> type) {
            return type == String[].class
                || type == Set.class
                || Set.class.isAssignableFrom(type);
        }

        @Override
        public @Nullable Object decode(@Nullable ByteBuf buffer, int dataType, Format format,
                                       Class<?> type) {
            if (buffer == null) {
                return null;
            }
            String text = ByteBufUtils.decode(buffer);
            String[] parts = text.isEmpty() ? new String[0] : text.split(",", -1);
            if (type == String[].class) {
                return parts;
            }
            return new LinkedHashSet<>(Arrays.asList(parts));
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeText());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveBindOid(dataType, this.typeOids.encodeText());
            return encodeText(this.allocator, oid, join(value));
        }

        @Override
        public EncodedParameter encodeNull() {
            return nullParameter(this.typeOids.encodeText());
        }

        private static String join(Object value) {
            Iterator<? extends CharSequence> it;
            if (value instanceof CharSequence[]) {
                it = Arrays.asList((CharSequence[]) value).iterator();
            } else {
                it = new ConvertedIterator(((Set<?>) value).iterator());
            }
            if (!it.hasNext()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(it.next());
            while (it.hasNext()) {
                sb.append(',').append(it.next());
            }
            return sb.toString();
        }

        private static boolean isValidSet(Set<?> value) {
            for (Object element : value) {
                if (!(element instanceof CharSequence) && !(element instanceof Enum<?>)) {
                    return false;
                }
            }
            return true;
        }

        private static final class ConvertedIterator implements Iterator<String> {

            private final Iterator<?> origin;

            private ConvertedIterator(Iterator<?> origin) {
                this.origin = origin;
            }

            @Override
            public boolean hasNext() {
                return this.origin.hasNext();
            }

            @Override
            public String next() {
                Object o = this.origin.next();
                return o instanceof Enum<?> ? ((Enum<?>) o).name() : o.toString();
            }
        }
    }

    /**
     * Large text as {@link Clob} (TEXT/VARCHAR on PG wire).
     */
    static final class ClobCodec extends KingbaseMysqlCodecSupport.AbstractMysqlCodec<Clob> {

        ClobCodec(ByteBufAllocator allocator) {
            this(allocator, KingbaseMysqlTypeOids.postgresqlDefaults());
        }

        ClobCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, Clob.class, KingbaseMysqlType.TEXT, typeOids);
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
            int oid = resolveBindOid(dataType, this.typeOids.encodeText());
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
    static final class BlobCodec extends KingbaseMysqlCodecSupport.AbstractMysqlCodec<Blob> {

        BlobCodec(ByteBufAllocator allocator) {
            this(allocator, KingbaseMysqlTypeOids.postgresqlDefaults());
        }

        BlobCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, Blob.class, KingbaseMysqlType.BLOB, typeOids);
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
            int oid = resolveBindOid(dataType, this.typeOids.encodeBinary());
            return new EncodedParameter(FORMAT_BINARY, oid,
                Flux.from(blob.stream())
                    .reduce(this.allocator.compositeBuffer(),
                        (composite, chunk) -> composite.addComponent(true, Unpooled.wrappedBuffer(chunk)))
                    .map(buf -> (ByteBuf) buf)
                    .defaultIfEmpty(this.allocator.buffer(0))
                    .concatWith(Flux.from(blob.discard()).then(Mono.empty())));
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
        return KingbaseMysqlCodecSupport.decodeBinaryText(ByteBufUtils.decode(buffer));
    }

}
