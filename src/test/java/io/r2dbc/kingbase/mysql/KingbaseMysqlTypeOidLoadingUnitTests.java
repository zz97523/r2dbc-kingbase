/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.r2dbc.kingbase.mysql;

import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.r2dbc.postgresql.api.PostgresqlStatement;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class KingbaseMysqlTypeOidLoadingUnitTests {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldKeepVisibleDatetimeInsteadOfLaterPgCatalogDuplicate() {
        PostgresqlConnection connection = mock(PostgresqlConnection.class);
        PostgresqlStatement statement = mock(PostgresqlStatement.class);
        PostgresqlResult result = mock(PostgresqlResult.class);
        RowMetadata metadata = mock(RowMetadata.class);
        Row visibleDatetime = row(9_801L, "datetime", "D");
        Row catalogDatetime = row(9_802L, "datetime", "D");

        when(connection.createStatement(argThat(sql -> sql.contains("sp.r IS NOT NULL")
            && sql.contains("'datetime'")))).thenReturn(statement);
        when(statement.execute()).thenReturn(Flux.just(result));
        when(result.map(any(BiFunction.class))).thenAnswer(invocation -> {
            BiFunction<Row, RowMetadata, Object> mapper = invocation.getArgument(0);
            return Flux.just(mapper.apply(visibleDatetime, metadata),
                mapper.apply(catalogDatetime, metadata));
        });

        KingbaseMysqlTypeOids typeOids = KingbaseMysqlTypeOids.load(connection).block();

        assertThat(typeOids).isNotNull();
        assertThat(typeOids.encodeTimestamp()).isEqualTo(9_801);
    }

    private static Row row(long oid, String name, String category) {
        Row row = mock(Row.class);
        when(row.get("oid", Long.class)).thenReturn(oid);
        when(row.get("typarray", Long.class)).thenReturn(0L);
        when(row.get("typname", String.class)).thenReturn(name);
        when(row.get("typcategory", String.class)).thenReturn(category);
        return row;
    }

}
