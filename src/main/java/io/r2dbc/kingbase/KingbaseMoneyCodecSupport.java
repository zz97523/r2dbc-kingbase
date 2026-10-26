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

/** Shared text conversion for Kingbase {@code money} values. */
public final class KingbaseMoneyCodecSupport {

    private KingbaseMoneyCodecSupport() {
    }

    public static BigDecimal parse(String text) {
        String value = text.trim();
        boolean parentheses = value.startsWith("(") && value.endsWith(")");
        String numeric = value.replaceAll("[^0-9,.+-]", "");
        int comma = numeric.lastIndexOf(',');
        int dot = numeric.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            if (comma > dot) {
                numeric = numeric.replace(".", "").replace(',', '.');
            } else {
                numeric = numeric.replace(",", "");
            }
        } else if (comma >= 0) {
            int digitsAfterComma = numeric.length() - comma - 1;
            numeric = digitsAfterComma == 3 ? numeric.replace(",", "") : numeric.replace(',', '.');
        }
        BigDecimal result = new BigDecimal(numeric);
        return parentheses ? result.abs().negate() : result;
    }

    public static String format(Number value) {
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }
        return value.toString();
    }

}
