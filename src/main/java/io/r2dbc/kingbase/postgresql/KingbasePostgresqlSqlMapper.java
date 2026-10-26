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

package io.r2dbc.kingbase.postgresql;

import io.r2dbc.kingbase.KingbaseSqlMapper;

/**
 * PostgreSQL-mode SQL mapper for KingbaseES.
 * <p>
 * Application SQL is already PostgreSQL-oriented; the wire protocol lives in
 * {@code io.r2dbc.postgresql}, so this mapper is identity (no rewrite).
 */
public final class KingbasePostgresqlSqlMapper implements KingbaseSqlMapper {

    public static final KingbasePostgresqlSqlMapper INSTANCE = new KingbasePostgresqlSqlMapper();

    private KingbasePostgresqlSqlMapper() {
    }

    @Override
    public String map(String sql) {
        return sql;
    }

}
