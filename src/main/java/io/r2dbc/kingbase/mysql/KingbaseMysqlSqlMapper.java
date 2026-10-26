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

package io.r2dbc.kingbase.mysql;

import io.r2dbc.kingbase.KingbaseSqlMapper;
import io.r2dbc.postgresql.util.Assert;

/**
 * MySQL-mode SQL mapper for KingbaseES.
 * <p>
 * Rewrites JDBC-style {@code ?} placeholders to PostgreSQL {@code $1}, {@code $2}, … while
 * respecting string literals, quoted identifiers, comments, and dollar-quoted strings.
 * Backticks and MySQL SQL syntax are left for the server (Kingbase MySQL mode), matching
 * r2dbc-mysql which does not rewrite dialect or JDBC {@code {fn}} escapes.
 */
public final class KingbaseMysqlSqlMapper implements KingbaseSqlMapper {

    public static final KingbaseMysqlSqlMapper INSTANCE = new KingbaseMysqlSqlMapper();

    private KingbaseMysqlSqlMapper() {
    }

    @Override
    public String map(String sql) {
        Assert.requireNonNull(sql, "sql must not be null");

        if (sql.indexOf('?') < 0) {
            return sql;
        }

        if (containsDollarParameter(sql)) {
            throw new IllegalArgumentException(
                "Cannot mix '?' and '$n' placeholders in Kingbase MySQL mode SQL: " + sql);
        }

        StringBuilder out = new StringBuilder(sql.length() + 16);
        int param = 0;
        int i = 0;
        int length = sql.length();

        while (i < length) {
            char c = sql.charAt(i);

            // line comment --
            if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                if (end < 0) {
                    out.append(sql, i, length);
                    break;
                }
                out.append(sql, i, end + 1);
                i = end + 1;
                continue;
            }

            // block comment /* */
            if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) {
                    out.append(sql, i, length);
                    break;
                }
                out.append(sql, i, end + 2);
                i = end + 2;
                continue;
            }

            // single-quoted string
            if (c == '\'') {
                i = appendQuoted(sql, i, '\'', out);
                continue;
            }

            // double-quoted identifier
            if (c == '"') {
                i = appendQuoted(sql, i, '"', out);
                continue;
            }

            // backtick identifier (MySQL)
            if (c == '`') {
                i = appendQuoted(sql, i, '`', out);
                continue;
            }

            // dollar-quoted string $tag$...$tag$ or $$...$$
            if (c == '$') {
                int tagEnd = findDollarTagEnd(sql, i);
                if (tagEnd > i) {
                    String tag = sql.substring(i, tagEnd + 1); // includes trailing $
                    int contentStart = tagEnd + 1;
                    int close = sql.indexOf(tag, contentStart);
                    if (close >= 0) {
                        out.append(sql, i, close + tag.length());
                        i = close + tag.length();
                        continue;
                    }
                }
            }

            if (c == '?') {
                param++;
                out.append('$').append(param);
                i++;
                continue;
            }

            out.append(c);
            i++;
        }

        return out.toString();
    }

    private static boolean containsDollarParameter(String sql) {
        int length = sql.length();
        int i = 0;

        while (i < length) {
            char c = sql.charAt(i);

            if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                i = end < 0 ? length : end + 1;
                continue;
            }
            if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? length : end + 2;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                i = skipQuoted(sql, i, c);
                continue;
            }
            if (c == '$') {
                int tagEnd = findDollarTagEnd(sql, i);
                if (tagEnd > i) {
                    String tag = sql.substring(i, tagEnd + 1);
                    int close = sql.indexOf(tag, tagEnd + 1);
                    if (close >= 0) {
                        i = close + tag.length();
                        continue;
                    }
                }
                // $n parameter
                if (i + 1 < length && Character.isDigit(sql.charAt(i + 1))) {
                    return true;
                }
            }
            i++;
        }
        return false;
    }

    /**
     * Find end index of a dollar-quote tag starting at {@code start} ({@code $} position).
     * Returns the index of the closing {@code $} of the opening tag, or {@code -1}.
     */
    private static int findDollarTagEnd(String sql, int start) {
        if (sql.charAt(start) != '$') {
            return -1;
        }
        // $$ empty tag
        if (start + 1 < sql.length() && sql.charAt(start + 1) == '$') {
            return start + 1;
        }
        // $tag$ — tag is [A-Za-z_][A-Za-z0-9_]*
        if (start + 1 >= sql.length()) {
            return -1;
        }
        char first = sql.charAt(start + 1);
        if (!(Character.isLetter(first) || first == '_')) {
            return -1;
        }
        int i = start + 2;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '$') {
                return i;
            }
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                return -1;
            }
            i++;
        }
        return -1;
    }

    private static int appendQuoted(String sql, int start, char quote, StringBuilder out) {
        out.append(quote);
        int i = start + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            out.append(c);
            if (c == quote) {
                // escaped quote by doubling
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    out.append(quote);
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            // MySQL backslash escape inside strings
            if (quote == '\'' && c == '\\' && i + 1 < sql.length()) {
                out.append(sql.charAt(i + 1));
                i += 2;
                continue;
            }
            i++;
        }
        return sql.length();
    }

    private static int skipQuoted(String sql, int start, char quote) {
        int i = start + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            if (quote == '\'' && c == '\\' && i + 1 < sql.length()) {
                i += 2;
                continue;
            }
            i++;
        }
        return sql.length();
    }

}
