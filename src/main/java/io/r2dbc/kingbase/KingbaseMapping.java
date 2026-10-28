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

import io.r2dbc.kingbase.mysql.KingbaseMysqlSqlMapper;
import io.r2dbc.kingbase.oracle.KingbaseOracleSqlMapper;
import io.r2dbc.kingbase.postgresql.KingbasePostgresqlSqlMapper;
import io.r2dbc.kingbase.sqlserver.KingbaseSqlServerSqlMapper;

/**
 * Factory for mode-specific {@link KingbaseSqlMapper} instances.
 */
public final class KingbaseMapping {

    private KingbaseMapping() {
    }

    /**
     * Resolve the SQL mapper for the given mode.
     * <p>
     * {@link KingbaseMode#AUTO} is <strong>not</strong> resolved here. Auto-detect happens in
     * {@code PostgresqlConnectionFactory} via {@link KingbaseDatabaseModeDetector} ({@code SHOW
     * database_mode}); afterward {@code applyKingbaseMode} calls this method with a concrete mode
     * ({@code MYSQL}/{@code ORACLE}/{@code SQLSERVER}/{@code POSTGRESQL}). Until detection finishes,
     * {@code AUTO} uses the PostgreSQL identity mapper only as a temporary bootstrap.
     *
     * @param mode kingbase mode, {@code null} treated as {@link KingbaseMode#POSTGRESQL}
     * @return mapper
     */
    public static KingbaseSqlMapper sqlMapper(KingbaseMode mode) {
        if (mode == null) {
            return KingbasePostgresqlSqlMapper.INSTANCE;
        }
        switch (mode) {
            case MYSQL:
                return KingbaseMysqlSqlMapper.INSTANCE;
            case ORACLE:
                return KingbaseOracleSqlMapper.INSTANCE;
            case SQLSERVER:
                return KingbaseSqlServerSqlMapper.INSTANCE;
            case POSTGRESQL:
            case AUTO: // temporary until SHOW database_mode resolves; not a silent fallback
            default:
                return KingbasePostgresqlSqlMapper.INSTANCE;
        }
    }

}
