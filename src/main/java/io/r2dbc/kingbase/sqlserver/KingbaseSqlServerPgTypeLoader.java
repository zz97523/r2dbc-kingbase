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

import io.r2dbc.postgresql.api.PostgresqlConnection;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Loads Kingbase SQL Server-mode type OIDs from {@code pg_catalog.pg_type}.
 * <p>
 * Unlike {@link io.r2dbc.postgresql.codec.PostgresTypes#lookupTypes}, this loader reads
 * {@code typnamespace} and applies non-{@code pg_catalog} rows before {@code pg_catalog} so
 * T-SQL types ({@code nvarchar}, Kingbase {@code date}, {@code varbinary}, …) are chosen for
 * encode defaults instead of stock PostgreSQL {@code pg_catalog.varchar/date/bytea}.
 */
final class KingbaseSqlServerPgTypeLoader {

    private KingbaseSqlServerPgTypeLoader() {
    }

    static Mono<Void> loadInto(KingbaseSqlServerTypeOids oids, PostgresqlConnection connection,
                               Iterable<String> typenames) {
        return queryTypes(connection, typenames)
            .collectList()
            .flatMap(rows -> {
                rows.sort(Comparator
                    .comparing(PgTypeRow::schemaRank, Comparator.nullsFirst(Comparator.reverseOrder()))
                    .thenComparing(PgTypeRow::typname)
                    .thenComparing(PgTypeRow::oid, Comparator.reverseOrder()));
                rows.forEach(row -> oids.accept(row.typname(), row.oid(), row.namespace(), row.arrayOid()));
                return resolveEncodeFallbacks(oids, connection);
            });
    }

    private static Flux<PgTypeRow> queryTypes(PostgresqlConnection connection, Iterable<String> typenames) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String typname : typenames) {
            joiner.add("'" + typname.toLowerCase(Locale.ROOT) + "'");
        }
        if (joiner.length() == 0) {
            return Flux.empty();
        }
        String sql = "SELECT t.oid, t.typarray, t.typname, n.nspname, "
            + "array_position(current_schemas(true), n.nspname) AS schema_rank "
            + "FROM pg_catalog.pg_type t "
            + "INNER JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace "
            + "WHERE lower(t.typname) IN (" + joiner + ") "
            + "AND t.typtype IN ('b', 'd') "
            + "AND n.nspname = ANY(current_schemas(true))";
        return connection.createStatement(sql)
            .execute()
            .flatMap(result -> result.map((row, meta) -> new PgTypeRow(
                row.get("typname", String.class),
                Math.toIntExact(row.get("oid", Long.class)),
                row.get("nspname", String.class),
                Math.toIntExact(row.get("typarray", Long.class)),
                row.get("schema_rank", Integer.class)
            )));
    }

    /**
     * If bulk load still left stock PG encode OIDs, resolve critical T-SQL types individually.
     */
    private static Mono<Void> resolveEncodeFallbacks(KingbaseSqlServerTypeOids oids, PostgresqlConnection connection) {
        List<Mono<Void>> steps = new ArrayList<>(6);
        if (oids.needsTextFallback()) {
            steps.add(preferTypename(connection, oids, "nvarchar", "nchar", "ntext"));
        }
        if (oids.needsDateFallback()) {
            steps.add(preferTypename(connection, oids, "date"));
        }
        if (oids.needsBinaryFallback()) {
            steps.add(preferTypename(connection, oids, "varbinary", "binary", "image"));
        }
        if (oids.needsBitFallback()) {
            steps.add(preferTypename(connection, oids, "bit"));
        }
        if (oids.needsDatetime2Fallback()) {
            steps.add(preferTypename(connection, oids, "datetime2"));
        }
        if (oids.needsDatetimeFallback()) {
            steps.add(preferTypename(connection, oids, "datetime", "smalldatetime"));
        }
        return Flux.concat(steps).then();
    }

    private static Mono<Void> preferTypename(PostgresqlConnection connection, KingbaseSqlServerTypeOids oids,
                                             String... typenames) {
        Mono<Void> chain = Mono.empty();
        for (String typname : typenames) {
            chain = chain.then(
                lookupPreferredType(connection, typname)
                    .doOnNext(row -> oids.accept(row.typname(), row.oid(), row.namespace(), row.arrayOid()))
                    .then()
            );
        }
        return chain;
    }

    private static Mono<PgTypeRow> lookupPreferredType(PostgresqlConnection connection, String typname) {
        String sql = "SELECT t.oid, t.typarray, t.typname, n.nspname "
            + "FROM pg_catalog.pg_type t "
            + "INNER JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace "
            + "WHERE lower(t.typname) = lower('" + typname.toLowerCase(Locale.ROOT) + "') "
            + "AND t.typtype IN ('b', 'd') "
            + "AND n.nspname = ANY(current_schemas(true)) "
            + "ORDER BY array_position(current_schemas(true), n.nspname) NULLS LAST, t.oid DESC "
            + "LIMIT 1";
        return connection.createStatement(sql)
            .execute()
            .flatMap(result -> result.map((row, meta) -> new PgTypeRow(
                row.get("typname", String.class),
                Math.toIntExact(row.get("oid", Long.class)),
                row.get("nspname", String.class),
                Math.toIntExact(row.get("typarray", Long.class)),
                null
            )))
            .next();
    }

    private static final class PgTypeRow {

        private final String typname;

        private final int oid;

        private final String namespace;

        private final int arrayOid;

        private final Integer schemaRank;

        private PgTypeRow(String typname, int oid, String namespace, int arrayOid, Integer schemaRank) {
            this.typname = typname;
            this.oid = oid;
            this.namespace = namespace;
            this.arrayOid = arrayOid;
            this.schemaRank = schemaRank;
        }

        String typname() {
            return this.typname;
        }

        int oid() {
            return this.oid;
        }

        String namespace() {
            return this.namespace;
        }

        int arrayOid() {
            return this.arrayOid;
        }

        Integer schemaRank() {
            return this.schemaRank;
        }
    }

}
