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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.r2dbc.postgresql.codec.ArrayCodec;
import io.r2dbc.postgresql.codec.ArrayCodecDelegate;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.PostgresTypeIdentifier;
import io.r2dbc.postgresql.message.Format;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.function.Function;

/**
 * Array Codec for a scalar type whose OIDs are resolved from {@code pg_type}.
 *
 * @param <T> array component type
 */
public final class KingbaseTypedArrayCodec<T> extends ArrayCodec<T> {

    private final boolean encodingEnabled;

    private final Class<?> arrayType;

    public KingbaseTypedArrayCodec(ByteBufAllocator allocator, int scalarOid, int arrayOid,
                                   Codec<T> scalarCodec, Class<T> componentType,
                                   Function<T, String> encoder, boolean encodingEnabled) {
        super(allocator, identifier(arrayOid),
            new ScalarArrayDelegate<>(scalarOid, arrayOid, scalarCodec, componentType, encoder),
            componentType);
        this.encodingEnabled = encodingEnabled;
        this.arrayType = Array.newInstance(componentType, 0).getClass();
    }

    @Override
    public Class<?> type() {
        return this.arrayType;
    }

    @Override
    public boolean canEncode(Object value) {
        return this.encodingEnabled && super.canEncode(value);
    }

    @Override
    public boolean canEncodeNull(Class<?> type) {
        return this.encodingEnabled && super.canEncodeNull(type);
    }

    private static PostgresTypeIdentifier identifier(int oid) {
        return () -> oid;
    }

    private static final class ScalarArrayDelegate<T> implements ArrayCodecDelegate<T> {

        private final Class<T> componentType;

        private final Function<T, String> encoder;

        private final int scalarOid;

        private final int arrayOid;

        private final Codec<T> scalarCodec;

        ScalarArrayDelegate(int scalarOid, int arrayOid, Codec<T> scalarCodec,
                            Class<T> componentType, Function<T, String> encoder) {
            this.scalarOid = scalarOid;
            this.arrayOid = arrayOid;
            this.scalarCodec = scalarCodec;
            this.componentType = componentType;
            this.encoder = encoder;
        }

        @Override
        public Class<?> type() {
            return this.componentType;
        }

        @Override
        public Iterable<PostgresTypeIdentifier> getDataTypes() {
            return Collections.singleton(identifier(this.scalarOid));
        }

        @Override
        public String encodeToText(T value) {
            return this.encoder.apply(value);
        }

        @Override
        public PostgresTypeIdentifier getArrayDataType() {
            return identifier(this.arrayOid);
        }

        @Override
        public T decode(ByteBuf buffer, PostgresTypeIdentifier dataType, Format format,
                        Class<? extends T> type) {
            return this.scalarCodec.decode(buffer, this.scalarOid, format, type);
        }
    }

}
