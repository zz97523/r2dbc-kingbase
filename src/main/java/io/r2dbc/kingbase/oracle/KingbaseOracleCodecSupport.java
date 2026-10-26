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
import io.r2dbc.postgresql.client.EncodedParameter;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.CodecMetadata;
import io.r2dbc.postgresql.codec.PostgresTypeIdentifier;
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.postgresql.util.Assert;
import io.r2dbc.postgresql.util.ByteBufUtils;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static io.r2dbc.postgresql.client.EncodedParameter.NULL_VALUE;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BPCHAR;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.FLOAT4;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.FLOAT8;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT2;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT4;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT8;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.NUMERIC;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.OID;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TEXT;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.VARCHAR;
import static io.r2dbc.postgresql.message.Format.FORMAT_BINARY;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;

/**
 * Shared helpers for Kingbase Oracle-mode codecs (PG wire OID + Oracle Java type habits).
 */
final class KingbaseOracleCodecSupport {

    static final Set<PostgresqlObjectId> NUMERIC_OIDS = EnumSet.of(
        INT2, INT4, INT8, FLOAT4, FLOAT8, NUMERIC, OID
    );

    static final Set<PostgresqlObjectId> STRING_OIDS = EnumSet.of(
        TEXT, VARCHAR, BPCHAR, NUMERIC, INT2, INT4, INT8, FLOAT4, FLOAT8
    );

    private KingbaseOracleCodecSupport() {
    }

    static boolean isTarget(Class<?> type, Class<?> expected) {
        return type == expected
            || (expected == Boolean.class && type == Boolean.TYPE)
            || (expected == Byte.class && type == Byte.TYPE)
            || (expected == Short.class && type == Short.TYPE)
            || (expected == Integer.class && type == Integer.TYPE)
            || (expected == Long.class && type == Long.TYPE)
            || (expected == Float.class && type == Float.TYPE)
            || (expected == Double.class && type == Double.TYPE);
    }

    static Number decodeNumber(ByteBuf buffer, int dataType, Format format) {
        if (format == FORMAT_BINARY && PostgresqlObjectId.isValid(dataType)) {
            PostgresqlObjectId oid = PostgresqlObjectId.valueOf(dataType);
            switch (oid) {
                case INT2:
                    return buffer.readShort();
                case INT4:
                case OID:
                    return buffer.readInt();
                case INT8:
                    return buffer.readLong();
                case FLOAT4:
                    return buffer.readFloat();
                case FLOAT8:
                    return buffer.readDouble();
                default:
                    break;
            }
        }
        String text = ByteBufUtils.decode(buffer).trim();
        if (text.isEmpty()) {
            return 0;
        }
        if (PostgresqlObjectId.isValid(dataType)) {
            PostgresqlObjectId oid = PostgresqlObjectId.valueOf(dataType);
            if (oid == FLOAT4 || oid == FLOAT8) {
                return Double.valueOf(text);
            }
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Cannot decode number from '" + text + "'", ex);
        }
    }

    static EncodedParameter encodeText(ByteBufAllocator allocator, int oid, String text) {
        return new EncodedParameter(FORMAT_TEXT, oid,
            Mono.fromSupplier(() -> ByteBufUtils.encode(allocator, text)));
    }

    static EncodedParameter nullParameter(int oid) {
        return new EncodedParameter(FORMAT_BINARY, oid, NULL_VALUE);
    }

    static int resolveBindOid(int dataType, int fallbackOid) {
        return dataType > 0 ? dataType : fallbackOid;
    }

