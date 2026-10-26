/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */

package io.r2dbc.kingbase;

import java.util.Objects;

/** Value used to bind text explicitly as the Kingbase {@code xml} type. */
public final class KingbaseXml implements CharSequence {

    private final String value;

    public KingbaseXml(String value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    public String getValue() {
        return this.value;
    }

    @Override
    public int length() {
        return this.value.length();
    }

    @Override
    public char charAt(int index) {
        return this.value.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return this.value.subSequence(start, end);
    }

    @Override
    public String toString() {
        return this.value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof KingbaseXml
            && this.value.equals(((KingbaseXml) other).value);
    }

    @Override
    public int hashCode() {
        return this.value.hashCode();
    }

}
