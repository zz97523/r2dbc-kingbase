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
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import io.r2dbc.postgresql.util.Assert;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryProvider;
import io.r2dbc.spi.Option;

import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;

/**
 * {@link ConnectionFactoryProvider} for R2DBC-Kingbase ({@code driver=kingbase}).
 * <p>
 * URL example: {@code r2dbc:kingbase://host:54321/db?mode=auto}
 * <p>
 * Modes ({@link #MODE}): {@code auto} (default), {@code postgresql}, {@code mysql}, {@code oracle},
 * {@code sqlserver}. The wire protocol is PostgreSQL; mode only selects the Kingbase mapping layer.
 */
public final class KingbaseConnectionFactoryProvider implements ConnectionFactoryProvider {

    /**
     * Driver identifier.
     */
    public static final String KINGBASE_DRIVER = "kingbase";

    /**
     * Mapping mode option ({@code auto|postgresql|mysql|oracle|sqlserver}). Defaults to {@link KingbaseMode#AUTO}.
     */
    public static final Option<KingbaseMode> MODE = Option.valueOf("mode");

    /**
     * Alias for {@link #MODE} (same meaning as postgresql provider's {@code kingbaseMode}).
     */
    public static final Option<KingbaseMode> KINGBASE_MODE = Option.valueOf("kingbaseMode");

    @Override
    public KingbaseConnectionFactory create(ConnectionFactoryOptions connectionFactoryOptions) {
        Assert.requireNonNull(connectionFactoryOptions, "connectionFactoryOptions must not be null");

        PostgresqlConnectionConfiguration.Builder builder =
            PostgresqlConnectionFactoryProvider.builder(connectionFactoryOptions);

        KingbaseMode mode = resolveMode(connectionFactoryOptions);
        builder.kingbaseMode(mode);

        return new KingbaseConnectionFactory(KingbaseConnectionConfiguration.from(builder.build()));
    }

    @Override
    public String getDriver() {
        return KINGBASE_DRIVER;
    }

    @Override
    public boolean supports(ConnectionFactoryOptions connectionFactoryOptions) {
        Assert.requireNonNull(connectionFactoryOptions, "connectionFactoryOptions must not be null");
        Object driver = connectionFactoryOptions.getValue(DRIVER);
        return driver != null && KINGBASE_DRIVER.equals(driver.toString());
    }

    private static KingbaseMode resolveMode(ConnectionFactoryOptions options) {
        Object mode = options.getValue(MODE);
        if (mode == null) {
            mode = options.getValue(KINGBASE_MODE);
        }
        if (mode == null) {
            return KingbaseMode.AUTO;
        }
        if (mode instanceof KingbaseMode) {
            return (KingbaseMode) mode;
        }
        return KingbaseMode.fromValue(mode.toString());
    }

}
