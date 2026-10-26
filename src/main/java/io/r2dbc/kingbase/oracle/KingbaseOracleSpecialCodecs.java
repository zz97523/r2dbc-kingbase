/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */

package io.r2dbc.kingbase.oracle;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.r2dbc.kingbase.KingbaseMoney;
import io.r2dbc.kingbase.KingbaseMoneyCodecSupport;
import io.r2dbc.kingbase.KingbaseXml;
import io.r2dbc.postgresql.client.EncodedParameter;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.CodecMetadata;
import io.r2dbc.postgresql.codec.KingbaseTypedArrayCodec;
import io.r2dbc.postgresql.codec.PostgresTypeIdentifier;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.postgresql.util.ByteBufUtils;
import io.r2dbc.spi.Blob;
import io.r2dbc.spi.Clob;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.sql.RowId;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.encodeText;
import static io.r2dbc.postgresql.client.EncodedParameter.NULL_VALUE;
import static io.r2dbc.postgresql.message.Format.FORMAT_BINARY;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;

/** JDBC-parity codecs for Oracle-mode special values and their SQL arrays. */
final class KingbaseOracleSpecialCodecs {

    private KingbaseOracleSpecialCodecs() {
    }

    static List<Codec<?>> create(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
        List<Codec<?>> codecs = new ArrayList<>();

        XmlCodec xml = new XmlCodec(allocator, typeOids);
        MoneyCodec money = new MoneyCodec(allocator, typeOids);
        CidrCodec cidr = new CidrCodec(typeOids);
        StructCodec struct = new StructCodec(allocator, typeOids);
        GenericObjectCodec generic = new GenericObjectCodec(allocator, typeOids);
        codecs.add(xml);
        codecs.add(money);
        codecs.add(cidr);
        codecs.add(struct);
        codecs.add(generic);

        addArray(codecs, allocator, typeOids, "xml", xml, String.class,
            KingbaseTypedArrayCodec::escapeArrayElement, true);
        addArray(codecs, allocator, typeOids, "money", cast(money), Double.class,
            value -> KingbaseTypedArrayCodec.escapeArrayElement(value.toString()), true);

        KingbaseOracleTemporalCodecs.DurationCodec duration =
            new KingbaseOracleTemporalCodecs.DurationCodec(allocator, typeOids);
        addArray(codecs, allocator, typeOids, "dsinterval", duration, Duration.class,
            value -> KingbaseTypedArrayCodec.escapeArrayElement(
                KingbaseOracleTemporalCodecs.DurationCodec.formatInterval(value)), true);
        addArray(codecs, allocator, typeOids, "intervalds", duration, Duration.class,
            value -> KingbaseTypedArrayCodec.escapeArrayElement(
                KingbaseOracleTemporalCodecs.DurationCodec.formatInterval(value)), false);

        KingbaseOracleTemporalCodecs.PeriodCodec period =
            new KingbaseOracleTemporalCodecs.PeriodCodec(allocator, typeOids);
        addArray(codecs, allocator, typeOids, "yminterval", period, Period.class,
            KingbaseOracleSpecialCodecs::encodePeriod, true);
        addArray(codecs, allocator, typeOids, "intervalym", period, Period.class,
            KingbaseOracleSpecialCodecs::encodePeriod, false);

        KingbaseOracleTemporalCodecs.LocalDateTimeCodec date =
            new KingbaseOracleTemporalCodecs.LocalDateTimeCodec(allocator, typeOids);
        addArray(codecs, allocator, typeOids, "date", date, LocalDateTime.class,
            value -> KingbaseTypedArrayCodec.escapeArrayElement(value.toString()), false);

        KingbaseOracleTextBinaryCodecs.RowIdCodec rowId =
            new KingbaseOracleTextBinaryCodecs.RowIdCodec(allocator, typeOids);
        addArray(codecs, allocator, typeOids, "rowid", rowId, RowId.class,
            value -> KingbaseTypedArrayCodec.escapeArrayElement(value.toString()), true);
        addArray(codecs, allocator, typeOids, "urowid", rowId, RowId.class,
            value -> KingbaseTypedArrayCodec.escapeArrayElement(value.toString()), false);

        KingbaseOracleLobCodecs.ClobCodec clob =
            new KingbaseOracleLobCodecs.ClobCodec(allocator, typeOids);
        addArray(codecs, allocator, typeOids, "clob", clob, Clob.class,
            value -> unsupported("Clob[] encoding requires materialized text values"), false);
        addArray(codecs, allocator, typeOids, "nclob", clob, Clob.class,
            value -> unsupported("Clob[] encoding requires materialized text values"), false);

        KingbaseOracleLobCodecs.BlobCodec blob =
            new KingbaseOracleLobCodecs.BlobCodec(allocator, typeOids);
        addArray(codecs, allocator, typeOids, "blob", blob, Blob.class,
            value -> unsupported("Blob[] encoding requires materialized binary values"), false);

        KingbaseOracleTextBinaryCodecs.ByteBufferCodec binary =
            new KingbaseOracleTextBinaryCodecs.ByteBufferCodec(allocator, typeOids);
        addArray(codecs, allocator, typeOids, "blob", binary, ByteBuffer.class,
            KingbaseOracleSpecialCodecs::encodeByteBuffer, true);
        addArray(codecs, allocator, typeOids, "cidr", cidr, InetAddress.class,
            value -> KingbaseTypedArrayCodec.escapeArrayElement(value.getHostAddress()), false);

        for (String typeName : typeOids.genericObjectTypeNames()) {
            int scalarOid = typeOids.oid(typeName);
            int arrayOid = typeOids.arrayOid(typeName);
            if (scalarOid > 0 && arrayOid > 0) {
                codecs.add(new GenericObjectArrayCodec(allocator, typeName, scalarOid, arrayOid, generic));
            }
        }
        for (Integer scalarOid : typeOids.compositeOids()) {
            String typeName = typeOids.name(scalarOid);
            int arrayOid = typeOids.arrayOid(typeName);
            if (arrayOid > 0) {
                codecs.add(new StructArrayCodec(allocator, typeName, scalarOid, arrayOid, struct));
            }
        }

        return codecs;
    }

