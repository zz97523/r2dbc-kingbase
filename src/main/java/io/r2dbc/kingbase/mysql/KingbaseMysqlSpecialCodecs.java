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
import io.netty.buffer.ByteBufUtil;
import io.r2dbc.kingbase.KingbaseXml;
import io.r2dbc.postgresql.client.EncodedParameter;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.CodecMetadata;
import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.postgresql.codec.Point;
import io.r2dbc.postgresql.codec.PostgresTypeIdentifier;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.postgresql.util.Assert;
import io.r2dbc.postgresql.util.ByteBufUtils;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.r2dbc.kingbase.mysql.KingbaseMysqlCodecSupport.encodeText;
import static io.r2dbc.kingbase.mysql.KingbaseMysqlCodecSupport.nullParameter;
import static io.r2dbc.kingbase.mysql.KingbaseMysqlCodecSupport.resolveBindOid;
import static io.r2dbc.postgresql.message.Format.FORMAT_BINARY;

/** JSON, XML and geometric codecs whose OIDs can vary in KingbaseES MySQL mode. */
final class KingbaseMysqlSpecialCodecs {

    private KingbaseMysqlSpecialCodecs() {
    }

    static List<Codec<?>> create(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
        List<Codec<?>> codecs = new ArrayList<>(4);
        codecs.add(new JsonCodec(allocator, typeOids));
        codecs.add(new JsonStringCodec(typeOids));
        codecs.add(new XmlCodec(allocator, typeOids));
        codecs.add(new PointCodec(allocator, typeOids));
        return codecs;
    }

    /** Uses {@link Json} so JSON binds cannot be confused with ordinary VARCHAR binds. */
    static final class JsonCodec implements Codec<Json>, CodecMetadata {

        private final ByteBufAllocator allocator;

        private final KingbaseMysqlTypeOids typeOids;

        JsonCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            this.allocator = Assert.requireNonNull(allocator, "allocator must not be null");
            this.typeOids = Assert.requireNonNull(typeOids, "typeOids must not be null");
        }

        @Override
        public Class<?> type() {
            return Json.class;
        }

        @Override
        public Iterable<PostgresTypeIdentifier> getDataTypes() {
            return identifiers(this.typeOids.oid("json"), this.typeOids.oid("jsonb"));
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return this.typeOids.isJson(dataType) && (type == Json.class || type == Object.class);
        }

        @Override
        public boolean canEncode(Object value) {
            return value instanceof Json;
        }

        @Override
        public boolean canEncodeNull(Class<?> type) {
            return type == Json.class;
        }

        @Override
        public @Nullable Json decode(@Nullable ByteBuf buffer, int dataType, Format format,
                                     Class<? extends Json> type) {
            if (buffer == null) {
                return null;
            }
            if (format == FORMAT_BINARY && this.typeOids.isJsonb(dataType)
                && buffer.isReadable() && buffer.getByte(buffer.readerIndex()) == 1) {
                buffer.skipBytes(1);
            }
            return Json.of(ByteBufUtil.getBytes(buffer));
        }

        @Override
        public EncodedParameter encode(Object value) {
            int oid = this.typeOids.oid("jsonb") > 0
                ? this.typeOids.encodeJsonb() : this.typeOids.encodeJson();
            return encode(value, oid);
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int fallback = this.typeOids.oid("jsonb") > 0
                ? this.typeOids.encodeJsonb() : this.typeOids.encodeJson();
            int oid = resolveBindOid(dataType, fallback);
            return encodeText(this.allocator, oid, ((Json) value).asString());
        }

