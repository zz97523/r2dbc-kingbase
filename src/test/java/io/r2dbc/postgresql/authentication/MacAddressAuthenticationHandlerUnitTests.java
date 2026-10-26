/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */

package io.r2dbc.postgresql.authentication;

import io.r2dbc.postgresql.message.backend.AuthenticationGSS;
import io.r2dbc.postgresql.message.backend.AuthenticationMacAddress;
import io.r2dbc.postgresql.message.frontend.MacAddressMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

final class MacAddressAuthenticationHandlerUnitTests {

    @Test
    void formatMacAddress() {
        assertThat(MacAddressAuthenticationHandler.formatMacAddress(
            new byte[]{0, 17, 34, 51, (byte) 0xAA, (byte) 0xFF}))
            .isEqualTo("00:11:22:33:AA:FF");
    }

    @Test
    void handleAuthenticationMacAddress() {
        MacAddressAuthenticationHandler handler =
            new MacAddressAuthenticationHandler(() -> "00:11:22:33:44:55");

        assertThat(handler.handle(AuthenticationMacAddress.INSTANCE))
            .isEqualTo(new MacAddressMessage("00:11:22:33:44:55"));
    }

    @Test
    void rejectUnsupportedMessage() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new MacAddressAuthenticationHandler().handle(AuthenticationGSS.INSTANCE))
            .withMessage("Cannot handle AuthenticationGSS message");
    }

}
