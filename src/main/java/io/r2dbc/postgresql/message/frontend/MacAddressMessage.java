/*
 * Copyright 2017 the original author or authors.
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

package io.r2dbc.postgresql.message.frontend;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.r2dbc.postgresql.util.Assert;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Objects;

import static io.r2dbc.postgresql.message.frontend.FrontendMessageUtils.writeByte;
import static io.r2dbc.postgresql.message.frontend.FrontendMessageUtils.writeCStringUTF8;
import static io.r2dbc.postgresql.message.frontend.FrontendMessageUtils.writeLengthPlaceholder;
import static io.r2dbc.postgresql.message.frontend.FrontendMessageUtils.writeSize;

/**
 * The response to a Kingbase MAC address authentication request.
 */
public final class MacAddressMessage implements FrontendMessage {

    private final String macAddresses;

    /**
     * Create a MAC address response.
     *
     * @param macAddresses slash-separated MAC addresses
     * @throws IllegalArgumentException if {@code macAddresses} is {@code null}
     */
    public MacAddressMessage(String macAddresses) {
        this.macAddresses = Assert.requireNonNull(macAddresses, "macAddresses must not be null");
    }

    @Override
    public Publisher<ByteBuf> encode(ByteBufAllocator byteBufAllocator) {
        Assert.requireNonNull(byteBufAllocator, "byteBufAllocator must not be null");

        return Mono.fromSupplier(() -> {
            ByteBuf out = byteBufAllocator.ioBuffer();

            writeByte(out, 'p');
            writeLengthPlaceholder(out);
            writeCStringUTF8(out, this.macAddresses);

            return writeSize(out);
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MacAddressMessage that = (MacAddressMessage) o;
        return Objects.equals(this.macAddresses, that.macAddresses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.macAddresses);
    }

    @Override
    public String toString() {
        return "MacAddressMessage{macAddresses='...'}";
    }

}
