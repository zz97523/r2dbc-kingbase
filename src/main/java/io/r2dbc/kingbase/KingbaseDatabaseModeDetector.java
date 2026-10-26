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

import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

/**
 * Detects KingbaseES compatibility mode via {@code SHOW database_mode}.
 * <p>
 * Detection does <strong>not</strong> fall back silently: Kingbase speaks the PostgreSQL wire
 * protocol, so a silent fallback to {@link KingbaseMode#POSTGRESQL} would look like a successful
 * connect while the wrong mapping layer is active. Explicit Kingbase modes are also checked
 * against this detected value before the mapping layer is initialized.
 */
public final class KingbaseDatabaseModeDetector {

    private static final Logger LOG = Loggers.getLogger(KingbaseDatabaseModeDetector.class);

    private KingbaseDatabaseModeDetector() {
    }

    /**
     * Result of a successful {@code SHOW database_mode} probe.
     */
    public static final class Detection {

        private final String databaseMode;

        private final KingbaseMode mode;

        public Detection(String databaseMode, KingbaseMode mode) {
            this.databaseMode = databaseMode;
            this.mode = mode;
        }

        /**
         * Raw {@code database_mode} text from the server (e.g. {@code mysql}, {@code pg}).
         *
         * @return raw value
         */
        public String getDatabaseMode() {
            return this.databaseMode;
        }

        /**
         * Mapped client {@link KingbaseMode}.
         *
         * @return mode
         */
        public KingbaseMode getMode() {
            return this.mode;
        }
    }

    /**
     * Query {@code SHOW database_mode} and map the result to a {@link KingbaseMode}.
     * Fails if the statement errors, returns empty, or yields an unrecognized value.
     *
     * @param connection live connection
     * @return detection result
     */
    public static Mono<Detection> detect(PostgresqlConnection connection) {
        return connection.createStatement("SHOW database_mode")
            .execute()
            .flatMap(result -> result.map((row, metadata) -> {
                Object value = row.get(0, String.class);
                return value == null ? "" : value.toString().trim();
            }))
            .next()
            .switchIfEmpty(Mono.error(new R2dbcNonTransientResourceException(
                "SHOW database_mode returned no row")))
            .map(raw -> {
                if (raw.isEmpty()) {
                    throw new R2dbcNonTransientResourceException("SHOW database_mode returned empty value");
                }
                KingbaseMode mode = KingbaseMode.fromDatabaseMode(raw);
                LOG.info("Detected Kingbase database_mode='{}' -> kingbaseMode={}", raw, mode);
                return new Detection(raw, mode);
            })
            .onErrorMap(e -> {
                if (e instanceof R2dbcNonTransientResourceException
                    && e.getMessage() != null
                    && e.getMessage().startsWith("Failed to detect Kingbase")) {
                    return e;
                }
                return new R2dbcNonTransientResourceException(
                    "Failed to detect Kingbase mode via SHOW database_mode (got no usable result). "
                        + "The Kingbase server must expose a recognized database_mode value. "
                        + "Cause: " + e.getMessage(),
                    e);
            });
    }

}
