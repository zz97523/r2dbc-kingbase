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
import io.r2dbc.postgresql.codec.CodecMetadata;
import io.r2dbc.postgresql.codec.PostgresTypeIdentifier;
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.postgresql.util.Assert;
import io.r2dbc.postgresql.util.ByteBufUtils;
import io.r2dbc.kingbase.KingbaseMoneyCodecSupport;
import io.r2dbc.kingbase.KingbaseMoney;
import io.r2dbc.kingbase.KingbaseXml;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.BitSet;

import static io.r2dbc.postgresql.client.EncodedParameter.NULL_VALUE;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BIT;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BPCHAR;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BYTEA;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT2;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT4;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT8;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TEXT;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.UUID;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.VARBIT;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.VARCHAR;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.AbstractSqlServerCodec;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.STRING_OIDS;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.encodeText;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.nullParameter;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.resolveBinaryBindOid;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.resolveBindOid;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.resolveTextBindOid;
import static io.r2dbc.postgresql.message.Format.FORMAT_BINARY;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;

/**
 * String / binary / UUID / bitset / enum codecs for Kingbase SQL Server mode.
 */
final class KingbaseSqlServerTextBinaryCodecs {

    private KingbaseSqlServerTextBinaryCodecs() {
    }

    /** Explicit MONEY value used when the database type must be selected during parameter inference. */
    static final class MoneyValueCodec extends AbstractSqlServerCodec<KingbaseMoney> {

        MoneyValueCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, KingbaseMoney.class, KingbaseSqlServerType.MONEY, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return format == FORMAT_TEXT && this.typeOids.isMoney(dataType)
                && type == KingbaseMoney.class;
        }

        @Override
        KingbaseMoney doDecode(ByteBuf buffer, int dataType, Format format,
                               Class<? extends KingbaseMoney> type) {
            return new KingbaseMoney(KingbaseMoneyCodecSupport.parse(ByteBufUtils.decode(buffer)));
        }

        @Override
        public boolean canEncode(Object value) {
            return value instanceof KingbaseMoney;
        }

