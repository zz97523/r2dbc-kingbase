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

import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.codec.PostgresTypes;
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
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
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.JSONB;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.JSONB_ARRAY;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.JSON_ARRAY;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.MONEY;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.MONEY_ARRAY;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.NUMERIC;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.OID;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.POINT;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.POINT_ARRAY;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TEXT;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TIME;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TIMESTAMP;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TIMESTAMPTZ;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TIMETZ;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.VARBIT;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.VARCHAR;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.XML;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.XML_ARRAY;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;

/**
 * OID catalog for KingbaseES <strong>MySQL compatibility mode</strong> on the PostgreSQL wire.
 * <p>
 * Kingbase reuses the PG protocol but may expose MySQL-named types under <em>non-standard</em> OIDs
 * (e.g. {@code date=7944}, {@code time≈7950}). Codecs must not assume only {@link PostgresqlObjectId}
 * values — those are the stock PostgreSQL catalog, not Kingbase's MySQL-mode catalog.
 * <p>
 * OIDs are resolved from {@code pg_catalog.pg_type} at connect time (same pattern as
 * {@link io.r2dbc.postgresql.codec.BuiltinDynamicCodecs}). Hardcoded JDBC OID numbers are
 * <strong>not</strong> used as defaults — versions/builds may differ; {@code pg_type} is authoritative.
 */
final class KingbaseMysqlTypeOids {

    /**
     * MySQL-mode typenames commonly present in Kingbase {@code pg_type}.
     */
    private static final List<String> LOOKUP_TYPENAMES = Collections.unmodifiableList(Arrays.asList(
        "date", "time", "datetime", "timestamp", "year", "bit",
        "tinyint", "int1", "smallint", "mediumint", "int3", "middleint",
        "int", "integer", "bigint", "int unsigned", "bigint unsigned",
        "float", "double", "decimal", "numeric", "money",
        // intentionally omit bare "char" (PG 1-byte type); use varchar/text for strings
        "varchar", "text", "tinytext", "mediumtext", "longtext", "bpchar",
        "binary", "varbinary",
        "blob", "tinyblob", "mediumblob", "longblob", "bytea",
        "clob", "nclob", "bpcharbyte", "varcharbyte", "rowid",
        "bool", "boolean",
        "enum", "set", "json", "jsonb", "xml", "point"
    ));

    private final Set<Integer> dateOids = new HashSet<>();

    private final Set<Integer> timeOids = new HashSet<>();

    private final Set<Integer> timestampOids = new HashSet<>();

    private final Set<Integer> numericOids = new HashSet<>();

    private final Set<Integer> intUnsignedOids = new HashSet<>();

    private final Set<Integer> bigintUnsignedOids = new HashSet<>();

    private final Set<Integer> textOids = new HashSet<>();

    private final Set<Integer> binaryOids = new HashSet<>();

    private final Set<Integer> bitOids = new HashSet<>();

    private final Set<Integer> yearOids = new HashSet<>();

    private final Set<Integer> booleanOids = new HashSet<>();

    private final Set<Integer> rowidOids = new HashSet<>();

    private final Set<Integer> moneyOids = new HashSet<>();

    private final Set<Integer> enumOids = new HashSet<>();

    private final Set<Integer> setOids = new HashSet<>();

    private final Set<Integer> jsonOids = new HashSet<>();

    private final Set<Integer> jsonbOids = new HashSet<>();

    private final Set<Integer> xmlOids = new HashSet<>();

    private final Set<Integer> pointOids = new HashSet<>();

    private final Map<String, Integer> arrayOids = new HashMap<>();

    private final Map<String, Integer> namedOids = new HashMap<>();

    private int encodeDate = DATE.getObjectId();

    private int encodeTime = TIME.getObjectId();

    private int encodeTimestamp = TIMESTAMP.getObjectId();

    private int encodeTimestamptz = TIMESTAMPTZ.getObjectId();

    private int encodeYear = INT2.getObjectId();

    /**
     * r2dbc-mysql encodes boolean as TINYINT 0/1 → INT2 on the PG wire.
     * Kingbase tinyint OIDs from {@code pg_type} are used for decode categories.
     */
    private int encodeBoolean = INT2.getObjectId();

