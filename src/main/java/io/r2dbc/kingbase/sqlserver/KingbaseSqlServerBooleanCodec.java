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
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static io.r2dbc.postgresql.client.EncodedParameter.NULL_VALUE;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BIT;
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
 * SQL Server-oriented boolean codec for KingbaseES SQL Server mode (aligned with r2dbc-mssql
 * {@code BooleanCodec}).
 * <p>
 * Encodes as text {@code "1"}/{@code "0"} with OID {@code int2} (BIT/TINYINT stand-in on the PG wire).
 * Decodes numeric/text as nonzero → {@code true}.
 */
final class KingbaseSqlServerBooleanCodec implements Codec<Boolean>, CodecMetadata {

    private static final Set<PostgresqlObjectId> DECODE_TYPES = EnumSet.of(
        BOOL, INT2, INT4, INT8, NUMERIC, TEXT, VARCHAR, BPCHAR
    );

    private final ByteBufAllocator allocator;

    private final KingbaseSqlServerTypeOids typeOids;

    KingbaseSqlServerBooleanCodec(ByteBufAllocator allocator) {
        this(allocator, KingbaseSqlServerTypeOids.postgresqlDefaults());
    }

    KingbaseSqlServerBooleanCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
        this.allocator = Assert.requireNonNull(allocator, "allocator must not be null");
        this.typeOids = Assert.requireNonNull(typeOids, "typeOids must not be null");
    }

    @Override
    public Class<?> type() {
        return Boolean.class;
    }

    @Override
    public Iterable<? extends PostgresTypeIdentifier> getDataTypes() {
        return Collections.singleton((PostgresTypeIdentifier) () -> this.typeOids.encodeBitAsInt());
    }

    @Override
    public boolean canDecode(int dataType, Format format, Class<?> type) {
        Assert.requireNonNull(format, "format must not be null");
        Assert.requireNonNull(type, "type must not be null");

        if (type == Object.class) {
            return this.typeOids.isDefaultBoolean(dataType);
        }
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

        // r2dbc-mssql: BIT is 0/1; also accept common bool literals
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
        return encode(value, this.typeOids.encodeBitAsInt());
    }

    @Override
    public EncodedParameter encode(Object value, int dataType) {
        Assert.requireNonNull(value, "value must not be null");
        boolean flag = (Boolean) value;

        // SQL Server BIT/TINYINT：始终 0/1 文本，不用 PG bool(t/f) 或 pg_catalog.bit
        int oid = KingbaseSqlServerCodecSupport.resolveBindOid(dataType, this.typeOids.encodeBitAsInt());
        if (PostgresqlObjectId.isValid(oid)) {
            PostgresqlObjectId pg = PostgresqlObjectId.valueOf(oid);
            if (pg == BOOL || pg == BIT) {
                oid = this.typeOids.encodeBitAsInt();
            }
        }
        return new EncodedParameter(FORMAT_TEXT, oid,
            Mono.fromSupplier(() -> ByteBufUtils.encode(this.allocator, flag ? "1" : "0")));
    }

    @Override
    public EncodedParameter encodeNull() {
        return new EncodedParameter(FORMAT_BINARY, this.typeOids.encodeBitAsInt(), NULL_VALUE);
    }

}