        @Override
        public boolean canEncodeNull(Class<?> type) {
            return type == KingbaseMoney.class;
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeMoney());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveBindOid(dataType, this.typeOids.encodeMoney());
            return encodeText(this.allocator, oid, value.toString());
        }

        @Override
        public EncodedParameter encodeNull() {
            return nullParameter(this.typeOids.encodeMoney());
        }
    }

    /** Optional explicit {@link Double} view of SQL Server {@code money}. */
    static final class MoneyCodec extends AbstractSqlServerCodec<Double> {

        MoneyCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, Double.class, KingbaseSqlServerType.MONEY, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return format == FORMAT_TEXT && this.typeOids.isMoney(dataType)
                && (type == Double.class || type == Double.TYPE);
        }

        @Override
        Double doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Double> type) {
            return KingbaseMoneyCodecSupport.parse(ByteBufUtils.decode(buffer)).doubleValue();
        }

        @Override
        public boolean canEncode(Object value) {
            return value instanceof KingbaseMoney;
        }

        @Override
        public boolean canEncodeNull(Class<?> type) {
            return type == KingbaseMoney.class;
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeMoney());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveBindOid(dataType, this.typeOids.encodeMoney());
            return encodeText(this.allocator, oid,
                KingbaseMoneyCodecSupport.format((Number) value));
        }

        @Override
        public EncodedParameter encodeNull() {
            return nullParameter(this.typeOids.encodeMoney());
        }
    }

    static final class MoneyBigDecimalCodec extends AbstractSqlServerCodec<BigDecimal> {

        MoneyBigDecimalCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, BigDecimal.class, KingbaseSqlServerType.MONEY, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            // KES JDBC SQLServerTypeInfoCache exposes MONEY as JDBC DECIMAL / BigDecimal.
            return format == FORMAT_TEXT && this.typeOids.isMoney(dataType)
                && (type == Object.class || type == BigDecimal.class);
        }

        @Override
        BigDecimal doDecode(ByteBuf buffer, int dataType, Format format,
                            Class<? extends BigDecimal> type) {
            return KingbaseMoneyCodecSupport.parse(ByteBufUtils.decode(buffer));
        }

        @Override
        public boolean canEncode(Object value) {
            return false;
        }

        @Override
        public boolean canEncodeNull(Class<?> type) {
            return false;
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeMoney());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveBindOid(dataType, this.typeOids.encodeMoney());
            return encodeText(this.allocator, oid,
                KingbaseMoneyCodecSupport.format((Number) value));
        }

        @Override
        public EncodedParameter encodeNull() {
            return nullParameter(this.typeOids.encodeMoney());
        }
    }

    /**
     * SQL Server {@code XML} is exposed as text because R2DBC SPI 0.9 has no SQLXML type.
     * XML encoding remains handled by the regular String codec when an XML OID is explicit.
     */
    static final class XmlCodec extends AbstractSqlServerCodec<String> {

        XmlCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, String.class, KingbaseSqlServerType.XML, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return (type == String.class || type == Object.class)
                && this.typeOids.isXml(dataType);
        }

        @Override
        String doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends String> type) {
            return ByteBufUtils.decode(buffer);
        }

        @Override
        public boolean canEncode(Object value) {
            return value instanceof KingbaseXml;
        }

        @Override
        public boolean canEncodeNull(Class<?> type) {
            return type == KingbaseXml.class;
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeXml());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveBindOid(dataType, this.typeOids.encodeXml());
            return encodeText(this.allocator, oid, value instanceof KingbaseXml
                ? ((KingbaseXml) value).getValue() : (String) value);
        }

        @Override
        public EncodedParameter encodeNull() {
            return nullParameter(this.typeOids.encodeXml());
        }
    }

    /** Explicit XML value used for scalar and array parameter inference. */
    static final class XmlValueCodec extends AbstractSqlServerCodec<KingbaseXml> {

        XmlValueCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, KingbaseXml.class, KingbaseSqlServerType.XML, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return this.typeOids.isXml(dataType) && type == KingbaseXml.class;
        }

        @Override
        KingbaseXml doDecode(ByteBuf buffer, int dataType, Format format,
                             Class<? extends KingbaseXml> type) {
            return new KingbaseXml(ByteBufUtils.decode(buffer));
        }

        @Override
        public boolean canEncode(Object value) {
            return value instanceof KingbaseXml;
        }

        @Override
        public boolean canEncodeNull(Class<?> type) {
            return type == KingbaseXml.class;
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeXml());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveBindOid(dataType, this.typeOids.encodeXml());
            return encodeText(this.allocator, oid, ((KingbaseXml) value).getValue());
        }

        @Override
        public EncodedParameter encodeNull() {
            return nullParameter(this.typeOids.encodeXml());
        }
    }

    static final class StringCodec extends AbstractSqlServerCodec<String> {

        StringCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, String.class, KingbaseSqlServerType.VARCHAR, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            if (type != String.class && !(type == Object.class && this.typeOids.isCharacter(dataType))) {
                return false;
            }
            return this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isText(dataType) || this.typeOids.isNumeric(dataType)
                    || KingbaseSqlServerTypeOids.isPgOid(dataType, STRING_OIDS));
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
            int oid = resolveTextBindOid(dataType, this.typeOids.encodeText());
            return encodeText(this.allocator, oid, (String) value);
        }
    }

    /**
     * Decode/encode Java {@link Enum} as string (r2dbc-mssql EnumCodec).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static final class EnumCodec implements Codec<Enum>, CodecMetadata {

        private final ByteBufAllocator allocator;

        private final KingbaseSqlServerTypeOids typeOids;

        EnumCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
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
            String name = ByteBufUtils.decode(buffer).trim();
            return Enum.valueOf(type, name);
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeText());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveTextBindOid(dataType, this.typeOids.encodeText());
            return encodeText(this.allocator, oid, ((Enum) value).name());
        }

        @Override
        public EncodedParameter encodeNull() {
            return nullParameter(this.typeOids.encodeText());
        }
    }

    static final class BitSetCodec extends AbstractSqlServerCodec<BitSet> {

        BitSetCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, BitSet.class, KingbaseSqlServerType.BIT, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == BitSet.class
                && this.typeOids.canDecodeTextExtension(dataType, format, this.typeOids.isBit(dataType));
        }

        @Override
        BitSet doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends BitSet> type) {
            if (PostgresqlObjectId.isValid(dataType)) {
                PostgresqlObjectId oid = PostgresqlObjectId.valueOf(dataType);
                if (oid == INT2 || oid == INT4 || oid == INT8) {
                    long bits = KingbaseSqlServerCodecSupport.decodeNumber(buffer, dataType, format).longValue();
                    return BitSet.valueOf(new long[]{bits});
                }
                if (format == FORMAT_BINARY && (oid == BYTEA || oid == BIT || oid == VARBIT)) {
                    byte[] bytes = ByteBufUtil.getBytes(buffer);
                    reverse(bytes);
                    return BitSet.valueOf(bytes);
                }
            } else if (this.typeOids.isNumeric(dataType)) {
                long bits = KingbaseSqlServerCodecSupport.decodeNumber(buffer, dataType, format).longValue();
                return BitSet.valueOf(new long[]{bits});
            }
            String text = ByteBufUtils.decode(buffer).trim();
            if (text.isEmpty()) {
                return new BitSet();
            }
            if (text.chars().allMatch(c -> c == '0' || c == '1')) {
                BitSet set = new BitSet(text.length());
                for (int i = 0; i < text.length(); i++) {
                    if (text.charAt(text.length() - 1 - i) == '1') {
                        set.set(i);
                    }
                }
                return set;
            }
            if (text.startsWith("0x") || text.startsWith("0X")) {
                return BitSet.valueOf(new long[]{Long.parseLong(text.substring(2), 16)});
            }
            return BitSet.valueOf(new long[]{Long.parseLong(text)});
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeBitAsInt());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            BitSet set = (BitSet) value;
            long bits = set.isEmpty() ? 0L : set.toLongArray()[0];
            int oid = resolveBindOid(dataType, this.typeOids.encodeBitAsInt());
            return encodeText(this.allocator, oid, Long.toString(bits));
        }

        private static void reverse(byte[] bytes) {
            for (int i = 0, j = bytes.length - 1; i < j; i++, j--) {
                byte t = bytes[i];
                bytes[i] = bytes[j];
                bytes[j] = t;
            }
        }
    }

    static final class ByteArrayCodec extends AbstractSqlServerCodec<byte[]> {

        ByteArrayCodec(ByteBufAllocator allocator) {
            this(allocator, KingbaseSqlServerTypeOids.postgresqlDefaults());
        }

        ByteArrayCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, byte[].class, KingbaseSqlServerType.VARBINARY, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return (type == byte[].class || (type == Object.class && this.typeOids.isRowVersion(dataType)))
                && this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isBinary(dataType) || this.typeOids.isText(dataType));
        }

        @Override
        public boolean canEncode(Object value) {
            return value instanceof byte[];
        }

        @Override
        byte[] doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends byte[]> type) {
            if (format == FORMAT_BINARY || KingbaseSqlServerTypeOids.equalsPg(dataType, BYTEA) || this.typeOids.isBinary(dataType)) {
                if (format == FORMAT_TEXT && !KingbaseSqlServerTypeOids.equalsPg(dataType, BYTEA) && !this.typeOids.isBinary(dataType)) {
                    return ByteBufUtils.decode(buffer).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
                if (format == FORMAT_TEXT) {
                    return KingbaseSqlServerCodecSupport.decodeBinaryText(ByteBufUtils.decode(buffer));
                }
                return ByteBufUtil.getBytes(buffer);
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
            int oid = resolveBinaryBindOid(dataType, this.typeOids.encodeBinary());
            return new EncodedParameter(FORMAT_BINARY, oid,
                Mono.fromSupplier(() -> this.allocator.buffer(bytes.length).writeBytes(bytes)));
        }

        @Override
        public EncodedParameter encodeNull() {
            return new EncodedParameter(FORMAT_BINARY, this.typeOids.encodeBinary(), NULL_VALUE);
        }
    }

    static final class ByteBufferCodec extends AbstractSqlServerCodec<ByteBuffer> {

        private final ByteArrayCodec delegate;

        ByteBufferCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, ByteBuffer.class, KingbaseSqlServerType.VARBINARY, typeOids);
            this.delegate = new ByteArrayCodec(allocator, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == ByteBuffer.class && this.delegate.canDecode(dataType, format, byte[].class);
        }

        @Override
        public boolean canEncode(Object value) {
            return value instanceof ByteBuffer;
        }

        @Override
        ByteBuffer doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends ByteBuffer> type) {
            return ByteBuffer.wrap(this.delegate.doDecode(buffer, dataType, format, byte[].class));
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            ByteBuffer buf = ((ByteBuffer) value).slice();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            return this.delegate.encode(bytes, dataType);
        }
    }

    /**
     * SQL Server {@code uniqueidentifier} ↔ {@link java.util.UUID} (r2dbc-mssql GUID mapping).
     */
    static final class UuidCodec extends AbstractSqlServerCodec<java.util.UUID> {

        UuidCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, java.util.UUID.class, KingbaseSqlServerType.UNIQUEIDENTIFIER, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == java.util.UUID.class
                && this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isUuid(dataType) || this.typeOids.isText(dataType)
                    || KingbaseSqlServerTypeOids.equalsPg(dataType, UUID));
        }

        @Override
        java.util.UUID doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends java.util.UUID> type) {
            if (format == FORMAT_BINARY && KingbaseSqlServerTypeOids.equalsPg(dataType, UUID)) {
                return new java.util.UUID(buffer.readLong(), buffer.readLong());
            }
            return java.util.UUID.fromString(ByteBufUtils.decode(buffer).trim());
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeUuid());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveBindOid(dataType, this.typeOids.encodeUuid());
            return encodeText(this.allocator, oid, value.toString());
        }

        @Override
        public EncodedParameter encodeNull() {
            return nullParameter(this.typeOids.encodeUuid());
        }
    }

}
