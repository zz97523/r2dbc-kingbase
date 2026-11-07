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

package io.r2dbc.kingbase;

import io.r2dbc.postgresql.client.SSLMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class KingbaseConnectionConfigurationUnitTests {

    @Test
    void configuresUnixDomainSocket() {
        KingbaseConnectionConfiguration configuration = KingbaseConnectionConfiguration.builder()
            .socket("/tmp/.s.KINGBASE.54321")
            .username("test")
            .database("test")
            .build();

        assertThat(configuration.asPostgresqlConfiguration())
            .extracting("singleHostConfiguration")
            .hasFieldOrPropertyWithValue("socket", "/tmp/.s.KINGBASE.54321");
    }

    @Test
    void configuresClientCertificateAuthentication() {
        KingbaseConnectionConfiguration configuration = KingbaseConnectionConfiguration.builder()
            .host("localhost")
            .username("test")
            .database("test")
            .sslMode(SSLMode.VERIFY_CA)
            .sslRootCert("server.crt")
            .sslCert("client.crt")
            .sslKey("client.key")
            .sslPassword(null)
            .build();

        assertThat(configuration.asPostgresqlConfiguration())
            .hasFieldOrProperty("sslConfig");
    }
}
