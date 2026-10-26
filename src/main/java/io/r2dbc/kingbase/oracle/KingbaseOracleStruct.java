/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */

package io.r2dbc.kingbase.oracle;

import java.util.ArrayList;
import java.util.List;

/** Oracle-mode structured value corresponding to KES JDBC {@code Struct}/{@code KbStruct}. */
public final class KingbaseOracleStruct extends KingbaseOracleObject {

    private final Object[] attributes;

    public KingbaseOracleStruct(String typeName, Object... attributes) {
        super(typeName, encode(attributes));
        this.attributes = attributes == null ? new Object[0] : attributes.clone();
    }

    private KingbaseOracleStruct(String typeName, String value, Object[] attributes) {
        super(typeName, value);
        this.attributes = attributes;
    }

    static KingbaseOracleStruct decode(String typeName, String value) {
        return new KingbaseOracleStruct(typeName, value, parse(value));
    }

    public Object[] getAttributes() {
        return this.attributes.clone();
    }

    private static String encode(Object[] attributes) {
        if (attributes == null || attributes.length == 0) {
            return "()";
        }
        StringBuilder result = new StringBuilder("(");
        for (int i = 0; i < attributes.length; i++) {
            if (i > 0) {
                result.append(',');
            }
            Object attribute = attributes[i];
            if (attribute == null) {
                continue;
            }
            if (attribute instanceof Number || attribute instanceof Boolean) {
                result.append(attribute);
            } else {
                String text = attribute instanceof KingbaseOracleObject
                    ? ((KingbaseOracleObject) attribute).getValue() : attribute.toString();
                result.append('"');
                for (int j = 0; j < text.length(); j++) {
                    char c = text.charAt(j);
                    if (c == '"' || c == '\\') {
                        result.append('\\');
                    }
                    result.append(c);
                }
                result.append('"');
            }
        }
        return result.append(')').toString();
    }

    private static Object[] parse(String value) {
        if (value == null || value.length() < 2 || value.charAt(0) != '('
            || value.charAt(value.length() - 1) != ')') {
            throw new IllegalArgumentException("Invalid Oracle structured value: " + value);
        }
        if (value.length() == 2) {
            return new Object[0];
        }

        List<Object> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        int nested = 0;
        for (int i = 1; i < value.length() - 1; i++) {
            char c = value.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && c == '(') {
                nested++;
                current.append(c);
            } else if (!quoted && c == ')') {
                nested--;
                current.append(c);
            } else if (!quoted && nested == 0 && c == ',') {
                values.add(current.length() == 0 ? null : current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.length() == 0 ? null : current.toString());
        return values.toArray(new Object[0]);
    }

    @Override
    public String toString() {
        return getValue();
    }

}