    private int encodeBigintUnsigned = NUMERIC.getObjectId();

    private int encodeBitAsInt = INT8.getObjectId();

    private int encodeText = VARCHAR.getObjectId();

    private int encodeBinary = BYTEA.getObjectId();

    private int encodeRowid = VARCHAR.getObjectId();

    private int encodeMoney = MONEY.getObjectId();

    private int encodeJson = JSON.getObjectId();

    private int encodeJsonb = JSONB.getObjectId();

    private int encodeXml = XML.getObjectId();

    private int encodePoint = POINT.getObjectId();

    private KingbaseMysqlTypeOids() {
    }

    /**
     * Stock PostgreSQL OIDs only (unit tests / fallback when {@code pg_type} lookup fails).
     */
    static KingbaseMysqlTypeOids postgresqlDefaults() {
        KingbaseMysqlTypeOids oids = new KingbaseMysqlTypeOids();
        oids.seedPostgresql();
        return oids;
    }

    static KingbaseMysqlTypeOids withNamedTypes(Map<String, Integer> types) {
        KingbaseMysqlTypeOids oids = postgresqlDefaults();
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

    /**
     * Load Kingbase MySQL-mode type OIDs from {@code pg_type}, then merge with PostgreSQL defaults.
     */
    static Mono<KingbaseMysqlTypeOids> load(PostgresqlConnection connection) {
        KingbaseMysqlTypeOids oids = postgresqlDefaults();
        return PostgresTypes.from(connection).lookupTypes(LOOKUP_TYPENAMES)
            .distinct(type -> type.getName().toLowerCase(Locale.ROOT))
            .doOnNext(type -> oids.accept(type.getName(), type.getObjectId(), type.getArrayObjectId()))
            .then(Mono.just(oids))
            .onErrorMap(error -> new R2dbcNonTransientResourceException(
                "Cannot resolve Kingbase MySQL compatibility type OIDs", error));
    }

    private void seedPostgresql() {
        addAll(this.dateOids, DATE);
        addAll(this.timeOids, TIME, TIMETZ, INTERVAL);
        addAll(this.timestampOids, TIMESTAMP, TIMESTAMPTZ, DATE);
        addAll(this.numericOids, INT2, INT4, INT8, FLOAT4, FLOAT8, NUMERIC, OID);
        addAll(this.textOids, TEXT, VARCHAR, BPCHAR);
        addAll(this.binaryOids, BYTEA);
        addAll(this.bitOids, BIT, VARBIT, BYTEA, INT2, INT4, INT8);
        addAll(this.yearOids, INT2, INT4, INT8, NUMERIC);
        addAll(this.booleanOids, BOOL, INT2, INT4, INT8, NUMERIC);
        addAll(this.moneyOids, MONEY);
        addAll(this.jsonOids, JSON, JSONB);
        addAll(this.jsonbOids, JSONB);
        addAll(this.xmlOids, XML);
        addAll(this.pointOids, POINT);
        this.namedOids.put("money", MONEY.getObjectId());
        this.namedOids.put("json", JSON.getObjectId());
        this.namedOids.put("jsonb", JSONB.getObjectId());
        this.namedOids.put("xml", XML.getObjectId());
        this.namedOids.put("point", POINT.getObjectId());
        this.arrayOids.put("money", MONEY_ARRAY.getObjectId());
        this.arrayOids.put("json", JSON_ARRAY.getObjectId());
        this.arrayOids.put("jsonb", JSONB_ARRAY.getObjectId());
        this.arrayOids.put("xml", XML_ARRAY.getObjectId());
        this.arrayOids.put("point", POINT_ARRAY.getObjectId());
        // date/time columns also readable as text on the wire
        this.dateOids.addAll(this.textOids);
        this.timeOids.addAll(this.textOids);
        this.timestampOids.addAll(this.textOids);
        this.yearOids.addAll(this.textOids);
        this.bitOids.addAll(this.textOids);
        this.numericOids.addAll(this.textOids);
        this.booleanOids.addAll(this.textOids);
    }

    private void accept(String typname, int oid) {
        accept(typname, oid, 0);
    }

    private void accept(String typname, int oid, int arrayOid) {
        if (typname == null) {
            return;
        }
        String name = typname.toLowerCase(Locale.ROOT);
        this.namedOids.put(name, oid);
        if (arrayOid > 0) {
            this.arrayOids.put(name, arrayOid);
        }
        switch (name) {
            case "date":
                this.dateOids.add(oid);
                this.timestampOids.add(oid);
                this.encodeDate = oid;
                break;
            case "time":
                this.timeOids.add(oid);
                this.encodeTime = oid;
                break;
            case "datetime":
                this.timestampOids.add(oid);
                this.dateOids.add(oid);
                this.encodeTimestamp = oid;
                break;
            case "timestamp":
                this.timestampOids.add(oid);
                // Prefer datetime for LocalDateTime binds when both exist (JDBC: datetime vs timestamp)
                this.encodeTimestamptz = oid;
                break;
            case "year":
                this.yearOids.add(oid);
                this.numericOids.add(oid);
                this.dateOids.add(oid);
                this.encodeYear = oid;
                break;
            case "bit":
                this.bitOids.add(oid);
                this.booleanOids.add(oid);
                break;
            case "tinyint":
            case "int1":
            case "smallint":
            case "mediumint":
            case "int3":
            case "middleint":
            case "int":
            case "integer":
            case "bigint":
            case "float":
            case "double":
            case "decimal":
            case "numeric":
                this.numericOids.add(oid);
                this.bitOids.add(oid);
                this.yearOids.add(oid);
                this.booleanOids.add(oid);
                break;
            case "money":
                this.moneyOids.add(oid);
                this.encodeMoney = oid;
                break;
            case "int unsigned":
                this.numericOids.add(oid);
                this.intUnsignedOids.add(oid);
                break;
            case "bigint unsigned":
                this.numericOids.add(oid);
                this.bigintUnsignedOids.add(oid);
                this.encodeBigintUnsigned = oid;
                break;
            case "bpchar":
            case "bpcharbyte":
                this.textOids.add(oid);
                break;
            case "varchar":
            case "varcharbyte":
            case "text":
            case "tinytext":
            case "mediumtext":
            case "longtext":
            case "clob":
            case "nclob":
                this.textOids.add(oid);
                this.dateOids.add(oid);
                this.timeOids.add(oid);
                this.timestampOids.add(oid);
                // Prefer unbounded text types for default string encode
                if ("text".equals(name) || "longtext".equals(name) || "mediumtext".equals(name)
                    || "tinytext".equals(name) || "clob".equals(name) || "nclob".equals(name)
                    || this.encodeText == VARCHAR.getObjectId()) {
                    this.encodeText = oid;
                }
                break;
            case "binary":
            case "varbinary":
            case "blob":
            case "tinyblob":
            case "mediumblob":
            case "longblob":
            case "bytea":
                this.binaryOids.add(oid);
                this.bitOids.add(oid);
                if ("bytea".equals(name)) {
                    this.encodeBinary = oid;
                } else if (this.encodeBinary == BYTEA.getObjectId()) {
                    this.encodeBinary = oid;
                }
                break;
            case "bool":
            case "boolean":
                this.numericOids.add(oid);
                this.booleanOids.add(oid);
                break;
            case "rowid":
                this.rowidOids.add(oid);
                this.textOids.add(oid);
                this.encodeRowid = oid;
                break;
            case "enum":
                this.enumOids.add(oid);
                this.textOids.add(oid);
                break;
            case "set":
                this.setOids.add(oid);
                this.textOids.add(oid);
                break;
            case "json":
                this.jsonOids.add(oid);
                this.encodeJson = oid;
                break;
            case "jsonb":
                this.jsonOids.add(oid);
                this.jsonbOids.add(oid);
                this.encodeJsonb = oid;
                break;
            case "xml":
                this.xmlOids.add(oid);
                this.encodeXml = oid;
                break;
            case "point":
                this.pointOids.add(oid);
                this.encodePoint = oid;
                break;
            default:
                break;
        }
    }

    private void acceptArray(String typname, int oid) {
        if (typname != null && oid > 0) {
            this.arrayOids.put(typname.toLowerCase(Locale.ROOT), oid);
        }
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

    boolean isNumeric(int oid) {
        return this.numericOids.contains(oid);
    }

    boolean isIntUnsigned(int oid) {
        return this.intUnsignedOids.contains(oid);
    }

    boolean isBigintUnsigned(int oid) {
        return this.bigintUnsignedOids.contains(oid);
    }

    boolean isText(int oid) {
        return this.textOids.contains(oid);
    }

    boolean isBinary(int oid) {
        return this.binaryOids.contains(oid) || equalsPg(oid, BYTEA);
    }

    boolean isBit(int oid) {
        return this.bitOids.contains(oid);
    }

    boolean isYear(int oid) {
        return this.yearOids.contains(oid);
    }

    boolean isBoolean(int oid) {
        return this.booleanOids.contains(oid);
    }

    boolean isRowid(int oid) {
        return this.rowidOids.contains(oid);
    }

    boolean isMoney(int oid) {
        return this.moneyOids.contains(oid);
    }

    boolean isEnum(int oid) {
        return this.enumOids.contains(oid);
    }

    boolean isSet(int oid) {
        return this.setOids.contains(oid);
    }

    boolean isJson(int oid) {
        return this.jsonOids.contains(oid);
    }

    boolean isJsonb(int oid) {
        return this.jsonbOids.contains(oid);
    }

    boolean isXml(int oid) {
        return this.xmlOids.contains(oid);
    }

    boolean isPoint(int oid) {
        return this.pointOids.contains(oid);
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

    int encodeYear() {
        return this.encodeYear;
    }

    int encodeBoolean() {
        return this.encodeBoolean;
    }

    int encodeBigintUnsigned() {
        return this.encodeBigintUnsigned;
    }

    int encodeBitAsInt() {
        return this.encodeBitAsInt;
    }

    int encodeText() {
        return this.encodeText;
    }

    int encodeBinary() {
        return this.encodeBinary;
    }

    int encodeRowid() {
        return this.encodeRowid;
    }

    int encodeMoney() {
        return this.encodeMoney;
    }

    int encodeJson() {
        return this.encodeJson;
    }

    int encodeJsonb() {
        return this.encodeJsonb;
    }

    int encodeXml() {
        return this.encodeXml;
    }

    int encodePoint() {
        return this.encodePoint;
    }

    int encodeOid(KingbaseMysqlType type) {
        switch (type) {
            case MONEY:
                return this.encodeMoney;
            case BIT:
                return this.encodeBitAsInt;
            case YEAR:
                return this.encodeYear;
            case DATE:
                return this.encodeDate;
            case TIME:
                return this.encodeTime;
            case DATETIME:
                return this.encodeTimestamp;
            case TIMESTAMP:
                return this.encodeTimestamptz;
            case VARCHAR:
            case TEXT:
                return this.encodeText;
            case BLOB:
                return this.encodeBinary;
            case ROWID:
                return this.encodeRowid;
            case BOOLEAN:
                return this.encodeBoolean;
            default:
                return type.getObjectId();
        }
    }

    int oid(String typname) {
        Integer oid = this.namedOids.get(typname.toLowerCase(Locale.ROOT));
        return oid == null ? 0 : oid;
    }

    int arrayOid(String typname) {
        Integer oid = this.arrayOids.get(typname.toLowerCase(Locale.ROOT));
        return oid == null ? 0 : oid;
    }

    /**
     * Whether this OID may be decoded for {@code javaType} using PG-wire <strong>text</strong> payloads.
     * <p>
     * Kingbase MySQL-mode types often use OIDs outside {@link PostgresqlObjectId}; the wire format is
     * still PostgreSQL text/binary, not the MySQL client protocol.
     */
    boolean canDecodeTextExtension(int dataType, Format format, boolean categoryMatch) {
        if (categoryMatch) {
            return true;
        }
        // Unknown / Kingbase-specific OID: accept text format and parse as PG text
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
