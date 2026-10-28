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

package io.r2dbc.postgresql.codec;

import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlStatement;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class PostgresTypesUnitTests {

    @Test
    void shouldAllowSafeMultiWordTypeNames() {
        PostgresqlConnection connection = mock(PostgresqlConnection.class);
        PostgresqlStatement statement = mock(PostgresqlStatement.class);
        when(connection.createStatement(argThat(sql -> sql.contains("'int unsigned'")
            && sql.contains("'bigint unsigned'")
            && sql.contains("sp.r IS NOT NULL")
            && sql.contains("nspname = 'pg_catalog'")))).thenReturn(statement);
        when(statement.execute()).thenReturn(Flux.empty());

        StepVerifier.create(PostgresTypes.from(connection)
                .lookupTypes(Arrays.asList("int unsigned", "bigint unsigned")))
            .verifyComplete();

        verify(statement).execute();
    }

    @Test
    void shouldRejectUnsafeMultiWordTypeNames() {
        PostgresqlConnection connection = mock(PostgresqlConnection.class);

        assertThatIllegalArgumentException().isThrownBy(() -> PostgresTypes.from(connection)
                .lookupTypes(Arrays.asList("int unsigned", "int' OR 1=1 --")))
            .withMessageContaining("Invalid typename");
    }
}
