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

import io.r2dbc.postgresql.message.backend.AuthenticationOk;
import io.r2dbc.postgresql.message.backend.AuthenticationSASL;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link TrustAuthenticationHandler}.
 */
final class TrustAuthenticationHandlerUnitTests {

    @Test
    void handle() {
        assertThat(TrustAuthenticationHandler.supports(AuthenticationOk.INSTANCE)).isTrue();
        assertThat(TrustAuthenticationHandler.INSTANCE.handle(AuthenticationOk.INSTANCE)).isNull();
    }

    @Test
    void rejectAuthenticationChallenge() {
        AuthenticationSASL message = new AuthenticationSASL(Collections.singletonList("SCRAM-SHA-256"));

        assertThat(TrustAuthenticationHandler.supports(message)).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> TrustAuthenticationHandler.INSTANCE.handle(message))
            .withMessage("Cannot handle AuthenticationSASL message");
    }

}
