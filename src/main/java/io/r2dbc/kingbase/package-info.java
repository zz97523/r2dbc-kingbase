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

/**
 * R2DBC driver for KingbaseES ({@code driver=kingbase} / {@code r2dbc:kingbase://...}).
 * <p>
 * Mode mapping layers:
 * <ul>
 *   <li>{@link io.r2dbc.kingbase.mysql} — MySQL compatibility mode</li>
 *   <li>{@link io.r2dbc.kingbase.oracle} — Oracle compatibility mode</li>
 *   <li>{@link io.r2dbc.kingbase.sqlserver} — SQL Server compatibility mode</li>
 *   <li>{@link io.r2dbc.kingbase.postgresql} — PostgreSQL compatibility mode (identity SQL)</li>
 * </ul>
 * Wire protocol / codecs live in {@code io.r2dbc.postgresql}. Select mode with
 * {@link io.r2dbc.kingbase.KingbaseMode} (or {@link io.r2dbc.kingbase.KingbaseMode#AUTO}).
 */
package io.r2dbc.kingbase;
