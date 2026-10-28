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
import io.r2dbc.kingbase.KingbaseMoney;
import io.r2dbc.kingbase.KingbaseTypedArrayCodec;
import io.r2dbc.kingbase.KingbaseXml;
import io.r2dbc.postgresql.codec.Codec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Function;

/**
 * Builds the Kingbase SQL Server-mode codec set for the PostgreSQL wire protocol.
 * <p>
 * Java type coverage follows r2dbc-mssql habits; OID resolution uses {@link KingbaseSqlServerTypeOids}
 * from {@code pg_type} (not the TDS protocol).
 */
final class KingbaseSqlServerCodecs {

    private KingbaseSqlServerCodecs() {
    }

    static List<Codec<?>> create(ByteBufAllocator allocator) {
        return create(allocator, KingbaseSqlServerTypeOids.postgresqlDefaults());
    }

    /**
     * @param allocator buffer allocator
     * @param typeOids  Kingbase/PG type OID catalog for this connection
     * @return codec list in lookup priority order (index 0 = highest)
     */
    static List<Codec<?>> create(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
        List<Codec<?>> codecs = new ArrayList<>();

        codecs.addAll(KingbaseSqlServerNumericCodecs.create(allocator, typeOids));
        KingbaseSqlServerTextBinaryCodecs.MoneyCodec money =
            new KingbaseSqlServerTextBinaryCodecs.MoneyCodec(allocator, typeOids);
        KingbaseSqlServerTextBinaryCodecs.MoneyValueCodec moneyValue =
            new KingbaseSqlServerTextBinaryCodecs.MoneyValueCodec(allocator, typeOids);
        KingbaseSqlServerTextBinaryCodecs.MoneyBigDecimalCodec moneyDecimal =
            new KingbaseSqlServerTextBinaryCodecs.MoneyBigDecimalCodec(allocator, typeOids);
        KingbaseSqlServerTextBinaryCodecs.XmlCodec xml =
            new KingbaseSqlServerTextBinaryCodecs.XmlCodec(allocator, typeOids);
        KingbaseSqlServerTextBinaryCodecs.XmlValueCodec xmlValue =
            new KingbaseSqlServerTextBinaryCodecs.XmlValueCodec(allocator, typeOids);
        codecs.add(moneyValue);
        codecs.add(moneyDecimal);
        codecs.add(money);
        codecs.add(new KingbaseSqlServerBooleanCodec(allocator, typeOids));
        codecs.add(new KingbaseSqlServerTextBinaryCodecs.UuidCodec(allocator, typeOids));
        codecs.add(new KingbaseSqlServerTextBinaryCodecs.BitSetCodec(allocator, typeOids));
        codecs.addAll(KingbaseSqlServerTemporalCodecs.create(allocator, typeOids));
        codecs.add(xmlValue);
        codecs.add(xml);
        codecs.add(new KingbaseSqlServerTextBinaryCodecs.StringCodec(allocator, typeOids));
        codecs.add(new KingbaseSqlServerTextBinaryCodecs.EnumCodec(allocator, typeOids));
        codecs.addAll(KingbaseSqlServerLobCodecs.create(allocator, typeOids));
        codecs.add(new KingbaseSqlServerTextBinaryCodecs.ByteArrayCodec(allocator, typeOids));
        codecs.add(new KingbaseSqlServerTextBinaryCodecs.ByteBufferCodec(allocator, typeOids));

        addArray(codecs, allocator, typeOids, "money", moneyDecimal, java.math.BigDecimal.class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement(value.toString()), false);
        addArray(codecs, allocator, typeOids, "xml", xml, String.class,
            io.r2dbc.postgresql.codec.ArrayCodec::escapeArrayElement, false);
        addArray(codecs, allocator, typeOids, "money", moneyValue, KingbaseMoney.class,
            value -> value.asBigDecimal().toPlainString(), true);
        addArray(codecs, allocator, typeOids, "xml", xmlValue, KingbaseXml.class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement(value.getValue()), true);

        KingbaseSqlServerNumericCodecs.ShortCodec smallint =
            new KingbaseSqlServerNumericCodecs.ShortCodec(allocator, typeOids);
        KingbaseSqlServerNumericCodecs.IntegerCodec integer =
            new KingbaseSqlServerNumericCodecs.IntegerCodec(allocator, typeOids);
        KingbaseSqlServerNumericCodecs.LongCodec bigint =
            new KingbaseSqlServerNumericCodecs.LongCodec(allocator, typeOids);
        KingbaseSqlServerNumericCodecs.FloatCodec real =
            new KingbaseSqlServerNumericCodecs.FloatCodec(allocator, typeOids);
        KingbaseSqlServerNumericCodecs.DoubleCodec doubleValue =
            new KingbaseSqlServerNumericCodecs.DoubleCodec(allocator, typeOids);
        KingbaseSqlServerNumericCodecs.BigDecimalCodec decimal =
            new KingbaseSqlServerNumericCodecs.BigDecimalCodec(allocator, typeOids);
        KingbaseSqlServerTemporalCodecs.LocalDateCodec date =
            new KingbaseSqlServerTemporalCodecs.LocalDateCodec(allocator, typeOids);
        KingbaseSqlServerTemporalCodecs.LocalTimeCodec time =
            new KingbaseSqlServerTemporalCodecs.LocalTimeCodec(allocator, typeOids);
        KingbaseSqlServerTemporalCodecs.LocalDateTimeCodec dateTime =
            new KingbaseSqlServerTemporalCodecs.LocalDateTimeCodec(allocator, typeOids);
        KingbaseSqlServerTemporalCodecs.OffsetDateTimeCodec offsetDateTime =
            new KingbaseSqlServerTemporalCodecs.OffsetDateTimeCodec(allocator, typeOids);
        KingbaseSqlServerBooleanCodec bool = new KingbaseSqlServerBooleanCodec(allocator, typeOids);
        KingbaseSqlServerTextBinaryCodecs.StringCodec string =
            new KingbaseSqlServerTextBinaryCodecs.StringCodec(allocator, typeOids);
        KingbaseSqlServerTextBinaryCodecs.ByteArrayCodec binary =
            new KingbaseSqlServerTextBinaryCodecs.ByteArrayCodec(allocator, typeOids);
        KingbaseSqlServerTextBinaryCodecs.UuidCodec uuid =
            new KingbaseSqlServerTextBinaryCodecs.UuidCodec(allocator, typeOids);

        addArrays(codecs, allocator, typeOids, smallint, Short.class, value -> value.toString(), true,
            "tinyint", "smallint");
        addArrays(codecs, allocator, typeOids, integer, Integer.class, value -> value.toString(), true,
            "int", "integer");
        addArrays(codecs, allocator, typeOids, bigint, Long.class, value -> value.toString(), true, "bigint");
        addArrays(codecs, allocator, typeOids, real, Float.class, value -> value.toString(), true, "real");
        addArrays(codecs, allocator, typeOids, doubleValue, Double.class, value -> value.toString(), true,
            "float", "double");
        addArrays(codecs, allocator, typeOids, decimal, BigDecimal.class, value -> value.toPlainString(), true,
            "decimal", "numeric", "smallmoney");
        addArrays(codecs, allocator, typeOids, date, LocalDate.class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement(value.toString()), true, "date");
        addArrays(codecs, allocator, typeOids, time, LocalTime.class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement(value.toString()), true, "time");
        addArrays(codecs, allocator, typeOids, dateTime, LocalDateTime.class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement(value.toString()), true,
            "datetime", "datetime2", "smalldatetime");
        addArrays(codecs, allocator, typeOids, offsetDateTime, OffsetDateTime.class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement(value.toString()), true,
            "datetimeoffset");
        addArrays(codecs, allocator, typeOids, bool, Boolean.class, value -> value ? "1" : "0", true,
            "bit", "bool", "boolean");
        addArrays(codecs, allocator, typeOids, string, String.class,
            io.r2dbc.postgresql.codec.ArrayCodec::escapeArrayElement, true,
            "nvarchar", "nchar", "varcharbyte", "bpcharbyte", "varchar", "text", "ntext", "bpchar",
            "clob", "nclob");
        addArrays(codecs, allocator, typeOids, binary, byte[].class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement("\\x"
                + io.netty.buffer.ByteBufUtil.hexDump(value)), true, "varbinary", "binary", "image", "bytea");
        addArrays(codecs, allocator, typeOids, uuid, UUID.class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement(value.toString()), true,
            "uniqueidentifier", "uuid");

        return Collections.unmodifiableList(codecs);
    }

