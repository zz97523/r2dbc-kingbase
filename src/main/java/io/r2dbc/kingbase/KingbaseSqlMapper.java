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

import java.util.Collections;
import java.util.Map;

/**
 * Client-side SQL mapping applied before the PostgreSQL wire {@code Parse} message.
 */
@FunctionalInterface
public interface KingbaseSqlMapper {

    /**
     * No-op mapper.
     */
    KingbaseSqlMapper IDENTITY = sql -> sql;

    /**
     * Map application SQL to wire SQL for the active Kingbase mode.
     *
     * @param sql original SQL
     * @return SQL to send on the PostgreSQL wire protocol
     */
    String map(String sql);

    /**
     * 0-based bind indexes for {@code @name} placeholders in original SQL.
     * Default: no named parameters (PG {@code $n} SQL uses {@code bind("$n", …)} only).
     *
     * @param sql original SQL before {@link #map(String)}
     * @return immutable map from parameter name (without {@code @}) to 0-based bind index
     */
    default Map<String, Integer> namedParameterIndexes(String sql) {
        return Collections.emptyMap();
    }

}
