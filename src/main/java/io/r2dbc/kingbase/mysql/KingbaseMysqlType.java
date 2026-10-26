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

package io.r2dbc.kingbase.mysql;

import io.r2dbc.postgresql.codec.PostgresqlObjectId;

/**
 * Logical MySQL-style Java types for Kingbase MySQL mode on the <strong>PostgreSQL wire</strong>.
 * <p>
 * Default encode OIDs seed from stock {@link PostgresqlObjectId}; at connect time
 * {@link KingbaseMysqlTypeOids} replaces them with OIDs from {@code pg_type} when Kingbase
 * exposes MySQL-named types under non-standard OIDs (e.g. {@code date=7944}).
 */
enum KingbaseMysqlType {

    TINYINT(PostgresqlObjectId.INT2),
    SMALLINT(PostgresqlObjectId.INT2),
    INT(PostgresqlObjectId.INT4),
    BIGINT(PostgresqlObjectId.INT8),
    FLOAT(PostgresqlObjectId.FLOAT4),
    DOUBLE(PostgresqlObjectId.FLOAT8),
    DECIMAL(PostgresqlObjectId.NUMERIC),
    MONEY(PostgresqlObjectId.MONEY),
    BIT(PostgresqlObjectId.INT8), // MySQL BIT ↔ integer on Kingbase wire (not pg_catalog.bit)
    YEAR(PostgresqlObjectId.INT2),
    DATE(PostgresqlObjectId.DATE),
    TIME(PostgresqlObjectId.TIME),
    DATETIME(PostgresqlObjectId.TIMESTAMP),
    TIMESTAMP(PostgresqlObjectId.TIMESTAMPTZ),
    VARCHAR(PostgresqlObjectId.VARCHAR),
    TEXT(PostgresqlObjectId.TEXT),
    BLOB(PostgresqlObjectId.BYTEA),
    ROWID(PostgresqlObjectId.VARCHAR),
    /** r2dbc-mysql encodes boolean as TINYINT → INT2 on the PG wire. */
    BOOLEAN(PostgresqlObjectId.INT2);

    private final PostgresqlObjectId oid;

    KingbaseMysqlType(PostgresqlObjectId oid) {
        this.oid = oid;
    }

    int getObjectId() {
        return this.oid.getObjectId();
    }

    PostgresqlObjectId getOid() {
        return this.oid;
    }

}