    private static <T> void addArray(List<Codec<?>> codecs, ByteBufAllocator allocator,
                                     KingbaseSqlServerTypeOids typeOids, String typeName,
                                     Codec<T> scalarCodec, Class<T> componentType,
                                     java.util.function.Function<T, String> encoder, boolean canEncode) {
        int scalarOid = typeOids.oid(typeName);
        if (scalarOid == 0 && typeName.equals("money")) {
            scalarOid = typeOids.encodeMoney();
        }
        if (scalarOid == 0 && typeName.equals("xml")) {
            scalarOid = typeOids.encodeXml();
        }
        int arrayOid = typeOids.arrayOid(typeName);
        if (scalarOid > 0 && arrayOid > 0) {
            codecs.add(new KingbaseTypedArrayCodec<>(allocator, scalarOid, arrayOid, scalarCodec,
                componentType, encoder, canEncode));
        }
    }

    private static <T> void addArrays(List<Codec<?>> codecs, ByteBufAllocator allocator,
                                      KingbaseSqlServerTypeOids typeOids, Codec<T> scalarCodec,
                                      Class<T> componentType, Function<T, String> encoder,
                                      boolean canEncode, String... typeNames) {
        for (String typeName : typeNames) {
            addArray(codecs, allocator, typeOids, typeName, scalarCodec, componentType, encoder,
                canEncode && typeName.equals(typeNames[0]));
        }
    }

}
