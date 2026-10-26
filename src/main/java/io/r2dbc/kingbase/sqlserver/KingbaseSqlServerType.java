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

package io.r2dbc.kingbase.sqlserver;

import io.r2dbc.postgresql.codec.PostgresqlObjectId;

/**
 * Logical SQL Server-style types for Kingbase SQL Server mode on the <strong>PostgreSQL wire</strong>.
 * <p>
 * Default encode OIDs seed from stock {@link PostgresqlObjectId}; at connect time
 * {@link KingbaseSqlServerTypeOids} replaces them with OIDs from {@code pg_type} when Kingbase
 * exposes SQL Server-named types under non-standard OIDs.
 * <p>
 * Java mappings follow r2dbc-mssql / JDBC habits (e.g. {@code BIT}→boolean 0/1,
 * {@code UNIQUEIDENTIFIER}→{@link java.util.UUID}, {@code FLOAT}→{@code Double}).
 */
enum KingbaseSqlServerType {

    TINYINT(PostgresqlObjectId.INT2),
    SMALLINT(PostgresqlObjectId.INT2),
    INT(PostgresqlObjectId.INT4),
    BIGINT(PostgresqlObjectId.INT8),
    /** SQL Server {@code real} → 4-byte float. */
    REAL(PostgresqlObjectId.FLOAT4),
    /** SQL Server {@code float} → 8-byte double. */
    FLOAT(PostgresqlObjectId.FLOAT8),
    DECIMAL(PostgresqlObjectId.NUMERIC),
    MONEY(PostgresqlObjectId.NUMERIC),
    /**
     * SQL Server {@code bit}; r2dbc-mssql encodes boolean as TINYINT 0/1 → INT2 on the PG wire.
     */
    BIT(PostgresqlObjectId.INT2),
    BOOLEAN(PostgresqlObjectId.INT2),
    DATE(PostgresqlObjectId.DATE),
    TIME(PostgresqlObjectId.TIME),
    DATETIME(PostgresqlObjectId.TIMESTAMP),
    DATETIME2(PostgresqlObjectId.TIMESTAMP),
    SMALLDATETIME(PostgresqlObjectId.TIMESTAMP),
    DATETIMEOFFSET(PostgresqlObjectId.TIMESTAMPTZ),
    CHAR(PostgresqlObjectId.BPCHAR),
    VARCHAR(PostgresqlObjectId.VARCHAR),
    NCHAR(PostgresqlObjectId.BPCHAR),
    NVARCHAR(PostgresqlObjectId.VARCHAR),
    TEXT(PostgresqlObjectId.TEXT),
    NTEXT(PostgresqlObjectId.TEXT),
    BINARY(PostgresqlObjectId.BYTEA),
    VARBINARY(PostgresqlObjectId.BYTEA),
    IMAGE(PostgresqlObjectId.BYTEA),
    /** SQL Server {@code uniqueidentifier}. */
    UNIQUEIDENTIFIER(PostgresqlObjectId.UUID),
    /** SQL Server {@code timestamp}/{@code rowversion} (binary). */
    ROWVERSION(PostgresqlObjectId.BYTEA),
    /** SQL Server {@code xml} represented as XML text on the PostgreSQL wire. */
    XML(PostgresqlObjectId.XML);

    private final PostgresqlObjectId oid;

    KingbaseSqlServerType(PostgresqlObjectId oid) {
        this.oid = oid;
    }

    int getObjectId() {
        return this.oid.getObjectId();
    }

    PostgresqlObjectId getOid() {
        return this.oid;
    }

}