    @SuppressWarnings("unchecked")
    private static Codec<Double> cast(MoneyCodec codec) {
        return (Codec<Double>) (Codec<?>) codec;
    }

    private static String encodePeriod(Period value) {
        long months = value.toTotalMonths();
        long absolute = Math.abs(months);
        return KingbaseTypedArrayCodec.escapeArrayElement((months < 0 ? "-" : "")
            + (absolute / 12) + "-" + (absolute % 12));
    }

    private static String encodeByteBuffer(ByteBuffer value) {
        ByteBuffer copy = value.slice();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return KingbaseTypedArrayCodec.escapeArrayElement("\\x" + ByteBufUtil.hexDump(bytes));
    }

    private static String unsupported(String message) {
        throw new UnsupportedOperationException(message);
    }

    private static <T> void addArray(List<Codec<?>> codecs, ByteBufAllocator allocator,
                                     KingbaseOracleTypeOids typeOids, String typeName,
                                     Codec<T> scalarCodec, Class<T> componentType,
                                     Function<T, String> encoder, boolean canEncode) {
        int scalarOid = typeOids.oid(typeName);
        int arrayOid = typeOids.arrayOid(typeName);
        if (scalarOid > 0 && arrayOid > 0) {
            codecs.add(new KingbaseTypedArrayCodec<>(allocator, scalarOid, arrayOid, scalarCodec,
                componentType, encoder, canEncode));
        }
    }

    private abstract static class DecodeOnlyCodec<T> implements Codec<T>, CodecMetadata {

        private final Class<T> javaType;

        DecodeOnlyCodec(Class<T> javaType) {
            this.javaType = javaType;
        }

        @Override
        public Class<?> type() {
            return this.javaType;
        }

        @Override
        public Iterable<Format> getFormats() {
            return Collections.singleton(FORMAT_TEXT);
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
            throw new UnsupportedOperationException("Decode-only codec");
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            throw new UnsupportedOperationException("Decode-only codec");
        }

        @Override
        public EncodedParameter encodeNull() {
            throw new UnsupportedOperationException("Decode-only codec");
        }
    }

    private static final class XmlCodec extends DecodeOnlyCodec<String> {

        private final ByteBufAllocator allocator;

        private final KingbaseOracleTypeOids typeOids;

        XmlCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(String.class);
            this.allocator = allocator;
            this.typeOids = typeOids;
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return format == FORMAT_TEXT && this.typeOids.isXml(dataType)
                && (type == Object.class || type == String.class);
        }

        @Override
        public String decode(@Nullable ByteBuf buffer, int dataType, Format format,
                             Class<? extends String> type) {
            return buffer == null ? null : ByteBufUtils.decode(buffer);
        }

        @Override
        public Iterable<? extends PostgresTypeIdentifier> getDataTypes() {
            return Collections.singleton(identifier(this.typeOids.oid("xml")));
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
            return encode(value, this.typeOids.oid("xml"));
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = dataType > 0 ? dataType : this.typeOids.oid("xml");
            return encodeText(this.allocator, oid, value instanceof KingbaseXml
                ? ((KingbaseXml) value).getValue() : (String) value);
        }

