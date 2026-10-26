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
 * KingbaseES MySQL compatibility mode mapping.
 * <p>
 * Provides {@code ?} → {@code $n} rewrite and MySQL-oriented type codecs (r2dbc-mysql habits)
 * on the PostgreSQL wire protocol. Type OIDs are resolved from {@code pg_type} at connect time
 * so Kingbase MySQL-named types (e.g. {@code datetime}, {@code tinyint}) are covered.
 */
package io.r2dbc.kingbase.mysql;
