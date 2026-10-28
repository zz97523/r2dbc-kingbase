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

import io.r2dbc.postgresql.message.backend.AuthenticationMacAddress;
import io.r2dbc.postgresql.message.backend.AuthenticationMessage;
import io.r2dbc.postgresql.message.frontend.FrontendMessage;
import io.r2dbc.postgresql.message.frontend.MacAddressMessage;
import io.r2dbc.postgresql.util.Assert;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.function.Supplier;

/**
 * Handles the Kingbase type 19 MAC address authentication request.
 */
public final class MacAddressAuthenticationHandler implements AuthenticationHandler {

    private final Supplier<String> macAddressSupplier;

    /**
     * Create a handler that reads MAC addresses from the local network interfaces.
     */
    public MacAddressAuthenticationHandler() {
        this(MacAddressAuthenticationHandler::getMacAddressString);
    }

    MacAddressAuthenticationHandler(Supplier<String> macAddressSupplier) {
        this.macAddressSupplier = Assert.requireNonNull(macAddressSupplier, "macAddressSupplier must not be null");
    }

    /**
     * Returns whether this handler supports the authentication message.
     *
     * @param message the message to inspect
     * @return whether the message is a MAC address authentication request
     */
    public static boolean supports(AuthenticationMessage message) {
        Assert.requireNonNull(message, "message must not be null");
        return message instanceof AuthenticationMacAddress;
    }

    @Override
    public FrontendMessage handle(AuthenticationMessage message) {
        Assert.requireNonNull(message, "message must not be null");

        if (!(message instanceof AuthenticationMacAddress)) {
            throw new IllegalArgumentException(String.format("Cannot handle %s message", message.getClass().getSimpleName()));
        }

        return new MacAddressMessage(this.macAddressSupplier.get());
    }

    static String formatMacAddress(byte[] hardwareAddress) {
        if (hardwareAddress.length == 0) {
            return "";
        }

        StringBuilder result = new StringBuilder(hardwareAddress.length * 3 - 1);

        for (int i = 0; i < hardwareAddress.length; i++) {
            if (i > 0) {
                result.append(':');
            }
            result.append(String.format("%02X", hardwareAddress[i] & 0xFF));
        }

        return result.toString();
    }

    private static String getMacAddressString() {
        try {
            StringBuilder result = new StringBuilder();
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

            if (networkInterfaces == null) {
                return "";
            }

            while (networkInterfaces.hasMoreElements()) {
                byte[] hardwareAddress = networkInterfaces.nextElement().getHardwareAddress();
                if (hardwareAddress == null || hardwareAddress.length == 0) {
                    continue;
                }
                if (result.length() > 0) {
                    result.append('/');
                }
                result.append(formatMacAddress(hardwareAddress));
            }

            return result.toString();
        } catch (SocketException e) {
            throw new IllegalStateException("Cannot retrieve local MAC addresses", e);
        }
    }

}
