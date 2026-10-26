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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.r2dbc.postgresql.client.EncodedParameter;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.postgresql.util.ByteBufUtils;
import io.r2dbc.spi.Blob;
import io.r2dbc.spi.Clob;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BYTEA;
import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.resolveBindOid;
import static io.r2dbc.kingbase.oracle.KingbaseOracleTypeOids.equalsPg;
import static io.r2dbc.postgresql.message.Format.FORMAT_BINARY;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;

/**
 * CLOB / BLOB codecs for Kingbase Oracle mode on the PostgreSQL wire.
 */
final class KingbaseOracleLobCodecs {

    private KingbaseOracleLobCodecs() {
    }

    static List<Codec<?>> create(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
        List<Codec<?>> list = new ArrayList<>(2);
        list.add(new ClobCodec(allocator, typeOids));
        list.add(new BlobCodec(allocator, typeOids));
        return list;
    }

    static final class ClobCodec extends KingbaseOracleCodecSupport.AbstractOracleCodec<Clob> {

        ClobCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, Clob.class, KingbaseOracleType.CLOB, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == Clob.class
                && this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isText(dataType) || this.typeOids.isBinary(dataType));
        }

        @Override
        Clob doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Clob> type) {
            String text;
            if (equalsPg(dataType, BYTEA) || this.typeOids.isBinary(dataType)) {
                text = new String(readBytes(buffer, format), StandardCharsets.UTF_8);
            } else {
                text = ByteBufUtils.decode(buffer);
            }
            return Clob.from(Mono.just(text));
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeText());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            Clob clob = (Clob) value;
            int oid = resolveBindOid(dataType, this.typeOids.encodeText());
            return new EncodedParameter(FORMAT_TEXT, oid,
                Flux.from(clob.stream())
                    .reduce(new StringBuilder(), StringBuilder::append)
                    .defaultIfEmpty(new StringBuilder())
                    .map(sb -> ByteBufUtils.encode(this.allocator, sb.toString()))
                    .concatWith(Flux.from(clob.discard()).then(Mono.empty())));
        }

        private static byte[] readBytes(ByteBuf buffer, Format format) {
            if (format == FORMAT_BINARY) {
                byte[] bytes = new byte[buffer.readableBytes()];
                buffer.readBytes(bytes);
                return bytes;
            }
            String text = ByteBufUtils.decode(buffer);
            if (text.startsWith("\\x") || text.startsWith("\\X")) {
                return ByteBufUtil.decodeHexDump(text.substring(2));
            }
            return text.getBytes(StandardCharsets.UTF_8);
        }
    }

    static final class BlobCodec extends KingbaseOracleCodecSupport.AbstractOracleCodec<Blob> {

        BlobCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, Blob.class, KingbaseOracleType.BLOB, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == Blob.class
                && this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isBinary(dataType) || this.typeOids.isText(dataType));
        }

        @Override
        Blob doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Blob> type) {
            return Blob.from(Mono.just(ByteBuffer.wrap(readBytes(buffer, format))));
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeBinary());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            Blob blob = (Blob) value;
            int oid = resolveBindOid(dataType, this.typeOids.encodeBinary());
            return new EncodedParameter(FORMAT_BINARY, oid,
                Flux.from(blob.stream())
                    .collectList()
                    .map(chunks -> {
                        int total = chunks.stream().mapToInt(ByteBuffer::remaining).sum();
                        byte[] bytes = new byte[total];
                        int offset = 0;
                        for (ByteBuffer chunk : chunks) {
                            int len = chunk.remaining();
                            chunk.get(bytes, offset, len);
                            offset += len;
                        }
                        return this.allocator.buffer(bytes.length).writeBytes(bytes);
                    })
                    .concatWith(Flux.from(blob.discard()).then(Mono.empty())));
        }

        private static byte[] readBytes(ByteBuf buffer, Format format) {
            if (format == FORMAT_BINARY) {
                byte[] bytes = new byte[buffer.readableBytes()];
                buffer.readBytes(bytes);
                return bytes;
            }
            String text = ByteBufUtils.decode(buffer);
            if (text.startsWith("\\x") || text.startsWith("\\X")) {
                return ByteBufUtil.decodeHexDump(text.substring(2));
            }
            return decodeOctalEscapes(text);
        }

        /**
         * Decode PostgreSQL escape-format bytea (e.g. {@code \012\024\036} or plain ASCII).
         * Handles both octal escapes (\NNN) and double-backslash (\\).
         */
        private static byte[] decodeOctalEscapes(String text) {
            byte[] buf = new byte[text.length()];
            int out = 0;
            int i = 0;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (c == '\\' && i + 1 < text.length()) {
                    char next = text.charAt(i + 1);
                    if (next == '\\') {
                        buf[out++] = '\\';
                        i += 2;
                    } else if (next >= '0' && next <= '7' && i + 3 < text.length()) {
                        int octal = (next - '0') * 64
                            + (text.charAt(i + 2) - '0') * 8
                            + (text.charAt(i + 3) - '0');
                        buf[out++] = (byte) octal;
                        i += 4;
                    } else {
                        buf[out++] = (byte) c;
                        i++;
                    }
                } else {
                    buf[out++] = (byte) c;
                    i++;
                }
            }
            byte[] result = new byte[out];
            System.arraycopy(buf, 0, result, 0, out);
            return result;
        }
    }

}
