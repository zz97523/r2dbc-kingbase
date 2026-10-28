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

package io.r2dbc.kingbase.oracle;

import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.codec.PostgresTypes;
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BIT;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BOOL;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BPCHAR;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.BYTEA;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.DATE;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.FLOAT4;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.FLOAT8;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT2;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT4;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT8;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INTERVAL;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.JSON;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.NUMERIC;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.OID;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TEXT;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TIME;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TIMESTAMP;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TIMESTAMPTZ;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TIMETZ;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.VARCHAR;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;

/**
 * OID catalog for KingbaseES <strong>Oracle compatibility mode</strong> on the PostgreSQL wire.
 */
final class KingbaseOracleTypeOids {

    /** Oracle-mode typenames commonly present in Kingbase {@code pg_type}. */
    private static final List<String> LOOKUP_TYPENAMES = Collections.unmodifiableList(Arrays.asList(
        "number", "numeric", "float", "binary_float", "binary_double",
        "int", "integer", "bigint", "smallint", "pls_integer",
        "char", "nchar", "varchar", "varchar2", "nvarchar2", "bpchar", "text",
        "clob", "nclob", "long",
        "raw", "blob", "bytea", "longraw",
        "date", "time", "timetz", "timestamp", "timestamptz", "timestampltz",
        "interval", "intervalds", "dsinterval", "intervalym", "yminterval",
        "json", "jsonb", "xml", "money", "inet", "cidr", "macaddr", "macaddr8",
        "int4range", "numrange", "tsrange", "tstzrange", "daterange", "int8range",
        "tsvector", "tsquery", "jsonpath", "pg_lsn", "bfile",
        "record", "anyobject", "associative_array", "nestedtable", "varray",
        "bool", "boolean", "rowid", "urowid"
    ));

