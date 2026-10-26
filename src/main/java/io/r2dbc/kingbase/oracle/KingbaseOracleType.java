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

package io.r2dbc.kingbase.oracle;

import io.r2dbc.postgresql.codec.PostgresqlObjectId;

/**
 * Logical Oracle-style types for Kingbase Oracle mode on the <strong>PostgreSQL wire</strong>.
 * <p>
 * Default encode OIDs seed from stock {@link PostgresqlObjectId}; at connect time
 * {@link KingbaseOracleTypeOids} replaces them with OIDs from {@code pg_type} when Kingbase
 * exposes Oracle-named types under non-standard OIDs.
 * <p>
 * Java mappings follow oracle-r2dbc / JDBC habits (e.g. {@code NUMBER}→{@code BigDecimal},
 * Oracle {@code DATE}→{@code LocalDateTime} because Oracle DATE includes time).
 */
enum KingbaseOracleType {

    NUMBER(PostgresqlObjectId.NUMERIC),
    BINARY_FLOAT(PostgresqlObjectId.FLOAT4),
    BINARY_DOUBLE(PostgresqlObjectId.FLOAT8),
    INTEGER(PostgresqlObjectId.INT4),
    BIGINT(PostgresqlObjectId.INT8),
    SMALLINT(PostgresqlObjectId.INT2),
    BOOLEAN(PostgresqlObjectId.BOOL),
    CHAR(PostgresqlObjectId.BPCHAR),
    VARCHAR2(PostgresqlObjectId.VARCHAR),
    CLOB(PostgresqlObjectId.TEXT),
    RAW(PostgresqlObjectId.BYTEA),
    BLOB(PostgresqlObjectId.BYTEA),
    /**
     * Oracle DATE includes time-of-day; default Java mapping is {@link java.time.LocalDateTime}.
     */
    DATE(PostgresqlObjectId.TIMESTAMP),
    TIME(PostgresqlObjectId.TIME),
    TIMESTAMP(PostgresqlObjectId.TIMESTAMP),
    TIMESTAMPTZ(PostgresqlObjectId.TIMESTAMPTZ),
    INTERVAL_DS(PostgresqlObjectId.INTERVAL),
    INTERVAL_YM(PostgresqlObjectId.INTERVAL),
    /** Oracle {@code TIMESTAMP WITH LOCAL TIME ZONE} → {@link java.time.LocalDateTime}. */
    TIMESTAMP_LTZ(PostgresqlObjectId.TIMESTAMP),
    /** Oracle {@code LONG} character type (legacy). */
    LONG(PostgresqlObjectId.TEXT),
    /** Oracle {@code LONG RAW} (legacy binary). */
    LONG_RAW(PostgresqlObjectId.BYTEA),
    /** Oracle {@code ROWID} / {@code UROWID}. */
    ROWID(PostgresqlObjectId.VARCHAR),
    /** Oracle {@code JSON} (string payload on PG wire). */
    JSON(PostgresqlObjectId.JSON);

    private final PostgresqlObjectId oid;

    KingbaseOracleType(PostgresqlObjectId oid) {
        this.oid = oid;
    }

    int getObjectId() {
        return this.oid.getObjectId();
    }

    PostgresqlObjectId getOid() {
        return this.oid;
    }

}
