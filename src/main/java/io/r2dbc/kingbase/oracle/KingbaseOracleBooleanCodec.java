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
import io.r2dbc.postgresql.client.EncodedParameter;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.CodecMetadata;
import io.r2dbc.postgresql.codec.PostgresTypeIdentifier;
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.postgresql.util.Assert;
import io.r2dbc.postgresql.util.ByteBufUtils;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Collections;

import static io.r2dbc.postgresql.client.EncodedParameter.NULL_VALUE;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BIT;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BOOL;
import static io.r2dbc.postgresql.message.Format.FORMAT_BINARY;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;
import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.decodeNumber;
import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.encodeText;
import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.resolveBindOid;

/**
 * Oracle-oriented boolean codec.
 * <p>
 * BOOLEAN columns use PG {@code t}/{@code f}; numeric/{@code NUMBER(1)} columns use {@code 1}/{@code 0}.
 */
final class KingbaseOracleBooleanCodec implements Codec<Boolean>, CodecMetadata {

    private final ByteBufAllocator allocator;

    private final KingbaseOracleTypeOids typeOids;

    KingbaseOracleBooleanCodec(ByteBufAllocator allocator) {
        this(allocator, KingbaseOracleTypeOids.postgresqlDefaults());
    }

    KingbaseOracleBooleanCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
        this.allocator = Assert.requireNonNull(allocator, "allocator must not be null");
        this.typeOids = Assert.requireNonNull(typeOids, "typeOids must not be null");
    }

    @Override
    public Class<?> type() {
        return Boolean.class;
    }

    @Override
    public Iterable<? extends PostgresTypeIdentifier> getDataTypes() {
        return Collections.singleton(BIT);
    }

    @Override
    public boolean canDecode(int dataType, Format format, Class<?> type) {
        Assert.requireNonNull(format, "format must not be null");
        Assert.requireNonNull(type, "type must not be null");
        if (type == Object.class) {
            return this.typeOids.isBitBoolean(dataType);
        }
        if (!(type == Boolean.class || type == Boolean.TYPE)) {
            return false;
        }
        return this.typeOids.canDecodeTextExtension(dataType, format, this.typeOids.isBoolean(dataType));
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
        if (format == FORMAT_BINARY && KingbaseOracleTypeOids.equalsPg(dataType, BIT)) {
            return decodeBinaryBit(buffer);
        }
        if (format == FORMAT_TEXT || !PostgresqlObjectId.isValid(dataType)) {
            String text = ByteBufUtils.decode(buffer).trim();
            if (text.isEmpty()) {
                return false;
            }
            if ("t".equalsIgnoreCase(text) || "true".equalsIgnoreCase(text) || "y".equalsIgnoreCase(text)
                || "yes".equalsIgnoreCase(text)) {
                return true;
            }
            if ("f".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text) || "n".equalsIgnoreCase(text)
                || "no".equalsIgnoreCase(text)) {
                return false;
            }
            try {
                return new BigDecimal(text).signum() != 0;
            } catch (NumberFormatException ex) {
                return !text.isEmpty();
            }
        }
        if (KingbaseOracleTypeOids.equalsPg(dataType, BOOL)) {
            return buffer.readBoolean();
        }
        return decodeNumber(buffer, dataType, format).intValue() != 0;
    }

    @Override
    public EncodedParameter encode(Object value) {
        // Default to BOOLEAN text t/f — Kingbase BOOLEAN rejected numeric "1" as NULL in round-trip
        return encode(value, this.typeOids.encodeBoolean());
    }

    @Override
    public EncodedParameter encode(Object value, int dataType) {
        boolean b = (Boolean) value;
        int oid = resolveBindOid(dataType, this.typeOids.encodeBoolean());
        if (this.typeOids.isBitBoolean(oid)) {
            return encodeText(this.allocator, oid, b ? "1" : "0");
        }
        if (isNumericBoolOid(oid)) {
            return encodeText(this.allocator, oid, b ? "1" : "0");
        }
        return encodeText(this.allocator, oid, b ? "t" : "f");
    }

    @Override
    public EncodedParameter encodeNull() {
        return new EncodedParameter(FORMAT_BINARY, this.typeOids.encodeBoolean(), NULL_VALUE);
    }

    private boolean isNumericBoolOid(int oid) {
        if (KingbaseOracleTypeOids.equalsPg(oid, BOOL)) {
            return false;
        }
        return this.typeOids.isNumber(oid);
    }

    private static boolean decodeBinaryBit(ByteBuf buffer) {
        if (buffer.readableBytes() == 1) {
            return buffer.readUnsignedByte() != 0;
        }
        if (buffer.readableBytes() < Integer.BYTES) {
            return false;
        }
        int bitCount = buffer.readInt();
        int byteCount = Math.min(buffer.readableBytes(), (Math.max(bitCount, 0) + 7) / 8);
        for (int i = 0; i < byteCount; i++) {
            if (buffer.readUnsignedByte() != 0) {
                return true;
            }
        }
        return false;
    }

}