    private static final Set<String> GENERIC_OBJECT_TYPENAMES = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            "macaddr", "macaddr8", "int4range", "numrange", "tsrange", "tstzrange",
            "daterange", "int8range", "tsvector", "tsquery", "jsonpath", "pg_lsn",
            "bfile", "record", "anyobject", "associative_array", "nestedtable", "varray",
            "cidr", "inet", "json", "jsonb"
        )));

    private final Map<String, Integer> namedOids = new HashMap<>();

    private final Map<String, Integer> arrayOids = new HashMap<>();

    private final Map<Integer, String> oidNames = new HashMap<>();

    private final Set<Integer> genericObjectOids = new HashSet<>();

    private final Set<Integer> compositeOids = new HashSet<>();

    private final Set<Integer> numberOids = new HashSet<>();

    private final Set<Integer> textOids = new HashSet<>();

    private final Set<Integer> binaryOids = new HashSet<>();

    private final Set<Integer> dateOids = new HashSet<>();

    private final Set<Integer> timestampOids = new HashSet<>();

    private final Set<Integer> timeOids = new HashSet<>();

    private final Set<Integer> intervalOids = new HashSet<>();

    private final Set<Integer> intervalDsOids = new HashSet<>();

    private final Set<Integer> intervalYmOids = new HashSet<>();

    private final Set<Integer> booleanOids = new HashSet<>();

    private final Set<Integer> bitBooleanOids = new HashSet<>();

    private final Set<Integer> rowidOids = new HashSet<>();

    private final Set<Integer> timestampltzOids = new HashSet<>();

    private final Set<Integer> moneyOids = new HashSet<>();

    private final Set<Integer> xmlOids = new HashSet<>();

    private final Set<Integer> cidrOids = new HashSet<>();

    private int encodeNumber = NUMERIC.getObjectId();

    private int encodeFloat = FLOAT4.getObjectId();

    private int encodeDouble = FLOAT8.getObjectId();

    private int encodeText = VARCHAR.getObjectId();

    private int encodeBinary = BYTEA.getObjectId();

    /** Oracle DATE → TIMESTAMP OID (time-of-day included). */
    private int encodeDate = TIMESTAMP.getObjectId();

    private int encodeTime = TIME.getObjectId();

    private int encodeTimestamp = TIMESTAMP.getObjectId();

    private int encodeTimestamptz = TIMESTAMPTZ.getObjectId();

    private int encodeInterval = INTERVAL.getObjectId();

    private int encodeIntervalDs = INTERVAL.getObjectId();

    private int encodeIntervalYm = INTERVAL.getObjectId();

    private int encodeBoolean = BOOL.getObjectId();

    private int encodeRowid = VARCHAR.getObjectId();

    private int encodeTimestampltz = TIMESTAMP.getObjectId();

    private int encodeJson = JSON.getObjectId();

    private KingbaseOracleTypeOids() {
    }

    static KingbaseOracleTypeOids postgresqlDefaults() {
        KingbaseOracleTypeOids oids = new KingbaseOracleTypeOids();
        oids.seedPostgresql();
        return oids;
    }

    static KingbaseOracleTypeOids withNamedTypes(Map<String, Integer> types) {
        KingbaseOracleTypeOids oids = postgresqlDefaults();
        if (types != null) {
            types.forEach((name, oid) -> {
                if (name != null && name.startsWith("_")) {
                    oids.acceptArray(name.substring(1), oid);
                } else {
                    oids.accept(name, oid);
                }
            });
        }
        return oids;
    }

    void registerCompositeType(String typeName, int oid, int arrayOid) {
        acceptComposite(typeName, oid, arrayOid);
    }

    static Mono<KingbaseOracleTypeOids> load(PostgresqlConnection connection) {
        KingbaseOracleTypeOids oids = postgresqlDefaults();
        return PostgresTypes.from(connection).lookupTypes(LOOKUP_TYPENAMES)
            .distinct(type -> type.getName().toLowerCase(Locale.ROOT))
            .doOnNext(type -> oids.accept(type.getName(), type.getObjectId(), type.getArrayObjectId()))
            .then(loadCompositeTypes(connection, oids))
            .thenReturn(oids)
            .onErrorMap(error -> new R2dbcNonTransientResourceException(
                "Cannot resolve Kingbase Oracle compatibility type OIDs", error));
    }

    private static Mono<Void> loadCompositeTypes(PostgresqlConnection connection,
                                                  KingbaseOracleTypeOids oids) {
        String sql = "SELECT t.oid, t.typarray, t.typname "
            + "FROM pg_catalog.pg_type t JOIN pg_catalog.pg_namespace n ON n.oid=t.typnamespace "
            + "WHERE t.typtype='c' AND n.nspname IN (SELECT unnest(current_schemas(false)))";
        return Flux.from(connection.createStatement(sql).execute())
            .flatMap(result -> result.map((row, metadata) -> {
                Long oid = row.get("oid", Long.class);
                Long arrayOid = row.get("typarray", Long.class);
                String name = row.get("typname", String.class);
                oids.acceptComposite(name, PostgresqlObjectId.toInt(oid),
                    PostgresqlObjectId.toInt(arrayOid));
                return name;
            }))
            .then();
    }

    private void seedPostgresql() {
        addAll(this.numberOids, INT2, INT4, INT8, FLOAT4, FLOAT8, NUMERIC, OID);
        addAll(this.textOids, TEXT, VARCHAR, BPCHAR);
        addAll(this.binaryOids, BYTEA);
        addAll(this.dateOids, DATE, TIMESTAMP, TIMESTAMPTZ);
        addAll(this.timeOids, TIME, TIMETZ);
        addAll(this.timestampOids, TIMESTAMP, TIMESTAMPTZ, DATE, TIME, TIMETZ);
        addAll(this.intervalOids, INTERVAL);
        addAll(this.booleanOids, BOOL, BIT, INT2, INT4, INT8, NUMERIC);
        addAll(this.bitBooleanOids, BIT);
        addAll(this.rowidOids, VARCHAR, TEXT);
        addAll(this.timestampltzOids, TIMESTAMP, TIMESTAMPTZ);
        this.dateOids.addAll(this.textOids);
        this.timeOids.addAll(this.textOids);
        this.timestampOids.addAll(this.textOids);
        this.numberOids.addAll(this.textOids);
        this.intervalOids.addAll(this.textOids);
        this.booleanOids.addAll(this.textOids);
        accept("xml", PostgresqlObjectId.XML.getObjectId(), PostgresqlObjectId.XML_ARRAY.getObjectId());
        accept("money", PostgresqlObjectId.MONEY.getObjectId(), PostgresqlObjectId.MONEY_ARRAY.getObjectId());
        accept("cidr", 650, 651);
    }

    private void accept(String typname, int oid) {
        accept(typname, oid, 0);
    }

    private void accept(String typname, int oid, int arrayOid) {
        if (typname == null) {
            return;
        }
        String name = typname.toLowerCase(Locale.ROOT);
        Integer previousOid = this.namedOids.get(name);
        if (previousOid == null || oid > previousOid) {
            this.namedOids.put(name, oid);
        }
        this.oidNames.put(oid, name);
        if (arrayOid > 0) {
            Integer previousArrayOid = this.arrayOids.get(name);
            if (previousArrayOid == null || arrayOid > previousArrayOid) {
                this.arrayOids.put(name, arrayOid);
            }
        }
        if (GENERIC_OBJECT_TYPENAMES.contains(name)) {
            this.genericObjectOids.add(oid);
        }
        switch (name) {
            case "number":
            case "numeric":
            case "float":
            case "int":
            case "integer":
            case "bigint":
            case "smallint":
            case "pls_integer":
                this.numberOids.add(oid);
                this.booleanOids.add(oid);
                if ("number".equals(name) || "numeric".equals(name)) {
                    this.encodeNumber = oid;
                }
                break;
            case "binary_float":
                this.numberOids.add(oid);
                this.encodeFloat = oid;
                break;
            case "binary_double":
                this.numberOids.add(oid);
                this.encodeDouble = oid;
                break;
            case "char":
            case "nchar":
            case "bpchar":
                this.textOids.add(oid);
                break;
            case "varchar":
            case "varchar2":
            case "nvarchar2":
            case "text":
            case "clob":
            case "nclob":
            case "long":
                this.textOids.add(oid);
                this.dateOids.add(oid);
                this.timestampOids.add(oid);
                if ("clob".equals(name) || "nclob".equals(name) || "text".equals(name)
                    || "long".equals(name) || this.encodeText == VARCHAR.getObjectId()) {
                    this.encodeText = oid;
                }
                break;
            case "raw":
            case "blob":
            case "bytea":
            case "longraw":
                this.binaryOids.add(oid);
                // Prefer bytea > raw > blob > longraw for default byte[] binds
                if ("bytea".equals(name)) {
                    this.encodeBinary = oid;
                } else if ("raw".equals(name) && this.encodeBinary == BYTEA.getObjectId()) {
                    this.encodeBinary = oid;
                } else if ("blob".equals(name) && this.encodeBinary == BYTEA.getObjectId()) {
                    this.encodeBinary = oid;
                } else if ("longraw".equals(name) && this.encodeBinary == BYTEA.getObjectId()) {
                    this.encodeBinary = oid;
                }
                break;
            case "date":
                this.dateOids.add(oid);
                this.timestampOids.add(oid);
                this.encodeDate = oid;
                break;
            case "time":
            case "timetz":
                this.timeOids.add(oid);
                this.timestampOids.add(oid);
                if ("time".equals(name) || this.encodeTime == TIME.getObjectId()) {
                    this.encodeTime = oid;
                }
                break;
            case "timestamp":
                this.timestampOids.add(oid);
                this.dateOids.add(oid);
                this.encodeTimestamp = oid;
                break;
            case "timestampltz":
                this.timestampOids.add(oid);
                this.timestampltzOids.add(oid);
                this.dateOids.add(oid);
                this.encodeTimestampltz = oid;
                break;
            case "timestamptz":
                this.timestampOids.add(oid);
                this.encodeTimestamptz = oid;
                break;
            case "interval":
                this.intervalOids.add(oid);
                this.encodeInterval = oid;
                break;
            case "intervalds":
            case "dsinterval":
                this.intervalOids.add(oid);
                this.intervalDsOids.add(oid);
                this.encodeIntervalDs = oid;
                if (this.encodeInterval == INTERVAL.getObjectId()) {
                    this.encodeInterval = oid;
                }
                break;
            case "intervalym":
            case "yminterval":
                this.intervalOids.add(oid);
                this.intervalYmOids.add(oid);
                this.encodeIntervalYm = oid;
                if (this.encodeInterval == INTERVAL.getObjectId()) {
                    this.encodeInterval = oid;
                }
                break;
            case "json":
                this.textOids.add(oid);
                this.encodeJson = oid;
                break;
            case "xml":
                this.xmlOids.add(oid);
                break;
            case "money":
                this.moneyOids.add(oid);
                break;
            case "cidr":
                this.cidrOids.add(oid);
                break;
            case "bool":
            case "boolean":
                this.booleanOids.add(oid);
                this.encodeBoolean = oid;
                break;
            case "bit":
                this.booleanOids.add(oid);
                this.bitBooleanOids.add(oid);
                break;
            case "rowid":
            case "urowid":
                this.textOids.add(oid);
                this.rowidOids.add(oid);
                this.encodeRowid = oid;
                break;
            default:
                break;
        }
    }

    private void acceptArray(String typname, int oid) {
        if (typname == null) {
            return;
        }
        String name = typname.toLowerCase(Locale.ROOT);
        Integer previous = this.arrayOids.get(name);
        if (previous == null || oid > previous) {
            this.arrayOids.put(name, oid);
        }
    }

    private void acceptComposite(String typname, int oid, int arrayOid) {
        if (typname == null) {
            return;
        }
        String name = typname.toLowerCase(Locale.ROOT);
        this.namedOids.put(name, oid);
        this.oidNames.put(oid, name);
        this.compositeOids.add(oid);
        if (arrayOid > 0) {
            this.arrayOids.put(name, arrayOid);
        }
    }

    boolean isNumber(int oid) {
        return this.numberOids.contains(oid);
    }

    boolean isText(int oid) {
        return this.textOids.contains(oid);
    }

    boolean isBinary(int oid) {
        return this.binaryOids.contains(oid) || equalsPg(oid, BYTEA);
    }

    boolean isDate(int oid) {
        return this.dateOids.contains(oid);
    }

    boolean isTime(int oid) {
        return this.timeOids.contains(oid);
    }

    boolean isTimestamp(int oid) {
        return this.timestampOids.contains(oid);
    }

    boolean isInterval(int oid) {
        return this.intervalOids.contains(oid);
    }

    boolean isIntervalDs(int oid) {
        return this.intervalDsOids.contains(oid);
    }

    boolean isIntervalYm(int oid) {
        return this.intervalYmOids.contains(oid);
    }

    boolean isBoolean(int oid) {
        return this.booleanOids.contains(oid);
    }

    boolean isBitBoolean(int oid) {
        return this.bitBooleanOids.contains(oid);
    }

    boolean isRowid(int oid) {
        return this.rowidOids.contains(oid);
    }

    boolean isTimestampltz(int oid) {
        return this.timestampltzOids.contains(oid);
    }

    boolean isMoney(int oid) {
        return this.moneyOids.contains(oid);
    }

    boolean isXml(int oid) {
        return this.xmlOids.contains(oid);
    }

    boolean isCidr(int oid) {
        return this.cidrOids.contains(oid);
    }

    boolean isGenericObject(int oid) {
        return this.genericObjectOids.contains(oid);
    }

    boolean isComposite(int oid) {
        return this.compositeOids.contains(oid);
    }

    String name(int oid) {
        return this.oidNames.get(oid);
    }

    int oid(String name) {
        Integer oid = this.namedOids.get(name.toLowerCase(Locale.ROOT));
        return oid == null ? 0 : oid;
    }

    int arrayOid(String name) {
        Integer oid = this.arrayOids.get(name.toLowerCase(Locale.ROOT));
        return oid == null ? 0 : oid;
    }

    Set<Integer> genericObjectOids() {
        return Collections.unmodifiableSet(this.genericObjectOids);
    }

    Set<Integer> compositeOids() {
        return Collections.unmodifiableSet(this.compositeOids);
    }

    Set<String> genericObjectTypeNames() {
        return GENERIC_OBJECT_TYPENAMES;
    }

    int encodeNumber() {
        return this.encodeNumber;
    }

    int encodeFloat() {
        return this.encodeFloat;
    }

    int encodeDouble() {
        return this.encodeDouble;
    }

    int encodeText() {
        return this.encodeText;
    }

    int encodeBinary() {
        return this.encodeBinary;
    }

    int encodeDate() {
        return this.encodeDate;
    }

    int encodeTime() {
        return this.encodeTime;
    }

    int encodeTimestamp() {
        return this.encodeTimestamp;
    }

    int encodeTimestamptz() {
        return this.encodeTimestamptz;
    }

    int encodeInterval() {
        return this.encodeInterval;
    }

    int encodeIntervalDs() {
        return this.encodeIntervalDs;
    }

    int encodeIntervalYm() {
        return this.encodeIntervalYm;
    }

    int encodeBoolean() {
        return this.encodeBoolean;
    }

    int encodeRowid() {
        return this.encodeRowid;
    }

    int encodeTimestampltz() {
        return this.encodeTimestampltz;
    }

    int encodeJson() {
        return this.encodeJson;
    }

    int encodeOid(KingbaseOracleType type) {
        switch (type) {
            case NUMBER:
                return this.encodeNumber;
            case BINARY_FLOAT:
                return this.encodeFloat;
            case BINARY_DOUBLE:
                return this.encodeDouble;
            case BOOLEAN:
                return this.encodeBoolean;
            case CHAR:
            case VARCHAR2:
            case CLOB:
            case LONG:
                return this.encodeText;
            case RAW:
            case BLOB:
            case LONG_RAW:
                return this.encodeBinary;
            case DATE:
                return this.encodeDate;
            case TIME:
                return this.encodeTime;
            case TIMESTAMP:
                return this.encodeTimestamp;
            case TIMESTAMPTZ:
                return this.encodeTimestamptz;
            case INTERVAL_DS:
                return this.encodeIntervalDs;
            case INTERVAL_YM:
                return this.encodeIntervalYm;
            case TIMESTAMP_LTZ:
                return this.encodeTimestampltz;
            case ROWID:
                return this.encodeRowid;
            case JSON:
                return this.encodeJson;
            default:
                return type.getObjectId();
        }
    }

    /**
     * Whether this OID may be decoded using PG-wire <strong>text</strong> payloads.
     * <p>
     * Kingbase Oracle-mode types often use OIDs outside {@link PostgresqlObjectId}; payloads
     * remain PostgreSQL text/binary on the wire.
     */
    boolean canDecodeTextExtension(int dataType, Format format, boolean categoryMatch) {
        if (categoryMatch) {
            return true;
        }
        return format == FORMAT_TEXT && !PostgresqlObjectId.isValid(dataType);
    }

    static boolean equalsPg(int dataType, PostgresqlObjectId expected) {
        return PostgresqlObjectId.isValid(dataType) && PostgresqlObjectId.valueOf(dataType) == expected;
    }

    static boolean isPgOid(int dataType, Set<PostgresqlObjectId> set) {
        return PostgresqlObjectId.isValid(dataType) && set.contains(PostgresqlObjectId.valueOf(dataType));
    }

    private static void addAll(Set<Integer> target, PostgresqlObjectId... ids) {
        for (PostgresqlObjectId id : ids) {
            target.add(id.getObjectId());
        }
    }

}
