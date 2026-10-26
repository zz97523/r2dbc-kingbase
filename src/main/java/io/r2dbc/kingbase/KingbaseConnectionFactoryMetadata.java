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

import io.r2dbc.spi.ConnectionFactoryMetadata;

/**
 * {@link ConnectionFactoryMetadata} for KingbaseES.
 */
public final class KingbaseConnectionFactoryMetadata implements ConnectionFactoryMetadata {

    /**
     * Driver / product name exposed via R2DBC metadata.
     */
    public static final String NAME = "KingbaseES";

    static final KingbaseConnectionFactoryMetadata INSTANCE = new KingbaseConnectionFactoryMetadata();

    private KingbaseConnectionFactoryMetadata() {
    }

    @Override
    public String getName() {
        return NAME;
    }

}
