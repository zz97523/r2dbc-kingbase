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

import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.util.Assert;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import reactor.core.publisher.Mono;

/**
 * R2DBC {@link ConnectionFactory} for KingbaseES.
 * <p>
 * Public entry point for the Kingbase driver. Internally uses the PostgreSQL wire protocol with a
 * mode-specific mapping layer ({@link KingbaseMode#POSTGRESQL}, {@link KingbaseMode#MYSQL},
 * {@link KingbaseMode#ORACLE}, {@link KingbaseMode#SQLSERVER}).
 */
public final class KingbaseConnectionFactory implements ConnectionFactory {

    private final PostgresqlConnectionFactory delegate;

    private final KingbaseConnectionConfiguration configuration;

    /**
     * Create a factory from Kingbase configuration.
     *
     * @param configuration configuration
     */
    public KingbaseConnectionFactory(KingbaseConnectionConfiguration configuration) {
        this.configuration = Assert.requireNonNull(configuration, "configuration must not be null");
        this.delegate = new PostgresqlConnectionFactory(configuration.asPostgresqlConfiguration());
    }

    @Override
    public Mono<? extends Connection> create() {
        return this.delegate.create();
    }

    @Override
    public ConnectionFactoryMetadata getMetadata() {
        return KingbaseConnectionFactoryMetadata.INSTANCE;
    }

    /**
     * @return configuration
     */
    public KingbaseConnectionConfiguration getConfiguration() {
        return this.configuration;
    }

    @Override
    public String toString() {
        return "KingbaseConnectionFactory{mode=" + this.configuration.getMode() + '}';
    }

}
