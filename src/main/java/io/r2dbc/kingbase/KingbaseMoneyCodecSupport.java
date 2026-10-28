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

package io.r2dbc.kingbase;

import java.math.BigDecimal;

/**
 * Shared text conversion for Kingbase {@code money} values.
 */
public final class KingbaseMoneyCodecSupport {

    private KingbaseMoneyCodecSupport() {
    }

    /**
     * Parse PostgreSQL/Kingbase money text while retaining decimal precision.
     * Currency symbols and grouping separators are accepted.
     */
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

    /**
     * Format a supported R2DBC money value for the PostgreSQL text wire format.
     */
    public static String format(Number value) {
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }
        return value.toString();
    }

}
