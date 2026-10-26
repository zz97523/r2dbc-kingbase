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
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.message.Format;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.AbstractNumericOracleCodec;
import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.decodeNumber;
import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.toBigDecimal;
import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.toBigInteger;

/**
 * Numeric codecs for Kingbase Oracle mode (NUMBER / BINARY_FLOAT / BINARY_DOUBLE habits).
 */
final class KingbaseOracleNumericCodecs {

    private KingbaseOracleNumericCodecs() {
    }

    static List<Codec<?>> create(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
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

    static final class ByteCodec extends AbstractNumericOracleCodec<Byte> {

        ByteCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, Byte.class, KingbaseOracleType.SMALLINT, typeOids);
        }

        @Override
        Byte doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Byte> type) {
            return decodeNumber(buffer, dataType, format).byteValue();
        }
    }

    static final class ShortCodec extends AbstractNumericOracleCodec<Short> {

        ShortCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, Short.class, KingbaseOracleType.SMALLINT, typeOids);
        }

        @Override
        Short doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Short> type) {
            return decodeNumber(buffer, dataType, format).shortValue();
        }
    }

    static final class IntegerCodec extends AbstractNumericOracleCodec<Integer> {

        IntegerCodec(ByteBufAllocator allocator) {
            this(allocator, KingbaseOracleTypeOids.postgresqlDefaults());
        }

        IntegerCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, Integer.class, KingbaseOracleType.INTEGER, typeOids);
        }

        @Override
        Integer doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Integer> type) {
            return decodeNumber(buffer, dataType, format).intValue();
        }
    }

    static final class LongCodec extends AbstractNumericOracleCodec<Long> {

        LongCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, Long.class, KingbaseOracleType.BIGINT, typeOids);
        }

        @Override
        Long doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Long> type) {
            return decodeNumber(buffer, dataType, format).longValue();
        }
    }

    static final class FloatCodec extends AbstractNumericOracleCodec<Float> {

        FloatCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, Float.class, KingbaseOracleType.BINARY_FLOAT, typeOids);
        }

        @Override
        Float doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Float> type) {
            return decodeNumber(buffer, dataType, format).floatValue();
        }
    }

    static final class DoubleCodec extends AbstractNumericOracleCodec<Double> {

        DoubleCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, Double.class, KingbaseOracleType.BINARY_DOUBLE, typeOids);
        }

        @Override
        Double doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Double> type) {
            return decodeNumber(buffer, dataType, format).doubleValue();
        }
    }

    static final class BigDecimalCodec extends AbstractNumericOracleCodec<BigDecimal> {

        BigDecimalCodec(ByteBufAllocator allocator) {
            this(allocator, KingbaseOracleTypeOids.postgresqlDefaults());
        }

        BigDecimalCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, BigDecimal.class, KingbaseOracleType.NUMBER, typeOids);
        }

        @Override
        BigDecimal doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends BigDecimal> type) {
            return toBigDecimal(decodeNumber(buffer, dataType, format));
        }
    }

    static final class BigIntegerCodec extends AbstractNumericOracleCodec<BigInteger> {

        BigIntegerCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, BigInteger.class, KingbaseOracleType.NUMBER, typeOids);
        }

        @Override
        BigInteger doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends BigInteger> type) {
            return toBigInteger(decodeNumber(buffer, dataType, format));
        }
    }

}