        @Override
        public EncodedParameter encodeNull() {
            int oid = this.typeOids.oid("jsonb") > 0
                ? this.typeOids.encodeJsonb() : this.typeOids.encodeJson();
            return nullParameter(oid);
        }
    }

    /** Explicit String target for JSON/JSONB; default Object reads remain {@link Json}. */
    static final class JsonStringCodec implements Codec<String> {

        private final KingbaseMysqlTypeOids typeOids;

        JsonStringCodec(KingbaseMysqlTypeOids typeOids) {
            this.typeOids = Assert.requireNonNull(typeOids, "typeOids must not be null");
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == String.class && this.typeOids.isJson(dataType);
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
        public @Nullable String decode(@Nullable ByteBuf buffer, int dataType, Format format,
                                       Class<? extends String> type) {
            if (buffer == null) {
                return null;
            }
            if (format == FORMAT_BINARY && this.typeOids.isJsonb(dataType)
                && buffer.isReadable() && buffer.getByte(buffer.readerIndex()) == 1) {
                buffer.skipBytes(1);
            }
            return buffer.toString(StandardCharsets.UTF_8);
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

    /** XML is returned as String; {@link KingbaseXml} selects XML for parameter encoding. */
    static final class XmlCodec implements Codec<String>, CodecMetadata {

        private final ByteBufAllocator allocator;

        private final KingbaseMysqlTypeOids typeOids;

        XmlCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            this.allocator = Assert.requireNonNull(allocator, "allocator must not be null");
            this.typeOids = Assert.requireNonNull(typeOids, "typeOids must not be null");
        }

        @Override
        public Class<?> type() {
            return String.class;
        }

        @Override
        public Iterable<PostgresTypeIdentifier> getDataTypes() {
            return identifiers(this.typeOids.oid("xml"));
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return this.typeOids.isXml(dataType) && (type == String.class || type == Object.class);
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
        public @Nullable String decode(@Nullable ByteBuf buffer, int dataType, Format format,
                                       Class<? extends String> type) {
            return buffer == null ? null : ByteBufUtils.decode(buffer);
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

    /** JDBC exposes MySQL-mode point as a geometric value; support dynamic and stock OIDs. */
    static final class PointCodec implements Codec<Point>, CodecMetadata {

        private final ByteBufAllocator allocator;

        private final KingbaseMysqlTypeOids typeOids;

        PointCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            this.allocator = Assert.requireNonNull(allocator, "allocator must not be null");
            this.typeOids = Assert.requireNonNull(typeOids, "typeOids must not be null");
        }

        @Override
        public Class<?> type() {
            return Point.class;
        }

        @Override
        public Iterable<PostgresTypeIdentifier> getDataTypes() {
            return identifiers(this.typeOids.oid("point"));
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return this.typeOids.isPoint(dataType) && (type == Point.class || type == Object.class);
        }

        @Override
        public boolean canEncode(Object value) {
            return value instanceof Point;
        }

        @Override
        public boolean canEncodeNull(Class<?> type) {
            return type == Point.class;
        }

        @Override
        public @Nullable Point decode(@Nullable ByteBuf buffer, int dataType, Format format,
                                      Class<? extends Point> type) {
            if (buffer == null) {
                return null;
            }
            if (format == FORMAT_BINARY) {
                return Point.of(buffer.readDouble(), buffer.readDouble());
            }
            String value = ByteBufUtils.decode(buffer).trim();
            if (value.startsWith("(") && value.endsWith(")")) {
                value = value.substring(1, value.length() - 1);
            }
            String[] coordinates = value.split(",", -1);
            if (coordinates.length != 2) {
                throw new IllegalArgumentException("Cannot decode point from '" + value + "'");
            }
            return Point.of(Double.parseDouble(coordinates[0].trim()),
                Double.parseDouble(coordinates[1].trim()));
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodePoint());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            Point point = (Point) value;
            int oid = resolveBindOid(dataType, this.typeOids.encodePoint());
            return new EncodedParameter(FORMAT_BINARY, oid, Mono.fromSupplier(() ->
                this.allocator.buffer(16).writeDouble(point.getX()).writeDouble(point.getY())));
        }

        @Override
        public EncodedParameter encodeNull() {
            return nullParameter(this.typeOids.encodePoint());
        }
    }

    private static Iterable<PostgresTypeIdentifier> identifiers(int... oids) {
        List<PostgresTypeIdentifier> result = new ArrayList<>(oids.length);
        for (int oid : oids) {
            if (oid > 0) {
                boolean duplicate = false;
                for (PostgresTypeIdentifier existing : result) {
                    if (existing.getObjectId() == oid) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    result.add(() -> oid);
                }
            }
        }
        return result.isEmpty() ? Collections.emptyList() : result;
    }
}
