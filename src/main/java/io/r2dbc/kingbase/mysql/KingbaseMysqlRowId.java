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

import java.sql.RowId;
import java.util.Arrays;

/**
 * Simple {@link RowId} implementation for Kingbase MySQL mode.
 */
public final class KingbaseMysqlRowId implements RowId {

    private final byte[] bytes;

    public KingbaseMysqlRowId(String text) {
        this.bytes = text == null ? new byte[0] : text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public KingbaseMysqlRowId(byte[] bytes) {
        this.bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] getBytes() {
        return Arrays.copyOf(this.bytes, this.bytes.length);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RowId)) {
            return false;
        }
        return Arrays.equals(this.bytes, ((RowId) other).getBytes());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.bytes);
    }

    @Override
    public String toString() {
        return new String(this.bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

}
