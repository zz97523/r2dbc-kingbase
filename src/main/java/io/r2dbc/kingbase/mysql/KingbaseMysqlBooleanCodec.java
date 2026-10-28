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
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.postgresql.util.Assert;
import io.r2dbc.postgresql.util.ByteBufUtils;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

import static io.r2dbc.postgresql.client.EncodedParameter.NULL_VALUE;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BOOL;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BPCHAR;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT2;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT4;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT8;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.NUMERIC;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TEXT;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.VARCHAR;
import static io.r2dbc.postgresql.message.Format.FORMAT_BINARY;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;

/**
 * MySQL-oriented boolean codec for KingbaseES MySQL mode (aligned with r2dbc-mysql
 * {@code BooleanCodec}, not Kingbase JDBC).
 * <p>
 * Encodes as text {@code "1"}/{@code "0"} with OID {@code int2} (TINYINT stand-in on the PG wire).
 * Decodes numeric/text as nonzero → {@code true} (r2dbc-mysql text: {@code != '0'}).
 */
final class KingbaseMysqlBooleanCodec implements Codec<Boolean> {

    private static final Set<PostgresqlObjectId> DECODE_TYPES = EnumSet.of(
        BOOL, INT2, INT4, INT8, NUMERIC, TEXT, VARCHAR, BPCHAR
    );

    private final ByteBufAllocator allocator;

    private final KingbaseMysqlTypeOids typeOids;

    KingbaseMysqlBooleanCodec(ByteBufAllocator allocator) {
        this(allocator, KingbaseMysqlTypeOids.postgresqlDefaults());
    }

    KingbaseMysqlBooleanCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
        this.allocator = Assert.requireNonNull(allocator, "allocator must not be null");
        this.typeOids = Assert.requireNonNull(typeOids, "typeOids must not be null");
    }

    @Override
    public boolean canDecode(int dataType, Format format, Class<?> type) {
        Assert.requireNonNull(format, "format must not be null");
        Assert.requireNonNull(type, "type must not be null");

        if (!(type == Boolean.class || type == Boolean.TYPE)) {
            return false;
        }
        boolean known = PostgresqlObjectId.isValid(dataType)
            && DECODE_TYPES.contains(PostgresqlObjectId.valueOf(dataType));
        return this.typeOids.canDecodeTextExtension(dataType, format,
            known || this.typeOids.isBoolean(dataType) || this.typeOids.isNumeric(dataType)
                || this.typeOids.isBit(dataType) || this.typeOids.isText(dataType));
    }

    @Override
    public boolean canEncode(Object value) {
        return value instanceof Boolean;
    }

    @Override
    public boolean canEncodeNull(Class<?> type) {
        return type == Boolean.class || type == Boolean.TYPE;
    }

    @Override
    public @Nullable Boolean decode(@Nullable ByteBuf buffer, int dataType, Format format, Class<? extends Boolean> type) {
        if (buffer == null) {
            return null;
        }

        if (format == FORMAT_BINARY && PostgresqlObjectId.isValid(dataType)) {
            PostgresqlObjectId oid = PostgresqlObjectId.valueOf(dataType);
            switch (oid) {
                case BOOL:
                    return buffer.readBoolean();
                case INT2:
                    return buffer.readShort() != 0;
                case INT4:
                    return buffer.readInt() != 0;
                case INT8:
                    return buffer.readLong() != 0L;
                case NUMERIC:
                    break;
                default:
                    break;
            }
        }

        String text = ByteBufUtils.decode(buffer).trim();
        if (text.isEmpty()) {
            return false;
        }

        // r2dbc-mysql text path: single byte != '0'; also accept common bool literals
        if ("1".equals(text)
            || "t".equalsIgnoreCase(text)
            || "true".equalsIgnoreCase(text)
            || "y".equalsIgnoreCase(text)
            || "yes".equalsIgnoreCase(text)
            || "on".equalsIgnoreCase(text)) {
            return true;
        }
        if ("0".equals(text)
            || "f".equalsIgnoreCase(text)
            || "false".equalsIgnoreCase(text)
            || "n".equalsIgnoreCase(text)
            || "no".equalsIgnoreCase(text)
            || "off".equalsIgnoreCase(text)) {
            return false;
        }

        try {
            return new BigDecimal(text).compareTo(BigDecimal.ZERO) != 0;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Cannot decode boolean from value '" + text + "'", ex);
        }
    }

    @Override
    public EncodedParameter encode(Object value) {
        return encode(value, this.typeOids.encodeBoolean());
    }

    @Override
    public EncodedParameter encode(Object value, int dataType) {
        Assert.requireNonNull(value, "value must not be null");
        boolean flag = (Boolean) value;

        if (PostgresqlObjectId.isValid(dataType) && PostgresqlObjectId.valueOf(dataType) == BOOL) {
            return new EncodedParameter(FORMAT_TEXT, BOOL.getObjectId(),
                Mono.fromSupplier(() -> ByteBufUtils.encode(this.allocator, flag ? "t" : "f")));
        }

        // r2dbc-mysql encodes boolean as TINYINT 0/1 → INT2 on PG wire
        int oid = dataType > 0 ? dataType : this.typeOids.encodeBoolean();
        return new EncodedParameter(FORMAT_TEXT, oid,
            Mono.fromSupplier(() -> ByteBufUtils.encode(this.allocator, flag ? "1" : "0")));
    }

    @Override
    public EncodedParameter encodeNull() {
        return new EncodedParameter(FORMAT_BINARY, this.typeOids.encodeBoolean(), NULL_VALUE);
    }

}
