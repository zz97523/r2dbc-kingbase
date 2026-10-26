/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package io.r2dbc.kingbase;

import java.math.BigDecimal;
import java.util.Objects;

/** Value used to bind a number explicitly as the Kingbase {@code money} type. */
public final class KingbaseMoney extends Number {

    private final BigDecimal value;

    public KingbaseMoney(String value) {
        this(new BigDecimal(value));
    }

    public KingbaseMoney(BigDecimal value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    public BigDecimal asBigDecimal() {
        return this.value;
    }

    @Override
    public int intValue() {
        return this.value.intValue();
    }

    @Override
    public long longValue() {
        return this.value.longValue();
    }

    @Override
    public float floatValue() {
        return this.value.floatValue();
    }

    @Override
    public double doubleValue() {
        return this.value.doubleValue();
    }

    @Override
    public String toString() {
        return this.value.toPlainString();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof KingbaseMoney
            && this.value.compareTo(((KingbaseMoney) other).value) == 0;
    }

    @Override
    public int hashCode() {
        return this.value.stripTrailingZeros().hashCode();
    }

}
