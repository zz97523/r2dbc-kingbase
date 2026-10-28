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

package io.r2dbc.kingbase.sqlserver;

import io.r2dbc.kingbase.KingbaseSqlMapper;
import io.r2dbc.postgresql.util.Assert;
import javax.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SQL Server-mode SQL mapper for KingbaseES.
 * <p>
 * Rewrites T-SQL / JDBC-style placeholders to PostgreSQL {@code $1}, {@code $2}, …:
 * <ul>
 *   <li>{@code @name} — named (same name reuses the same {@code $n}, r2dbc-mssql style)</li>
 *   <li>{@code ?} — positional (each occurrence gets the next index)</li>
 * </ul>
 * Skips string literals, quoted identifiers, bracket identifiers {@code [...]}, comments,
 * dollar-quoted strings, and T-SQL globals {@code @@...}.
 */
public final class KingbaseSqlServerSqlMapper implements KingbaseSqlMapper {

    public static final KingbaseSqlServerSqlMapper INSTANCE = new KingbaseSqlServerSqlMapper();

    private KingbaseSqlServerSqlMapper() {
    }

    @Override
    public String map(String sql) {
        Assert.requireNonNull(sql, "sql must not be null");
        return rewritePlaceholders(sql, new StringBuilder(sql.length() + 16)).toString();
    }

    @Override
    public Map<String, Integer> namedParameterIndexes(String sql) {
        Assert.requireNonNull(sql, "sql must not be null");
        if (sql.indexOf('?') < 0 && sql.indexOf('@') < 0) {
            return Collections.emptyMap();
        }
        if (containsDollarParameter(sql)) {
            return Collections.emptyMap();
        }
        Map<String, Integer> wireIndexes = new LinkedHashMap<>();
        rewritePlaceholders(sql, null, wireIndexes);
        if (wireIndexes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> bindIndexes = new LinkedHashMap<>();
        wireIndexes.forEach((name, oneBased) -> bindIndexes.put(name, oneBased - 1));
        return Collections.unmodifiableMap(bindIndexes);
    }

    private static StringBuilder rewritePlaceholders(String sql, StringBuilder out) {
        return rewritePlaceholders(sql, out, new LinkedHashMap<>());
    }

    /**
     * Scan {@code sql} for {@code ?}/{@code @name} placeholders.
     * When {@code out} is non-null, append mapped SQL; always fill {@code wireIndexes} (name → 1-based {@code $n}).
     */
    private static StringBuilder rewritePlaceholders(String sql, @Nullable StringBuilder out,
                                                     Map<String, Integer> wireIndexes) {
        if (sql.indexOf('?') < 0 && sql.indexOf('@') < 0) {
            if (out != null) {
                out.append(sql);
            }
            return out;
        }

        if (containsDollarParameter(sql)) {
            throw new IllegalArgumentException(
                "Cannot mix '?' / '@name' and '$n' placeholders in Kingbase SQL Server mode SQL: " + sql);
        }

        StringBuilder target = out != null ? out : new StringBuilder(sql.length() + 16);
        Map<String, Integer> namedIndexes = wireIndexes;
        int nextParam = 0;
        int i = 0;
        int length = sql.length();

        while (i < length) {
            char c = sql.charAt(i);

            if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                if (end < 0) {
                    target.append(sql, i, length);
                    break;
                }
                target.append(sql, i, end + 1);
                i = end + 1;
                continue;
            }

            if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) {
                    target.append(sql, i, length);
                    break;
                }
                target.append(sql, i, end + 2);
                i = end + 2;
                continue;
            }

            if (c == '\'') {
                i = appendQuoted(sql, i, '\'', target);
                continue;
            }

            if (c == '"') {
                i = appendQuoted(sql, i, '"', target);
                continue;
            }

            // T-SQL bracket identifier [col]
            if (c == '[') {
                target.append('[');
                i++;
                while (i < length) {
                    char b = sql.charAt(i);
                    target.append(b);
                    i++;
                    if (b == ']') {
                        // escaped ]]
                        if (i < length && sql.charAt(i) == ']') {
                            target.append(']');
                            i++;
                            continue;
                        }
                        break;
                    }
                }
                continue;
            }

            if (c == '$') {
                int tagEnd = findDollarTagEnd(sql, i);
                if (tagEnd > i) {
                    String tag = sql.substring(i, tagEnd + 1);
                    int contentStart = tagEnd + 1;
                    int close = sql.indexOf(tag, contentStart);
                    if (close >= 0) {
                        target.append(sql, i, close + tag.length());
                        i = close + tag.length();
                        continue;
                    }
                }
            }

            if (c == '?') {
                nextParam++;
                target.append('$').append(nextParam);
                i++;
                continue;
            }

            if (c == '@') {
                // @@global — leave as-is (@@IDENTITY, @@VERSION, …)
                if (i + 1 < length && sql.charAt(i + 1) == '@') {
                    target.append("@@");
                    i += 2;
                    while (i < length && isNamePart(sql.charAt(i))) {
                        target.append(sql.charAt(i));
                        i++;
                    }
                    continue;
                }
                if (i + 1 < length && isNameStart(sql.charAt(i + 1))) {
                    int nameStart = i + 1;
                    int nameEnd = nameStart + 1;
                    while (nameEnd < length && isNamePart(sql.charAt(nameEnd))) {
                        nameEnd++;
                    }
                    String name = sql.substring(nameStart, nameEnd);
                    Integer existing = namedIndexes.get(name);
                    int index;
                    if (existing != null) {
                        index = existing;
                    } else {
                        nextParam++;
                        index = nextParam;
                        namedIndexes.put(name, index);
                    }
                    target.append('$').append(index);
                    i = nameEnd;
                    continue;
                }
            }

            target.append(c);
            i++;
        }

        return target;
    }

    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '@';
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
            if (c == '\'' || c == '"') {
                i = skipQuoted(sql, i, c);
                continue;
            }
            if (c == '[') {
                i++;
                while (i < length) {
                    char b = sql.charAt(i++);
                    if (b == ']') {
                        if (i < length && sql.charAt(i) == ']') {
                            i++;
                            continue;
                        }
                        break;
                    }
                }
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
                if (i + 1 < length && Character.isDigit(sql.charAt(i + 1))) {
                    return true;
                }
            }
            i++;
        }
        return false;
    }

    private static int findDollarTagEnd(String sql, int start) {
        if (sql.charAt(start) != '$') {
            return -1;
        }
        if (start + 1 < sql.length() && sql.charAt(start + 1) == '$') {
            return start + 1;
        }
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
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    out.append(quote);
                    i += 2;
                    continue;
                }
                return i + 1;
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
            i++;
        }
        return sql.length();
    }

}
