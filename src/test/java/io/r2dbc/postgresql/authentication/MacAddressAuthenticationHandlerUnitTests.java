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

package io.r2dbc.postgresql.authentication;

import io.r2dbc.postgresql.message.backend.AuthenticationGSS;
import io.r2dbc.postgresql.message.backend.AuthenticationMacAddress;
import io.r2dbc.postgresql.message.frontend.MacAddressMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link MacAddressAuthenticationHandler}.
 */
final class MacAddressAuthenticationHandlerUnitTests {

    @Test
    void formatMacAddress() {
        assertThat(MacAddressAuthenticationHandler.formatMacAddress(new byte[]{0, 17, 34, 51, (byte) 0xAA, (byte) 0xFF}))
            .isEqualTo("00:11:22:33:AA:FF");
    }

    @Test
    void formatEmptyMacAddress() {
        assertThat(MacAddressAuthenticationHandler.formatMacAddress(new byte[0])).isEmpty();
    }

    @Test
    void handleAuthenticationMacAddress() {
        MacAddressAuthenticationHandler handler = new MacAddressAuthenticationHandler(() -> "00:11:22:33:44:55");

        assertThat(handler.handle(AuthenticationMacAddress.INSTANCE))
            .isEqualTo(new MacAddressMessage("00:11:22:33:44:55"));
    }

    @Test
    void handleNoMessage() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MacAddressAuthenticationHandler().handle(null))
            .withMessage("message must not be null");
    }

    @Test
    void handleUnknown() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MacAddressAuthenticationHandler().handle(AuthenticationGSS.INSTANCE))
            .withMessage("Cannot handle AuthenticationGSS message");
    }

}
