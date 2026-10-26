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

/**
 * KingbaseES client compatibility mode (mapping layer).
 * <p>
 * R2DBC-Kingbase always speaks the PostgreSQL wire protocol. {@code KingbaseMode} selects which
 * SQL/type mapping layer sits above that protocol:
 * <ul>
 *   <li>{@link #POSTGRESQL} — PG-style {@code $n}, stock codecs</li>
 *   <li>{@link #MYSQL} — JDBC {@code ?} rewrite + MySQL-oriented codecs</li>
 *   <li>{@link #ORACLE} — JDBC/Oracle {@code ?}/{@code :name} rewrite + Oracle-oriented codecs</li>
 *   <li>{@link #SQLSERVER} — T-SQL {@code @name}/{@code ?} rewrite + SQL Server-oriented codecs</li>
 *   <li>{@link #AUTO} — {@code SHOW database_mode} at connect time</li>
 * </ul>
 */
public enum KingbaseMode {

    /**
     * Detect via {@code SHOW database_mode}. Failure aborts connect (no silent fallback).
     */
    AUTO,

    /**
     * PostgreSQL-compatible mapping.
     */
    POSTGRESQL,

    /**
     * MySQL-compatible mapping.
     */
    MYSQL,

    /**
     * Oracle-compatible mapping.
     */
    ORACLE,

    /**
     * Microsoft SQL Server-compatible mapping.
     */
    SQLSERVER;

    /**
     * Parse configuration string
     * ({@code auto|postgresql|mysql|oracle|sqlserver}, also {@code pg}/{@code postgres}/{@code mssql}).
     *
     * @param value mode name
     * @return mode
     */
    public static KingbaseMode fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("mode must not be null");
        }

        String normalized = value.trim().toLowerCase();
        switch (normalized) {
            case "auto":
                return AUTO;
            case "postgresql":
            case "postgres":
            case "pg":
                return POSTGRESQL;
            case "mysql":
                return MYSQL;
            case "oracle":
                return ORACLE;
            case "sqlserver":
            case "mssql":
            case "sql_server":
                return SQLSERVER;
            default:
                throw new IllegalArgumentException("Unknown Kingbase mode: " + value
                    + " (expected auto|postgresql|mysql|oracle|sqlserver)");
        }
    }

    /**
     * Map {@code SHOW database_mode} text to a client mapping mode.
     *
     * @param databaseMode raw server value
     * @return mode
     */
    public static KingbaseMode fromDatabaseMode(String databaseMode) {
        if (databaseMode == null || databaseMode.trim().isEmpty()) {
            throw new IllegalArgumentException("database_mode must not be null/empty");
        }

        String normalized = databaseMode.trim().toLowerCase();
        switch (normalized) {
            case "mysql":
            case "2":
                return MYSQL;
            case "oracle":
            case "1":
                return ORACLE;
            case "pg":
            case "postgresql":
            case "postgres":
            case "0":
                return POSTGRESQL;
            case "sqlserver":
            case "mssql":
            case "3":
                return SQLSERVER;
            default:
                throw new IllegalArgumentException(
                    "Unrecognized Kingbase database_mode: '" + databaseMode
                        + "' (expected mysql|pg|oracle|sqlserver or 0|1|2|3)");
        }
    }

    /**
     * @return {@code true} if {@link #AUTO}
     */
    public boolean isAutoDetect() {
        return this == AUTO;
    }

}
