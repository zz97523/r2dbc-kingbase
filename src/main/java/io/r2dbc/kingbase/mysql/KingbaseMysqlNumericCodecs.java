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
import io.r2dbc.postgresql.client.EncodedParameter;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.message.Format;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static io.r2dbc.kingbase.mysql.KingbaseMysqlCodecSupport.AbstractNumericMysqlCodec;
import static io.r2dbc.kingbase.mysql.KingbaseMysqlCodecSupport.decodeNumber;
import static io.r2dbc.kingbase.mysql.KingbaseMysqlCodecSupport.nullParameter;
import static io.r2dbc.kingbase.mysql.KingbaseMysqlCodecSupport.toBigDecimal;
import static io.r2dbc.kingbase.mysql.KingbaseMysqlCodecSupport.toBigInteger;

/**
 * Numeric codecs for Kingbase MySQL mode (mirrors r2dbc-mysql numeric set).
 */
final class KingbaseMysqlNumericCodecs {

    private KingbaseMysqlNumericCodecs() {
    }

    static List<Codec<?>> create(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
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

    static final class ByteCodec extends AbstractNumericMysqlCodec<Byte> {

        ByteCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, Byte.class, KingbaseMysqlType.TINYINT, typeOids);
        }

        @Override
        Byte doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Byte> type) {
            return decodeNumber(buffer, dataType, format).byteValue();
        }
    }

    static final class ShortCodec extends AbstractNumericMysqlCodec<Short> {

        ShortCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, Short.class, KingbaseMysqlType.SMALLINT, typeOids);
        }

        @Override
        Short doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Short> type) {
            return decodeNumber(buffer, dataType, format).shortValue();
        }
    }

    static final class IntegerCodec extends AbstractNumericMysqlCodec<Integer> {

        IntegerCodec(ByteBufAllocator allocator) {
            this(allocator, KingbaseMysqlTypeOids.postgresqlDefaults());
        }

        IntegerCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, Integer.class, KingbaseMysqlType.INT, typeOids);
        }

        @Override
        Integer doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Integer> type) {
            return decodeNumber(buffer, dataType, format).intValue();
        }
    }

    static final class LongCodec extends AbstractNumericMysqlCodec<Long> {

        LongCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, Long.class, KingbaseMysqlType.BIGINT, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            if (type == Object.class) {
                return this.typeOids.isIntUnsigned(dataType);
            }
            return super.canDecode(dataType, format, type);
        }

        @Override
        Long doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Long> type) {
            return decodeNumber(buffer, dataType, format).longValue();
        }
    }

    static final class FloatCodec extends AbstractNumericMysqlCodec<Float> {

        FloatCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, Float.class, KingbaseMysqlType.FLOAT, typeOids);
        }

        @Override
        Float doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Float> type) {
            return decodeNumber(buffer, dataType, format).floatValue();
        }
    }

    static final class DoubleCodec extends AbstractNumericMysqlCodec<Double> {

        DoubleCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, Double.class, KingbaseMysqlType.DOUBLE, typeOids);
        }

        @Override
        Double doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Double> type) {
            return decodeNumber(buffer, dataType, format).doubleValue();
        }
    }

    static final class BigDecimalCodec extends AbstractNumericMysqlCodec<BigDecimal> {

        BigDecimalCodec(ByteBufAllocator allocator) {
            this(allocator, KingbaseMysqlTypeOids.postgresqlDefaults());
        }

        BigDecimalCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, BigDecimal.class, KingbaseMysqlType.DECIMAL, typeOids);
        }

        @Override
        BigDecimal doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends BigDecimal> type) {
            return toBigDecimal(decodeNumber(buffer, dataType, format));
        }
    }

    static final class BigIntegerCodec extends AbstractNumericMysqlCodec<BigInteger> {

        BigIntegerCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, BigInteger.class, KingbaseMysqlType.BIGINT, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            if (type == Object.class) {
                return this.typeOids.isBigintUnsigned(dataType);
            }
            return super.canDecode(dataType, format, type);
        }

        @Override
        BigInteger doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends BigInteger> type) {
            return toBigInteger(decodeNumber(buffer, dataType, format));
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeBigintUnsigned());
        }

        @Override
        public EncodedParameter encodeNull() {
            return nullParameter(this.typeOids.encodeBigintUnsigned());
        }
    }

}
