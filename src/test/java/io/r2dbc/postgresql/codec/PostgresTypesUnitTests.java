/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.r2dbc.postgresql.codec;

import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlStatement;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class PostgresTypesUnitTests {

    @Test
    void shouldLimitTypeLookupToVisibleSchemasAndPgCatalog() {
        PostgresqlConnection connection = mock(PostgresqlConnection.class);
        PostgresqlStatement statement = mock(PostgresqlStatement.class);
        when(connection.createStatement(argThat(sql -> sql.contains("'datetime'")
            && sql.contains("sp.r IS NOT NULL")
            && sql.contains("nspname = 'pg_catalog'")))).thenReturn(statement);
        when(statement.execute()).thenReturn(Flux.empty());

        StepVerifier.create(PostgresTypes.from(connection)
                .lookupTypes(Arrays.asList("datetime")))
            .verifyComplete();

        verify(statement).execute();
    }

}
