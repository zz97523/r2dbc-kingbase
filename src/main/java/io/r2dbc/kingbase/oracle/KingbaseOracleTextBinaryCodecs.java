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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.r2dbc.postgresql.client.EncodedParameter;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.CodecMetadata;
import io.r2dbc.postgresql.codec.PostgresTypeIdentifier;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.postgresql.util.Assert;
import io.r2dbc.postgresql.util.ByteBufUtils;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.sql.RowId;
import java.util.Arrays;

import static io.r2dbc.postgresql.client.EncodedParameter.NULL_VALUE;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BPCHAR;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BYTEA;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TEXT;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.VARCHAR;
import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.AbstractOracleCodec;
import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.STRING_OIDS;
import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.decodeBinaryText;
import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.encodeText;
import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.nullParameter;
import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.resolveBindOid;
import static io.r2dbc.postgresql.message.Format.FORMAT_BINARY;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;

/**
 * String / binary codecs for Kingbase Oracle mode (VARCHAR2 / RAW habits).
 */
final class KingbaseOracleTextBinaryCodecs {

    private KingbaseOracleTextBinaryCodecs() {
    }

    static final class StringCodec extends AbstractOracleCodec<String> {

        StringCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, String.class, KingbaseOracleType.VARCHAR2, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            if (type != String.class) {
                return false;
            }
            return this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isText(dataType) || this.typeOids.isNumber(dataType)
                    || KingbaseOracleTypeOids.isPgOid(dataType, STRING_OIDS));
        }

        @Override
        String doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends String> type) {
            return ByteBufUtils.decode(buffer);
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeText());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveBindOid(dataType, this.typeOids.encodeText());
            return encodeText(this.allocator, oid, (String) value);
        }

    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static final class EnumCodec implements Codec<Enum>, CodecMetadata {

        private final ByteBufAllocator allocator;

        private final KingbaseOracleTypeOids typeOids;

        EnumCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            this.allocator = Assert.requireNonNull(allocator, "allocator must not be null");
            this.typeOids = Assert.requireNonNull(typeOids, "typeOids must not be null");
        }

        @Override
        public Class<?> type() {
            return Enum.class;
        }

        @Override
        public Iterable<PostgresTypeIdentifier> getDataTypes() {
            return Arrays.<PostgresTypeIdentifier>asList(VARCHAR, TEXT, BPCHAR);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type.isEnum() && this.typeOids.canDecodeTextExtension(dataType, format, this.typeOids.isText(dataType));
        }

        @Override
        public boolean canEncode(Object value) {
            return value instanceof Enum;
        }

        @Override
        public boolean canEncodeNull(Class<?> type) {
            return type.isEnum();
        }

        @Override
        public Enum decode(ByteBuf buffer, int dataType, Format format, Class<? extends Enum> type) {
            if (buffer == null) {
                return null;
            }
            return Enum.valueOf(type, ByteBufUtils.decode(buffer).trim());
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeText());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveBindOid(dataType, this.typeOids.encodeText());
            return encodeText(this.allocator, oid, ((Enum) value).name());
        }

        @Override
        public EncodedParameter encodeNull() {
            return nullParameter(this.typeOids.encodeText());
        }
    }

    static final class ByteArrayCodec extends AbstractOracleCodec<byte[]> {

        ByteArrayCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, byte[].class, KingbaseOracleType.RAW, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == byte[].class
                && this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isBinary(dataType) || this.typeOids.isText(dataType));
        }

        @Override
        byte[] doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends byte[]> type) {
            if (this.typeOids.isBinary(dataType) || KingbaseOracleTypeOids.equalsPg(dataType, BYTEA)) {
                // Kingbase may return bytea as text (octal/hex escaped) even when format=BINARY
                return decodeBinaryText(ByteBufUtils.decode(buffer));
            }
            if (format == FORMAT_BINARY) {
                byte[] bytes = new byte[buffer.readableBytes()];
                buffer.readBytes(bytes);
                return bytes;
            }
            return ByteBufUtils.decode(buffer).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeBinary());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            byte[] bytes = (byte[]) value;
            int oid = resolveBindOid(dataType, this.typeOids.encodeBinary());
            return new EncodedParameter(FORMAT_BINARY, oid,
                Mono.fromSupplier(() -> this.allocator.buffer(bytes.length).writeBytes(bytes)));
        }

        @Override
        public EncodedParameter encodeNull() {
            return new EncodedParameter(FORMAT_BINARY, this.typeOids.encodeBinary(), NULL_VALUE);
        }
    }

    /**
     * Oracle {@code ROWID} / {@code UROWID} → {@link RowId} (oracle-r2dbc habit).
     */
    static final class RowIdCodec extends AbstractOracleCodec<RowId> {

        RowIdCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, RowId.class, KingbaseOracleType.ROWID, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == RowId.class
                && this.typeOids.canDecodeTextExtension(dataType, format, this.typeOids.isRowid(dataType));
        }

        @Override
        RowId doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends RowId> type) {
            return new KingbaseOracleRowId(ByteBufUtils.decode(buffer).trim());
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeRowid());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveBindOid(dataType, this.typeOids.encodeRowid());
            return encodeText(this.allocator, oid, value.toString());
        }
    }

    static final class ByteBufferCodec extends AbstractOracleCodec<ByteBuffer> {

        private final ByteArrayCodec delegate;

        ByteBufferCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, ByteBuffer.class, KingbaseOracleType.RAW, typeOids);
            this.delegate = new ByteArrayCodec(allocator, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == ByteBuffer.class && this.delegate.canDecode(dataType, format, byte[].class);
        }

        @Override
        ByteBuffer doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends ByteBuffer> type) {
            return ByteBuffer.wrap(this.delegate.doDecode(buffer, dataType, format, byte[].class));
        }

        @Override
        public EncodedParameter encode(Object value) {
            ByteBuffer buf = ((ByteBuffer) value).slice();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            return this.delegate.encode(bytes);
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            ByteBuffer buf = ((ByteBuffer) value).slice();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            return this.delegate.encode(bytes, dataType);
        }
    }

}