        @Override
        public EncodedParameter encodeNull() {
            return new EncodedParameter(FORMAT_BINARY, this.typeOids.oid("xml"), NULL_VALUE);
        }
    }

    private static final class MoneyCodec extends DecodeOnlyCodec<Object> {

        private final ByteBufAllocator allocator;

        private final KingbaseOracleTypeOids typeOids;

        MoneyCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(Object.class);
            this.allocator = allocator;
            this.typeOids = typeOids;
        }

        @Override
        public Class<?> type() {
            return Double.class;
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return format == FORMAT_TEXT && this.typeOids.isMoney(dataType)
                && (type == Object.class || type == Double.class || type == Double.TYPE
                || type == BigDecimal.class);
        }

        @Override
        public Object decode(@Nullable ByteBuf buffer, int dataType, Format format,
                             Class<? extends Object> type) {
            if (buffer == null) {
                return null;
            }
            BigDecimal value = KingbaseMoneyCodecSupport.parse(ByteBufUtils.decode(buffer));
            return type == BigDecimal.class ? value : value.doubleValue();
        }

        @Override
        public Iterable<? extends PostgresTypeIdentifier> getDataTypes() {
            return Collections.singleton(identifier(this.typeOids.oid("money")));
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
            return encode(value, this.typeOids.oid("money"));
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = dataType > 0 ? dataType : this.typeOids.oid("money");
            return encodeText(this.allocator, oid, KingbaseMoneyCodecSupport.format((Number) value));
        }

        @Override
        public EncodedParameter encodeNull() {
            return new EncodedParameter(FORMAT_BINARY, this.typeOids.oid("money"), NULL_VALUE);
        }
    }

    private static final class CidrCodec extends DecodeOnlyCodec<InetAddress> {

        private final KingbaseOracleTypeOids typeOids;

        CidrCodec(KingbaseOracleTypeOids typeOids) {
            super(InetAddress.class);
            this.typeOids = typeOids;
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return format == FORMAT_TEXT && this.typeOids.isCidr(dataType)
                && (type == Object.class || type == InetAddress.class);
        }

        @Override
        public InetAddress decode(@Nullable ByteBuf buffer, int dataType, Format format,
                                  Class<? extends InetAddress> type) {
            if (buffer == null) {
                return null;
            }
            String value = ByteBufUtils.decode(buffer);
            int slash = value.indexOf('/');
            try {
                return InetAddress.getByName(slash < 0 ? value : value.substring(0, slash));
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Cannot decode CIDR value '" + value + "'", e);
            }
        }

        @Override
        public Iterable<? extends PostgresTypeIdentifier> getDataTypes() {
            return Collections.singleton(identifier(this.typeOids.oid("cidr")));
        }
    }

    private static final class GenericObjectCodec implements Codec<KingbaseOracleObject>, CodecMetadata {

        private final ByteBufAllocator allocator;

        private final KingbaseOracleTypeOids typeOids;

        GenericObjectCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            this.allocator = allocator;
            this.typeOids = typeOids;
        }

        @Override
        public Class<?> type() {
            return KingbaseOracleObject.class;
        }

        @Override
        public Iterable<Format> getFormats() {
            return Collections.singleton(FORMAT_TEXT);
        }

        @Override
        public Iterable<? extends PostgresTypeIdentifier> getDataTypes() {
            List<PostgresTypeIdentifier> identifiers = new ArrayList<>();
            for (Integer oid : this.typeOids.genericObjectOids()) {
                identifiers.add(identifier(oid));
            }
            return identifiers;
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return format == FORMAT_TEXT && this.typeOids.isGenericObject(dataType)
                && (type == Object.class || type == KingbaseOracleObject.class);
        }

        @Override
        public boolean canEncode(Object value) {
            return value instanceof KingbaseOracleObject
                && this.typeOids.oid(((KingbaseOracleObject) value).getTypeName()) > 0;
        }

        @Override
        public boolean canEncodeNull(Class<?> type) {
            return false;
        }

        @Override
        public KingbaseOracleObject decode(@Nullable ByteBuf buffer, int dataType, Format format,
                                           Class<? extends KingbaseOracleObject> type) {
            return buffer == null ? null
                : new KingbaseOracleObject(this.typeOids.name(dataType), ByteBufUtils.decode(buffer));
        }

        @Override
        public EncodedParameter encode(Object value) {
            KingbaseOracleObject object = (KingbaseOracleObject) value;
            int oid = this.typeOids.oid(object.getTypeName());
            if (oid == 0) {
                throw new IllegalArgumentException("Unknown Oracle-mode type: " + object.getTypeName());
            }
            return encodeText(this.allocator, oid, object.getValue());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            KingbaseOracleObject object = (KingbaseOracleObject) value;
            int oid = dataType > 0 ? dataType : this.typeOids.oid(object.getTypeName());
            return encodeText(this.allocator, oid, object.getValue());
        }

        @Override
        public EncodedParameter encodeNull() {
            throw new UnsupportedOperationException(
                "A database type name is required to encode a null KingbaseOracleObject");
        }
    }

    private static final class StructCodec implements Codec<KingbaseOracleStruct>, CodecMetadata {

        private final ByteBufAllocator allocator;

        private final KingbaseOracleTypeOids typeOids;

        StructCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            this.allocator = allocator;
            this.typeOids = typeOids;
        }

        @Override
        public Class<?> type() {
            return KingbaseOracleStruct.class;
        }

        @Override
        public Iterable<Format> getFormats() {
            return Collections.singleton(FORMAT_TEXT);
        }

        @Override
        public Iterable<? extends PostgresTypeIdentifier> getDataTypes() {
            List<PostgresTypeIdentifier> identifiers = new ArrayList<>();
            for (Integer oid : this.typeOids.compositeOids()) {
                identifiers.add(identifier(oid));
            }
            return identifiers;
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return format == FORMAT_TEXT && this.typeOids.isComposite(dataType)
                && (type == Object.class || type == KingbaseOracleStruct.class);
        }

        @Override
        public boolean canEncode(Object value) {
            return value instanceof KingbaseOracleStruct
                && this.typeOids.oid(((KingbaseOracleStruct) value).getTypeName()) > 0;
        }

        @Override
        public boolean canEncodeNull(Class<?> type) {
            return false;
        }

        @Override
        public KingbaseOracleStruct decode(@Nullable ByteBuf buffer, int dataType, Format format,
                                           Class<? extends KingbaseOracleStruct> type) {
            return buffer == null ? null
                : KingbaseOracleStruct.decode(this.typeOids.name(dataType), ByteBufUtils.decode(buffer));
        }

        @Override
        public EncodedParameter encode(Object value) {
            KingbaseOracleStruct struct = (KingbaseOracleStruct) value;
            return encodeText(this.allocator, this.typeOids.oid(struct.getTypeName()), struct.getValue());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            KingbaseOracleStruct struct = (KingbaseOracleStruct) value;
            int oid = dataType > 0 ? dataType : this.typeOids.oid(struct.getTypeName());
            return encodeText(this.allocator, oid, struct.getValue());
        }

        @Override
        public EncodedParameter encodeNull() {
            throw new UnsupportedOperationException(
                "A database type name is required to encode a null KingbaseOracleStruct");
        }
    }

    private static final class GenericObjectArrayCodec
        extends KingbaseTypedArrayCodec<KingbaseOracleObject> {

        private final String typeName;

        GenericObjectArrayCodec(ByteBufAllocator allocator, String typeName, int scalarOid,
                                int arrayOid, Codec<KingbaseOracleObject> scalarCodec) {
            super(allocator, scalarOid, arrayOid, scalarCodec, KingbaseOracleObject.class,
                value -> KingbaseTypedArrayCodec.escapeArrayElement(value.getValue()), true);
            this.typeName = typeName;
        }

        @Override
        public boolean canEncode(Object value) {
            if (!super.canEncode(value)) {
                return false;
            }
            for (KingbaseOracleObject item : (KingbaseOracleObject[]) value) {
                if (item != null && !this.typeName.equals(item.getTypeName())) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean canEncodeNull(Class<?> type) {
            return false;
        }
    }

    private static final class StructArrayCodec extends KingbaseTypedArrayCodec<KingbaseOracleStruct> {

        private final String typeName;

        StructArrayCodec(ByteBufAllocator allocator, String typeName, int scalarOid, int arrayOid,
                         Codec<KingbaseOracleStruct> scalarCodec) {
            super(allocator, scalarOid, arrayOid, scalarCodec, KingbaseOracleStruct.class,
                value -> KingbaseTypedArrayCodec.escapeArrayElement(value.getValue()), true);
            this.typeName = typeName;
        }

        @Override
        public boolean canEncode(Object value) {
            if (!super.canEncode(value)) {
                return false;
            }
            for (KingbaseOracleStruct item : (KingbaseOracleStruct[]) value) {
                if (item != null && !this.typeName.equals(item.getTypeName())) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean canEncodeNull(Class<?> type) {
            return false;
        }
    }

    private static PostgresTypeIdentifier identifier(int oid) {
        return () -> oid;
    }

}
