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

import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.DefaultCodecs;
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.postgresql.util.ByteBufUtils;
import io.r2dbc.spi.Blob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;
import java.util.List;

import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;
import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;
import static org.assertj.core.api.Assertions.assertThat;

final class KingbaseMysqlBlobCodecUnitTests {

    private static final byte[] EXPECTED = new byte[]{0, 1, 2, 10, 64, 127, (byte) 128, (byte) 255};

    private DefaultCodecs codecs;

    @BeforeEach
    void beforeEach() {
        this.codecs = new DefaultCodecs(TEST, false);
        List<Codec<?>> mysqlCodecs = KingbaseMysqlCodecs.create(TEST);
        for (int i = mysqlCodecs.size() - 1; i >= 0; i--) {
            this.codecs.addFirst(mysqlCodecs.get(i));
        }
    }

    @Test
    void shouldDecodeMysqlBlobTextAsDefaultByteBuffer() {
        Object decoded = this.codecs.decode(payload(), PostgresqlObjectId.BYTEA.getObjectId(),
            FORMAT_TEXT, Object.class);

        assertThat(decoded).isInstanceOf(ByteBuffer.class);
        assertThat(toBytes((ByteBuffer) decoded)).containsExactly(EXPECTED);
    }

    @Test
    void shouldKeepStringAsDefaultForVarchar() {
        Object decoded = this.codecs.decode(ByteBufUtils.encode(TEST, "kingbase"),
            PostgresqlObjectId.VARCHAR.getObjectId(), FORMAT_TEXT, Object.class);

        assertThat(decoded).isEqualTo("kingbase");
    }

    @Test
    void shouldKeepNumberAsDefaultForSmallint() {
        Object decoded = this.codecs.decode(ByteBufUtils.encode(TEST, "7"),
            PostgresqlObjectId.INT2.getObjectId(), FORMAT_TEXT, Object.class);

        assertThat(decoded).isInstanceOf(Number.class);
        assertThat(((Number) decoded).intValue()).isEqualTo(7);
    }

    @Test
    void shouldDecodeMysqlBlobTextAsByteArray() {
        byte[] decoded = this.codecs.decode(payload(), PostgresqlObjectId.BYTEA.getObjectId(),
            FORMAT_TEXT, byte[].class);

        assertThat(decoded).containsExactly(EXPECTED);
    }

    @Test
    void shouldDecodeMysqlBlobTextAsBlob() {
        Blob decoded = this.codecs.decode(payload(), PostgresqlObjectId.BYTEA.getObjectId(),
            FORMAT_TEXT, Blob.class);

        List<ByteBuffer> chunks = Flux.from(decoded.stream()).collectList().block();
        assertThat(chunks).hasSize(1);
        assertThat(toBytes(chunks.get(0))).containsExactly(EXPECTED);
    }

    private io.netty.buffer.ByteBuf payload() {
        return ByteBufUtils.encode(TEST, "\\000\\001\\002\\012@\\177\\200\\377");
    }

    private byte[] toBytes(ByteBuffer value) {
        ByteBuffer copy = value.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }
}
