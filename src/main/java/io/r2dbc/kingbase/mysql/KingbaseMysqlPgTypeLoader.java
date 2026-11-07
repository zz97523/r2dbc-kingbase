/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.r2dbc.kingbase.mysql;

import io.r2dbc.postgresql.api.PostgresqlConnection;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.Locale;
import java.util.StringJoiner;

/** Loads schema-aware MySQL compatibility type OIDs from {@code pg_catalog.pg_type}. */
final class KingbaseMysqlPgTypeLoader {

    private KingbaseMysqlPgTypeLoader() {
    }

    static Mono<Void> loadInto(KingbaseMysqlTypeOids oids, PostgresqlConnection connection,
                               Iterable<String> typenames) {
        return queryTypes(connection, typenames)
            .collectList()
            .doOnNext(rows -> {
                // Process lower-priority schemas first so the first active schema wins last.
                rows.sort(Comparator
                    .comparing(PgTypeRow::schemaRank, Comparator.nullsFirst(Comparator.reverseOrder()))
                    .thenComparing(PgTypeRow::typname)
                    .thenComparing(PgTypeRow::oid, Comparator.reverseOrder()));
                rows.forEach(row -> oids.accept(row.typname(), row.oid(), row.namespace(), row.arrayOid()));
            })
            .then();
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
            .flatMap(result -> result.map((row, metadata) -> new PgTypeRow(
                row.get("typname", String.class),
                Math.toIntExact(row.get("oid", Long.class)),
                row.get("nspname", String.class),
                Math.toIntExact(row.get("typarray", Long.class)),
                row.get("schema_rank", Integer.class)
            )));
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
