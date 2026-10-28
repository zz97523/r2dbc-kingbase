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
import io.r2dbc.postgresql.client.EncodedParameter;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.CodecMetadata;
import io.r2dbc.postgresql.codec.PostgresTypeIdentifier;
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.postgresql.util.Assert;
import io.r2dbc.postgresql.util.ByteBufUtils;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static io.r2dbc.postgresql.client.EncodedParameter.NULL_VALUE;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BPCHAR;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BYTEA;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.CHAR;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.DATE;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.FLOAT4;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.FLOAT8;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT2;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT4;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT8;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.NUMERIC;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.OID;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TEXT;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TIMESTAMP;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TIMESTAMPTZ;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.UUID;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.VARCHAR;
import static io.r2dbc.postgresql.message.Format.FORMAT_BINARY;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;

/**
 * Shared helpers for Kingbase SQL Server-mode codecs (PG wire OID + SQL Server Java type habits).
 */
final class KingbaseSqlServerCodecSupport {

    static final Set<PostgresqlObjectId> NUMERIC_OIDS = EnumSet.of(
        INT2, INT4, INT8, FLOAT4, FLOAT8, NUMERIC, OID
    );

    static final Set<PostgresqlObjectId> STRING_OIDS = EnumSet.of(
        TEXT, VARCHAR, BPCHAR, NUMERIC, INT2, INT4, INT8, FLOAT4, FLOAT8, UUID
    );

