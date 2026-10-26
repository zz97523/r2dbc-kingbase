/*
 * Copyright 2020 the original author or authors.
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

package io.r2dbc.postgresql;

import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.client.Client;
import io.r2dbc.postgresql.client.PortalNameSupplier;
import io.r2dbc.postgresql.codec.Codecs;
import io.r2dbc.kingbase.KingbaseMapping;
import io.r2dbc.kingbase.KingbaseMode;
import io.r2dbc.kingbase.KingbaseSqlMapper;
import io.r2dbc.postgresql.util.Assert;
import org.jspecify.annotations.Nullable;

/**
 * Value object capturing contextual connection resources such as {@link Client}, {@link Codecs} and the {@link PostgresqlConnection connection facade}.
 */
final class ConnectionResources {

    private final Client client;

    private final Codecs codecs;

    private final PostgresqlConnection connection;

    private final PostgresqlConnectionConfiguration configuration;

    private final StatementCache statementCache;

    private final PortalNameSupplier portalNameSupplier;

    private volatile KingbaseMode kingbaseMode;

    private volatile KingbaseSqlMapper sqlMapper;

    private volatile String detectedDatabaseMode;

    ConnectionResources(Client client, Codecs codecs, PostgresqlConnection connection, PostgresqlConnectionConfiguration configuration, PortalNameSupplier portalNameSupplier,
                        StatementCache statementCache) {
        this.client = client;
        this.codecs = codecs;
        this.connection = connection;
        this.configuration = configuration;
        this.portalNameSupplier = Assert.requireNonNull(portalNameSupplier, "portalNameSupplier must not be null");
        this.statementCache = Assert.requireNonNull(statementCache, "statementCache must not be null");

        KingbaseMode configured = configuration.getKingbaseMode();
        // AUTO starts as POSTGRESQL until SHOW database_mode resolves the real mode
        KingbaseMode initial = configured.isAutoDetect() ? KingbaseMode.POSTGRESQL : configured;
        this.kingbaseMode = initial;
        this.sqlMapper = KingbaseMapping.sqlMapper(initial);
        this.detectedDatabaseMode = null;
    }

    public Client getClient() {
        return this.client;
    }

    public Codecs getCodecs() {
        return this.codecs;
    }

    public PostgresqlConnection getConnection() {
        return this.connection;
    }

    public PostgresqlConnectionConfiguration getConfiguration() {
        return this.configuration;
    }

    public PortalNameSupplier getPortalNameSupplier() {
        return this.portalNameSupplier;
    }

    public StatementCache getStatementCache() {
        return this.statementCache;
    }

    /**
     * Effective Kingbase mapping mode for this connection (after auto-detect when configured).
     *
     * @return effective mode
     */
    KingbaseMode getKingbaseMode() {
        return this.kingbaseMode;
    }

    /**
     * SQL mapper for the effective {@link #getKingbaseMode() kingbase mode}.
     *
     * @return sql mapper
     */
    KingbaseSqlMapper getSqlMapper() {
        return this.sqlMapper;
    }

    /**
     * Raw {@code SHOW database_mode} value when auto-detect succeeded; otherwise {@code null}.
     *
     * @return detected database_mode text, or {@code null}
     */
    @Nullable
    String getDetectedDatabaseMode() {
        return this.detectedDatabaseMode;
    }

    /**
     * Apply a resolved Kingbase mapping mode (typically after {@code SHOW database_mode}).
     *
     * @param mode         resolved mode
     * @param databaseMode raw {@code database_mode} from server, may be {@code null} when forced
     */
    void applyKingbaseMode(KingbaseMode mode, @Nullable String databaseMode) {
        KingbaseMode resolved = Assert.requireNonNull(mode, "mode must not be null");
        if (resolved == KingbaseMode.AUTO) {
            resolved = KingbaseMode.POSTGRESQL;
        }
        this.kingbaseMode = resolved;
        this.sqlMapper = KingbaseMapping.sqlMapper(resolved);
        this.detectedDatabaseMode = databaseMode;
    }

    /**
     * Apply a resolved Kingbase mapping mode without a raw {@code database_mode} string.
     *
     * @param mode resolved mode
     */
    void applyKingbaseMode(KingbaseMode mode) {
        applyKingbaseMode(mode, null);
    }

    @Override
    public String toString() {
        return "ConnectionContext{" +
            "client=" + this.client +
            ", codecs=" + this.codecs +
            ", connection=" + this.connection +
            ", configuration=" + this.configuration +
            ", kingbaseMode=" + this.kingbaseMode +
            ", detectedDatabaseMode=" + this.detectedDatabaseMode +
            ", portalNameSupplier=" + this.portalNameSupplier +
            ", statementCache=" + this.statementCache +
            '}';
    }

}
