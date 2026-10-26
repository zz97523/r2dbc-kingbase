/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */

package io.r2dbc.kingbase.oracle;

import io.r2dbc.postgresql.util.Assert;

import java.util.Locale;
import java.util.Objects;

/** Typed text value corresponding to the KES JDBC driver's {@code KBobject}. */
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
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof KingbaseOracleObject)) {
            return false;
        }
        KingbaseOracleObject that = (KingbaseOracleObject) other;
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