    private KingbaseSqlServerCodecSupport() {
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

    static boolean isNumericOid(int dataType) {
        return PostgresqlObjectId.isValid(dataType) && NUMERIC_OIDS.contains(PostgresqlObjectId.valueOf(dataType));
    }

    /**
     * Decode a number from PG-wire text/binary. Unknown Kingbase OIDs fall through to text parsing
     * (never call {@link PostgresqlObjectId#valueOf(int)} on non-catalog OIDs).
     */
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

    /**
     * Prefer an explicit bind OID when present; otherwise the Kingbase/PG fallback from {@code pg_type}.
     * When the fallback is a Kingbase-specific OID, never bind stock {@code pg_catalog} types — T-SQL
     * columns reject cross-catalog mismatches ({@code nvarchar = text}, {@code bit = pg_catalog.bit}, …).
     */
    static int resolveBindOid(int dataType, int fallbackOid) {
        if (dataType <= 0) {
            return fallbackOid;
        }
        if (dataType == fallbackOid) {
            return dataType;
        }
        if (!PostgresqlObjectId.isValid(fallbackOid)) {
            return fallbackOid;
        }
        return dataType;
    }

    static int resolveTextBindOid(int dataType, int fallbackOid) {
        int oid = resolveBindOid(dataType, fallbackOid);
        if (PostgresqlObjectId.isValid(oid)) {
            PostgresqlObjectId pg = PostgresqlObjectId.valueOf(oid);
            if (pg == TEXT || pg == VARCHAR || pg == BPCHAR || pg == CHAR) {
                return fallbackOid;
            }
        }
        return oid;
    }

    static int resolveBinaryBindOid(int dataType, int fallbackOid) {
        int oid = resolveBindOid(dataType, fallbackOid);
        if (PostgresqlObjectId.isValid(oid)) {
            PostgresqlObjectId pg = PostgresqlObjectId.valueOf(oid);
            if (pg == BYTEA || pg == TIMESTAMP) {
                return fallbackOid;
            }
        }
        return oid;
    }

    static int resolveDateBindOid(int dataType, int fallbackOid) {
        int oid = resolveBindOid(dataType, fallbackOid);
        if (equalsPg(oid, DATE)) {
            return fallbackOid;
        }
        return oid;
    }

    static int resolveTimestampBindOid(int dataType, int fallbackOid) {
        int oid = resolveBindOid(dataType, fallbackOid);
        if (PostgresqlObjectId.isValid(oid)) {
            PostgresqlObjectId pg = PostgresqlObjectId.valueOf(oid);
            if (pg == TIMESTAMP || pg == TIMESTAMPTZ) {
                return fallbackOid;
            }
        }
        return oid;
    }

    /**
     * Encode {@link java.time.OffsetDateTime} for the PG wire in SQL Server mode.
     * {@code datetimeoffset} keeps offset; {@code datetime2}/{@code datetime} store UTC wall-clock
     * so decode-as-UTC preserves {@link java.time.OffsetDateTime#toInstant()}.
     */
    static String formatOffsetDateTimeText(OffsetDateTime value, int bindOid, KingbaseSqlServerTypeOids typeOids) {
        if (typeOids.isDatetimeoffsetEncodeOid(bindOid)) {
            return value.toString();
        }
        return value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime().toString().replace('T', ' ');
    }

    /**
     * Encode {@link java.time.ZonedDateTime} with the same rules as {@link #formatOffsetDateTimeText}.
     */
    static String formatZonedDateTimeText(ZonedDateTime value, int bindOid, KingbaseSqlServerTypeOids typeOids) {
        if (typeOids.isDatetimeoffsetEncodeOid(bindOid)) {
            return value.toOffsetDateTime().toString();
        }
        return value.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime().toString().replace('T', ' ');
    }

    private static boolean equalsPg(int dataType, PostgresqlObjectId expected) {
        return PostgresqlObjectId.isValid(dataType) && PostgresqlObjectId.valueOf(dataType) == expected;
    }

    static abstract class AbstractSqlServerCodec<T> implements Codec<T>, CodecMetadata {

        protected final ByteBufAllocator allocator;

        protected final Class<T> javaType;

        protected final KingbaseSqlServerType sqlserverType;

        protected final KingbaseSqlServerTypeOids typeOids;

        protected AbstractSqlServerCodec(ByteBufAllocator allocator, Class<T> javaType, KingbaseSqlServerType sqlserverType) {
            this(allocator, javaType, sqlserverType, KingbaseSqlServerTypeOids.postgresqlDefaults());
        }

        protected AbstractSqlServerCodec(ByteBufAllocator allocator, Class<T> javaType, KingbaseSqlServerType sqlserverType,
                                     KingbaseSqlServerTypeOids typeOids) {
            this.allocator = Assert.requireNonNull(allocator, "allocator must not be null");
            this.javaType = Assert.requireNonNull(javaType, "javaType must not be null");
            this.sqlserverType = Assert.requireNonNull(sqlserverType, "sqlserverType must not be null");
            this.typeOids = Assert.requireNonNull(typeOids, "typeOids must not be null");
        }

        @Override
        public Class<?> type() {
            return this.javaType;
        }

        @Override
        public Iterable<PostgresTypeIdentifier> getDataTypes() {
            return Collections.<PostgresTypeIdentifier>singleton(
                () -> this.typeOids.encodeOid(this.sqlserverType));
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
            return encode(value, this.typeOids.encodeOid(this.sqlserverType));
        }

        @Override
        public EncodedParameter encodeNull() {
            return nullParameter(this.typeOids.encodeOid(this.sqlserverType));
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

    static abstract class AbstractNumericSqlServerCodec<T extends Number> extends AbstractSqlServerCodec<T> {

        protected AbstractNumericSqlServerCodec(ByteBufAllocator allocator, Class<T> javaType, KingbaseSqlServerType sqlserverType) {
            super(allocator, javaType, sqlserverType);
        }

        protected AbstractNumericSqlServerCodec(ByteBufAllocator allocator, Class<T> javaType, KingbaseSqlServerType sqlserverType,
                                            KingbaseSqlServerTypeOids typeOids) {
            super(allocator, javaType, sqlserverType, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            Assert.requireNonNull(format, "format must not be null");
            Assert.requireNonNull(type, "type must not be null");
            if (type == Object.class) {
                if (!this.typeOids.isDefaultNumeric(dataType, this.javaType)) {
                    return false;
                }
            } else if (!isTarget(type, this.javaType)) {
                return false;
            }
            return this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isNumeric(dataType) || this.typeOids.isText(dataType));
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            Number number = (Number) value;
            int oid = resolveBindOid(dataType, this.sqlserverType.getObjectId());
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

    /**
     * Kingbase SQL Server-mode binary columns often arrive as TEXT with SQL Server octal escapes
     * (e.g. {@code \001\002}) or PG-style {@code \x0102} hex.
     */
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
            if (c == '\\' && i + 3 < text.length()
                && isOctalDigit(text.charAt(i + 1))
                && isOctalDigit(text.charAt(i + 2))
                && isOctalDigit(text.charAt(i + 3))) {
                out[outLen++] = (byte) (
                    (text.charAt(i + 1) - '0') * 64
                        + (text.charAt(i + 2) - '0') * 8
                        + (text.charAt(i + 3) - '0'));
                i += 3;
                continue;
            }
            out[outLen++] = (byte) c;
        }
        if (outLen == out.length) {
            return out;
        }
        byte[] trimmed = new byte[outLen];
        System.arraycopy(out, 0, trimmed, 0, outLen);
        return trimmed;
    }

    private static boolean isOctalDigit(char c) {
        return c >= '0' && c <= '7';
    }

}
