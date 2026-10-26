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
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.message.Format;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.AbstractNumericSqlServerCodec;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.decodeNumber;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.toBigDecimal;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.toBigInteger;

/**
 * Numeric codecs for Kingbase SQL Server mode (mirrors r2dbc-mssql numeric set).
 */
final class KingbaseSqlServerNumericCodecs {

    private KingbaseSqlServerNumericCodecs() {
    }

    static List<Codec<?>> create(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
        List<Codec<?>> list = new ArrayList<>(8);
        list.add(new ByteCodec(allocator, typeOids));
        list.add(new ShortCodec(allocator, typeOids));
        list.add(new IntegerCodec(allocator, typeOids));
        list.add(new LongCodec(allocator, typeOids));
        list.add(new BigIntegerCodec(allocator, typeOids));
        list.add(new BigDecimalCodec(allocator, typeOids));
        list.add(new FloatCodec(allocator, typeOids));
        list.add(new DoubleCodec(allocator, typeOids));
        return list;
    }

    static final class ByteCodec extends AbstractNumericSqlServerCodec<Byte> {

        ByteCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, Byte.class, KingbaseSqlServerType.TINYINT, typeOids);
        }

        @Override
        Byte doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Byte> type) {
            return decodeNumber(buffer, dataType, format).byteValue();
        }
    }

    static final class ShortCodec extends AbstractNumericSqlServerCodec<Short> {

        ShortCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, Short.class, KingbaseSqlServerType.SMALLINT, typeOids);
        }

        @Override
        Short doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Short> type) {
            return decodeNumber(buffer, dataType, format).shortValue();
        }
    }

    static final class IntegerCodec extends AbstractNumericSqlServerCodec<Integer> {

        IntegerCodec(ByteBufAllocator allocator) {
            this(allocator, KingbaseSqlServerTypeOids.postgresqlDefaults());
        }

        IntegerCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, Integer.class, KingbaseSqlServerType.INT, typeOids);
        }

        @Override
        Integer doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Integer> type) {
            return decodeNumber(buffer, dataType, format).intValue();
        }
    }

    static final class LongCodec extends AbstractNumericSqlServerCodec<Long> {

        LongCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, Long.class, KingbaseSqlServerType.BIGINT, typeOids);
        }

        @Override
        Long doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Long> type) {
            return decodeNumber(buffer, dataType, format).longValue();
        }
    }

    static final class FloatCodec extends AbstractNumericSqlServerCodec<Float> {

        FloatCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, Float.class, KingbaseSqlServerType.REAL, typeOids);
        }

        @Override
        Float doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Float> type) {
            return decodeNumber(buffer, dataType, format).floatValue();
        }
    }

    static final class DoubleCodec extends AbstractNumericSqlServerCodec<Double> {

        DoubleCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, Double.class, KingbaseSqlServerType.FLOAT, typeOids);
        }

        @Override
        Double doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Double> type) {
            return decodeNumber(buffer, dataType, format).doubleValue();
        }
    }

    static final class BigDecimalCodec extends AbstractNumericSqlServerCodec<BigDecimal> {

        BigDecimalCodec(ByteBufAllocator allocator) {
            this(allocator, KingbaseSqlServerTypeOids.postgresqlDefaults());
        }

        BigDecimalCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, BigDecimal.class, KingbaseSqlServerType.DECIMAL, typeOids);
        }

        @Override
        BigDecimal doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends BigDecimal> type) {
            return toBigDecimal(decodeNumber(buffer, dataType, format));
        }
    }

    static final class BigIntegerCodec extends AbstractNumericSqlServerCodec<BigInteger> {

        BigIntegerCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, BigInteger.class, KingbaseSqlServerType.BIGINT, typeOids);
        }

        @Override
        BigInteger doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends BigInteger> type) {
            return toBigInteger(decodeNumber(buffer, dataType, format));
        }
    }

}