    static byte[] decodeBinaryText(String text) {
        if (text.isEmpty()) {
            return new byte[0];
        }
        if (text.startsWith("\\x") || text.startsWith("\\X")) {
            return io.netty.buffer.ByteBufUtil.decodeHexDump(text.substring(2));
        }
        if (text.startsWith("0x") || text.startsWith("0X")) {
            return io.netty.buffer.ByteBufUtil.decodeHexDump(text.substring(2));
        }
        if (text.indexOf('\\') < 0) {
            return text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        }
        byte[] out = new byte[text.length()];
        int outLen = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == '\\') {
                    out[outLen++] = '\\';
                    i++;
                } else if (next >= '0' && next <= '3' && i + 3 < text.length()) {
                    out[outLen++] = (byte) ((next - '0') << 6
                        | (text.charAt(i + 2) - '0') << 3
                        | (text.charAt(i + 3) - '0'));
                    i += 3;
                } else {
                    out[outLen++] = (byte) c;
                }
            } else {
                out[outLen++] = (byte) c;
            }
        }
        return java.util.Arrays.copyOf(out, outLen);
    }

    static abstract class AbstractOracleCodec<T> implements Codec<T>, CodecMetadata {

        protected final ByteBufAllocator allocator;

        protected final Class<T> javaType;

        protected final KingbaseOracleType oracleType;

        protected final KingbaseOracleTypeOids typeOids;

        protected AbstractOracleCodec(ByteBufAllocator allocator, Class<T> javaType, KingbaseOracleType oracleType,
                                      KingbaseOracleTypeOids typeOids) {
            this.allocator = Assert.requireNonNull(allocator, "allocator must not be null");
            this.javaType = Assert.requireNonNull(javaType, "javaType must not be null");
            this.oracleType = Assert.requireNonNull(oracleType, "oracleType must not be null");
            this.typeOids = Assert.requireNonNull(typeOids, "typeOids must not be null");
        }

        @Override
        public Class<?> type() {
            return this.javaType;
        }

        @Override
        public Iterable<? extends PostgresTypeIdentifier> getDataTypes() {
            return Collections.singleton(this.oracleType.getOid());
        }

        @Override
        public boolean canEncode(Object value) {
            return this.javaType.isInstance(value);
        }

        @Override
        public boolean canEncodeNull(Class<?> type) {
            return this.javaType.isAssignableFrom(type)
                || (this.javaType == Boolean.class && type == Boolean.TYPE)
                || (this.javaType == Byte.class && type == Byte.TYPE)
                || (this.javaType == Short.class && type == Short.TYPE)
                || (this.javaType == Integer.class && type == Integer.TYPE)
                || (this.javaType == Long.class && type == Long.TYPE)
                || (this.javaType == Float.class && type == Float.TYPE)
                || (this.javaType == Double.class && type == Double.TYPE);
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.oracleType.getObjectId());
        }

        @Override
        public EncodedParameter encodeNull() {
            return nullParameter(this.oracleType.getObjectId());
        }

        @Override
        @SuppressWarnings("unchecked")
        public final @Nullable T decode(@Nullable ByteBuf buffer, int dataType, Format format, Class<? extends T> type) {
            if (buffer == null) {
                return null;
            }
            return doDecode(buffer, dataType, format, type);
        }

        abstract T doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends T> type);
    }

    static abstract class AbstractNumericOracleCodec<T extends Number> extends AbstractOracleCodec<T> {

        protected AbstractNumericOracleCodec(ByteBufAllocator allocator, Class<T> javaType, KingbaseOracleType oracleType,
                                             KingbaseOracleTypeOids typeOids) {
            super(allocator, javaType, oracleType, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            Assert.requireNonNull(format, "format must not be null");
            Assert.requireNonNull(type, "type must not be null");
            if (!isTarget(type, this.javaType)) {
                return false;
            }
            return this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isNumber(dataType) || this.typeOids.isText(dataType));
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            Number number = (Number) value;
            int oid = resolveBindOid(dataType, this.typeOids.encodeNumber());
            if (this.oracleType == KingbaseOracleType.BINARY_FLOAT) {
                oid = resolveBindOid(dataType, this.typeOids.encodeFloat());
            } else if (this.oracleType == KingbaseOracleType.BINARY_DOUBLE) {
                oid = resolveBindOid(dataType, this.typeOids.encodeDouble());
            } else if (this.oracleType == KingbaseOracleType.INTEGER) {
                oid = resolveBindOid(dataType, KingbaseOracleType.INTEGER.getObjectId());
            } else if (this.oracleType == KingbaseOracleType.BIGINT) {
                oid = resolveBindOid(dataType, KingbaseOracleType.BIGINT.getObjectId());
            } else if (this.oracleType == KingbaseOracleType.SMALLINT) {
                oid = resolveBindOid(dataType, KingbaseOracleType.SMALLINT.getObjectId());
            }
            return encodeText(this.allocator, oid, number.toString());
        }
    }

    static BigInteger toBigInteger(Number number) {
        if (number instanceof BigInteger) {
            return (BigInteger) number;
        }
        if (number instanceof BigDecimal) {
            return ((BigDecimal) number).toBigInteger();
        }
        return BigInteger.valueOf(number.longValue());
    }

    static BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal) {
            return (BigDecimal) number;
        }
        if (number instanceof BigInteger) {
            return new BigDecimal((BigInteger) number);
        }
        return BigDecimal.valueOf(number.doubleValue());
    }

}
