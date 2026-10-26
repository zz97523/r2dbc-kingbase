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

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.util.Assert;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Configuration for {@link KingbaseConnectionFactory}.
 * <p>
 * Builds on the PostgreSQL wire-protocol client; {@link #getMode() mode} selects the Kingbase
 * mapping layer (PostgreSQL / MySQL / Oracle / SQL Server).
 */
public final class KingbaseConnectionConfiguration {

    private final PostgresqlConnectionConfiguration delegate;

    private KingbaseConnectionConfiguration(PostgresqlConnectionConfiguration delegate) {
        this.delegate = Assert.requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * Wrap an already-built PostgreSQL-protocol configuration.
     *
     * @param configuration postgresql configuration (includes {@code kingbaseMode})
     * @return kingbase configuration
     */
    public static KingbaseConnectionConfiguration from(PostgresqlConnectionConfiguration configuration) {
        return new KingbaseConnectionConfiguration(configuration);
    }

    /**
     * @return a new builder (default {@link KingbaseMode#AUTO})
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Effective Kingbase mapping mode configuration.
     *
     * @return mode
     */
    public KingbaseMode getMode() {
        return this.delegate.getKingbaseMode();
    }

    /**
     * Underlying PostgreSQL-protocol configuration used by the wire client.
     *
     * @return postgresql configuration
     */
    PostgresqlConnectionConfiguration asPostgresqlConfiguration() {
        return this.delegate;
    }

    /**
     * Builder for {@link KingbaseConnectionConfiguration}.
     */
    public static final class Builder {

        private final PostgresqlConnectionConfiguration.Builder delegate = PostgresqlConnectionConfiguration.builder();

        private KingbaseMode mode = KingbaseMode.AUTO;

        private Builder() {
        }

        /**
         * Kingbase mapping mode. Defaults to {@link KingbaseMode#AUTO}.
         *
         * @param mode mode
         * @return this builder
         */
        public Builder mode(KingbaseMode mode) {
            this.mode = Assert.requireNonNull(mode, "mode must not be null");
            return this;
        }

        public Builder host(String host) {
            this.delegate.host(host);
            return this;
        }

        public Builder port(int port) {
            this.delegate.port(port);
            return this;
        }

        public Builder username(String username) {
            this.delegate.username(username);
            return this;
        }

        public Builder password(@Nullable CharSequence password) {
            this.delegate.password(password);
            return this;
        }

        public Builder database(@Nullable String database) {
            this.delegate.database(database);
            return this;
        }

        public Builder connectTimeout(@Nullable Duration connectTimeout) {
            this.delegate.connectTimeout(connectTimeout);
            return this;
        }

        public Builder applicationName(String applicationName) {
            this.delegate.applicationName(applicationName);
            return this;
        }

        /**
         * @return immutable configuration
         */
        public KingbaseConnectionConfiguration build() {
            this.delegate.kingbaseMode(this.mode);
            return new KingbaseConnectionConfiguration(this.delegate.build());
        }
    }

}
