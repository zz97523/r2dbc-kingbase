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
import io.r2dbc.kingbase.KingbaseTypedArrayCodec;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.postgresql.codec.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.Function;

/**
 * Builds the Kingbase MySQL-mode codec set for the PostgreSQL wire protocol.
 * <p>
 * Java type coverage follows r2dbc-mysql habits; OID resolution uses {@link KingbaseMysqlTypeOids}
 * from {@code pg_type} (not the MySQL client protocol).
 */
final class KingbaseMysqlCodecs {

    private KingbaseMysqlCodecs() {
    }

    static List<Codec<?>> create(ByteBufAllocator allocator) {
        return create(allocator, KingbaseMysqlTypeOids.postgresqlDefaults());
    }

    /**
     * @param allocator buffer allocator
     * @param typeOids  Kingbase/PG type OID catalog for this connection
     * @return codec list in lookup priority order (index 0 = highest)
     */
    static List<Codec<?>> create(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
        List<Codec<?>> codecs = new ArrayList<>();

        codecs.addAll(KingbaseMysqlNumericCodecs.create(allocator, typeOids));
        KingbaseMysqlTextBinaryCodecs.MoneyCodec money =
            new KingbaseMysqlTextBinaryCodecs.MoneyCodec(allocator, typeOids);
        KingbaseMysqlTextBinaryCodecs.RowIdCodec rowId =
            new KingbaseMysqlTextBinaryCodecs.RowIdCodec(allocator, typeOids);
        codecs.add(money);
        codecs.add(new KingbaseMysqlTextBinaryCodecs.MoneyBigDecimalCodec(allocator, typeOids));
        codecs.add(new KingbaseMysqlBooleanCodec(allocator, typeOids));
        codecs.add(new KingbaseMysqlTextBinaryCodecs.BitSetCodec(allocator, typeOids));
        codecs.addAll(KingbaseMysqlTemporalCodecs.create(allocator, typeOids));

        KingbaseMysqlSpecialCodecs.JsonCodec json =
            new KingbaseMysqlSpecialCodecs.JsonCodec(allocator, typeOids);
        KingbaseMysqlSpecialCodecs.JsonStringCodec jsonString =
            new KingbaseMysqlSpecialCodecs.JsonStringCodec(typeOids);
        KingbaseMysqlSpecialCodecs.XmlCodec xml =
            new KingbaseMysqlSpecialCodecs.XmlCodec(allocator, typeOids);
        KingbaseMysqlSpecialCodecs.PointCodec point =
            new KingbaseMysqlSpecialCodecs.PointCodec(allocator, typeOids);
        codecs.add(json);
        codecs.add(jsonString);
        codecs.add(xml);
        codecs.add(point);
        codecs.add(new KingbaseMysqlTextBinaryCodecs.StringCodec(allocator, typeOids));
        codecs.add(rowId);
        codecs.add(new KingbaseMysqlTextBinaryCodecs.EnumCodec(allocator, typeOids));
        codecs.addAll(KingbaseMysqlLobCodecs.create(allocator, typeOids));
        codecs.add(new KingbaseMysqlTextBinaryCodecs.ByteArrayCodec(allocator, typeOids));
        codecs.add(new KingbaseMysqlTextBinaryCodecs.ByteBufferCodec(allocator, typeOids));

        addArray(codecs, allocator, typeOids, "money", money, Double.class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement(value.toString()), true);
        addArray(codecs, allocator, typeOids, "rowid", rowId, java.sql.RowId.class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement(value.toString()), true);
        addArray(codecs, allocator, typeOids, "jsonb", json, Json.class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement(value.asString()), true);
        addArray(codecs, allocator, typeOids, "json", json, Json.class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement(value.asString()), false);
        addArray(codecs, allocator, typeOids, "jsonb", jsonString, String.class,
            io.r2dbc.postgresql.codec.ArrayCodec::escapeArrayElement, false);
        addArray(codecs, allocator, typeOids, "json", jsonString, String.class,
            io.r2dbc.postgresql.codec.ArrayCodec::escapeArrayElement, false);
        addArray(codecs, allocator, typeOids, "xml", xml, String.class,
            io.r2dbc.postgresql.codec.ArrayCodec::escapeArrayElement, false);
        addArray(codecs, allocator, typeOids, "point", point, Point.class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement(value.toString()), true);

        KingbaseMysqlNumericCodecs.IntegerCodec integer =
            new KingbaseMysqlNumericCodecs.IntegerCodec(allocator, typeOids);
        KingbaseMysqlNumericCodecs.ShortCodec smallint =
            new KingbaseMysqlNumericCodecs.ShortCodec(allocator, typeOids);
        KingbaseMysqlNumericCodecs.LongCodec bigint =
            new KingbaseMysqlNumericCodecs.LongCodec(allocator, typeOids);
        KingbaseMysqlNumericCodecs.BigIntegerCodec unsignedBigint =
            new KingbaseMysqlNumericCodecs.BigIntegerCodec(allocator, typeOids);
        KingbaseMysqlNumericCodecs.FloatCodec real =
            new KingbaseMysqlNumericCodecs.FloatCodec(allocator, typeOids);
        KingbaseMysqlNumericCodecs.DoubleCodec doubleValue =
            new KingbaseMysqlNumericCodecs.DoubleCodec(allocator, typeOids);
        KingbaseMysqlNumericCodecs.BigDecimalCodec decimal =
            new KingbaseMysqlNumericCodecs.BigDecimalCodec(allocator, typeOids);
        KingbaseMysqlTemporalCodecs.LocalDateCodec date =
            new KingbaseMysqlTemporalCodecs.LocalDateCodec(allocator, typeOids);
        KingbaseMysqlTemporalCodecs.LocalTimeCodec time =
            new KingbaseMysqlTemporalCodecs.LocalTimeCodec(allocator, typeOids);
        KingbaseMysqlTemporalCodecs.LocalDateTimeCodec dateTime =
            new KingbaseMysqlTemporalCodecs.LocalDateTimeCodec(allocator, typeOids);
        KingbaseMysqlTemporalCodecs.YearCodec year =
            new KingbaseMysqlTemporalCodecs.YearCodec(allocator, typeOids);
        KingbaseMysqlBooleanCodec bool = new KingbaseMysqlBooleanCodec(allocator, typeOids);
        KingbaseMysqlTextBinaryCodecs.StringCodec string =
            new KingbaseMysqlTextBinaryCodecs.StringCodec(allocator, typeOids);
        KingbaseMysqlTextBinaryCodecs.ByteArrayCodec binary =
            new KingbaseMysqlTextBinaryCodecs.ByteArrayCodec(allocator, typeOids);

        addArrays(codecs, allocator, typeOids, integer, Integer.class, value -> value.toString(), true,
            "int", "integer");
        addArrays(codecs, allocator, typeOids, smallint, Short.class, value -> value.toString(), true,
            "smallint");
        addArrays(codecs, allocator, typeOids, bigint, Long.class, value -> value.toString(), true,
            "bigint");
        addArrays(codecs, allocator, typeOids, unsignedBigint, BigInteger.class, value -> value.toString(), true,
            "bigint unsigned");
        addArrays(codecs, allocator, typeOids, real, Float.class, value -> value.toString(), true,
            "float");
        addArrays(codecs, allocator, typeOids, doubleValue, Double.class, value -> value.toString(), true,
            "double");
        addArrays(codecs, allocator, typeOids, decimal, BigDecimal.class, value -> value.toPlainString(), true,
            "decimal", "numeric");
        addArrays(codecs, allocator, typeOids, date, LocalDate.class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement(value.toString()), true, "date");
        addArrays(codecs, allocator, typeOids, time, LocalTime.class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement(value.toString()), true, "time");
        addArrays(codecs, allocator, typeOids, dateTime, LocalDateTime.class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement(value.toString()), true,
            "datetime", "timestamp");
        addArrays(codecs, allocator, typeOids, year, Year.class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement(value.toString()), true, "year");
        addArrays(codecs, allocator, typeOids, bool, Boolean.class, value -> value ? "1" : "0", true,
            "bit", "bool", "boolean");
        addArrays(codecs, allocator, typeOids, string, String.class,
            io.r2dbc.postgresql.codec.ArrayCodec::escapeArrayElement, true,
            "varchar", "text", "tinytext", "mediumtext", "longtext", "bpcharbyte", "varcharbyte");
        addArrays(codecs, allocator, typeOids, binary, byte[].class,
            value -> io.r2dbc.postgresql.codec.ArrayCodec.escapeArrayElement("\\x"
                + io.netty.buffer.ByteBufUtil.hexDump(value)), true, "varbinary", "binary");

        return Collections.unmodifiableList(codecs);
    }

    private static <T> void addArrays(List<Codec<?>> codecs, ByteBufAllocator allocator,
                                      KingbaseMysqlTypeOids typeOids, Codec<T> scalarCodec,
                                      Class<T> componentType, Function<T, String> encoder,
                                      boolean canEncode, String... typeNames) {
        for (String typeName : typeNames) {
            addArray(codecs, allocator, typeOids, typeName, scalarCodec, componentType, encoder,
                canEncode && typeName.equals(typeNames[0]));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> void addArray(List<Codec<?>> codecs, ByteBufAllocator allocator,
                                     KingbaseMysqlTypeOids typeOids, String typeName,
                                     Codec<T> scalarCodec, Class<T> componentType,
                                     java.util.function.Function<T, String> encoder, boolean canEncode) {
        int scalarOid = typeOids.oid(typeName);
        int arrayOid = typeOids.arrayOid(typeName);
        if (scalarOid > 0 && arrayOid > 0) {
            codecs.add(new KingbaseTypedArrayCodec(allocator, scalarOid, arrayOid, scalarCodec,
                componentType, encoder, canEncode));
        }
    }

}
