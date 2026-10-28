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

import io.r2dbc.postgresql.util.Assert;

import java.util.Locale;
import java.util.Objects;

/**
 * A typed text value for Oracle-mode database types that do not have a standard R2DBC Java type.
 * <p>
 * This is the R2DBC equivalent of the KES JDBC driver's {@code KBobject}: the database type name
 * is retained so that values such as {@code MACADDR}, ranges, {@code TSVECTOR}, {@code JSONPATH}
 * and {@code PG_LSN} can be bound with their native OID instead of being sent as plain text.
 */
public class KingbaseOracleObject {

    private final String typeName;

    private final String value;

    public KingbaseOracleObject(String typeName, String value) {
        Assert.requireNonNull(typeName, "typeName must not be null");
        Assert.requireNonNull(value, "value must not be null");
        String normalized = typeName.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("typeName must not be empty");
        }
        this.typeName = normalized;
        this.value = value;
    }

    public String getTypeName() {
        return this.typeName;
    }

    public String getValue() {
        return this.value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KingbaseOracleObject)) {
            return false;
        }
        KingbaseOracleObject that = (KingbaseOracleObject) o;
        return this.typeName.equals(that.typeName) && this.value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.typeName, this.value);
    }

    @Override
    public String toString() {
        return this.value;
    }

}
