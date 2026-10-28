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

import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
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
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.CHAR;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.DATE;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.FLOAT4;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.FLOAT8;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT2;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT4;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INT8;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.INTERVAL;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.MONEY;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.MONEY_ARRAY;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.NUMERIC;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.OID;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TEXT;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TIME;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TIMESTAMP;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TIMESTAMPTZ;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TIMETZ;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.UUID;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.VARBIT;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.VARCHAR;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.XML;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.XML_ARRAY;
import static io.r2dbc.postgresql.message.Format.FORMAT_TEXT;

/**
 * OID catalog for KingbaseES <strong>SQL Server compatibility mode</strong> on the PostgreSQL wire.
 * <p>
 * Kingbase reuses the PG protocol but may expose SQL Server-named types under non-standard OIDs.
 * Codecs resolve OIDs from {@code pg_catalog.pg_type} at connect time.
 */
final class KingbaseSqlServerTypeOids {

    private static final String PG_CATALOG = "pg_catalog";

    /**
     * Only single-token typenames — lookup rejects spaces.
     */
    private static final List<String> LOOKUP_TYPENAMES = Collections.unmodifiableList(Arrays.asList(
        "tinyint", "smallint", "int", "integer", "bigint",
        "real", "float", "double", "decimal", "numeric", "money", "smallmoney",
        "bit", "bool", "boolean",
        "date", "time", "datetime", "datetime2", "smalldatetime", "datetimeoffset",
        // omit bare "char" — PG internal char / T-SQL char(1) must not become encodeText default
        "nchar", "varchar", "nvarchar", "text", "ntext", "bpchar", "bpcharbyte", "varcharbyte",
        "binary", "varbinary", "image", "bytea",
        "uniqueidentifier", "uuid",
        "timestamp", "rowversion",
        "clob", "nclob", "xml"
    ));

    private final Set<Integer> dateOids = new HashSet<>();

    private final Set<Integer> timeOids = new HashSet<>();

    private final Set<Integer> timestampOids = new HashSet<>();

    private final Set<Integer> numericOids = new HashSet<>();

    private final Map<Integer, Class<?>> numericJavaTypes = new HashMap<>();

    private final Set<Integer> textOids = new HashSet<>();

    private final Set<Integer> characterOids = new HashSet<>();

    private final Set<Integer> binaryOids = new HashSet<>();

    private final Set<Integer> rowVersionOids = new HashSet<>();

    private final Set<Integer> bitOids = new HashSet<>();

    private final Set<Integer> booleanOids = new HashSet<>();

    private final Set<Integer> uuidOids = new HashSet<>();

    private final Set<Integer> xmlOids = new HashSet<>();

    private final Set<Integer> moneyOids = new HashSet<>();

    private final Map<String, Integer> arrayOids = new HashMap<>();

    private final Map<String, Integer> namedOids = new HashMap<>();

    private int encodeDate = DATE.getObjectId();

    private int encodeTime = TIME.getObjectId();

    private int encodeTimestamp = TIMESTAMP.getObjectId();

    private int encodeTimestamptz = TIMESTAMPTZ.getObjectId();

    /** Kingbase T-SQL {@code datetime}/{@code smalldatetime} — never {@code pg_catalog.datetime}. */
    private int encodeDatetime = TIMESTAMP.getObjectId();

    /** Kingbase T-SQL {@code datetime2}. */
    private int encodeDatetime2 = TIMESTAMP.getObjectId();

    /** r2dbc-mssql encodes boolean as TINYINT 0/1 → INT2 on the PG wire. */
    private int encodeBoolean = INT2.getObjectId();

    private int encodeBitAsInt = INT2.getObjectId();

    private int encodeText = VARCHAR.getObjectId();

    private int encodeBinary = BYTEA.getObjectId();

    private int encodeUuid = UUID.getObjectId();

    private int encodeXml = XML.getObjectId();

    private int encodeMoney = MONEY.getObjectId();

    private KingbaseSqlServerTypeOids() {
    }

    static KingbaseSqlServerTypeOids postgresqlDefaults() {
        KingbaseSqlServerTypeOids oids = new KingbaseSqlServerTypeOids();
        oids.seedPostgresql();
        return oids;
    }

    /**
     * Test / fixture helper: seed OIDs as if {@code pg_type} had returned the given typenames.
     */
    public static KingbaseSqlServerTypeOids withNamedTypes(Map<String, Integer> types) {
        KingbaseSqlServerTypeOids oids = postgresqlDefaults();
        if (types != null) {
            types.forEach((name, oid) -> {
                if (name != null && name.startsWith("_")) {
                    oids.acceptArray(name.substring(1), oid);
                } else {
                    oids.accept(name, oid, null, 0);
                }
            });
        }
        return oids;
    }

    /**
     * Register one typename/OID pair (for tests simulating {@code pg_type} row order).
     */
    public void registerType(String typname, int oid) {
        accept(typname, oid, null, 0);
    }

    public void registerType(String typname, int oid, String namespace) {
        accept(typname, oid, namespace, 0);
    }

    public void registerType(String typname, int oid, int arrayOid, String namespace) {
        accept(typname, oid, namespace, arrayOid);
    }

    static Mono<KingbaseSqlServerTypeOids> load(PostgresqlConnection connection) {
        KingbaseSqlServerTypeOids oids = postgresqlDefaults();
        return KingbaseSqlServerPgTypeLoader.loadInto(oids, connection, LOOKUP_TYPENAMES)
            .thenReturn(oids)
            .onErrorMap(error -> new R2dbcNonTransientResourceException(
                "Cannot resolve Kingbase SQL Server compatibility type OIDs", error));
    }

    boolean needsTextFallback() {
        return equalsPg(this.encodeText, VARCHAR) || equalsPg(this.encodeText, TEXT)
            || equalsPg(this.encodeText, BPCHAR) || equalsPg(this.encodeText, CHAR);
    }

    boolean needsDateFallback() {
        return equalsPg(this.encodeDate, DATE);
    }

    boolean needsBinaryFallback() {
        return equalsPg(this.encodeBinary, BYTEA) || equalsPg(this.encodeBinary, TIMESTAMP);
    }

    boolean needsBitFallback() {
        return this.encodeBitAsInt == INT2.getObjectId() || equalsPg(this.encodeBitAsInt, BIT);
    }

    boolean needsDatetimeFallback() {
        return isStockTemporalEncode(this.encodeDatetime);
    }

    boolean needsDatetime2Fallback() {
        return isStockTemporalEncode(this.encodeDatetime2);
    }

    private static boolean isStockTemporalEncode(int oid) {
        return oid == TIMESTAMP.getObjectId() || oid == TIMESTAMPTZ.getObjectId();
    }

    private void seedPostgresql() {
        addAll(this.dateOids, DATE);
        addAll(this.timeOids, TIME, TIMETZ, INTERVAL);
        addAll(this.timestampOids, TIMESTAMP, TIMESTAMPTZ, DATE);
        addAll(this.numericOids, INT2, INT4, INT8, FLOAT4, FLOAT8, NUMERIC, OID);
        this.numericJavaTypes.put(INT2.getObjectId(), Short.class);
        this.numericJavaTypes.put(INT4.getObjectId(), Integer.class);
        this.numericJavaTypes.put(INT8.getObjectId(), Long.class);
        this.numericJavaTypes.put(OID.getObjectId(), Long.class);
        this.numericJavaTypes.put(FLOAT4.getObjectId(), Float.class);
        this.numericJavaTypes.put(FLOAT8.getObjectId(), Double.class);
        this.numericJavaTypes.put(NUMERIC.getObjectId(), BigDecimal.class);
        addAll(this.textOids, TEXT, VARCHAR, BPCHAR);
        addAll(this.characterOids, TEXT, VARCHAR, BPCHAR);
        addAll(this.binaryOids, BYTEA);
        addAll(this.bitOids, BIT, VARBIT, BYTEA, INT2, INT4, INT8);
        addAll(this.booleanOids, BOOL, INT2, INT4, INT8, NUMERIC);
        addAll(this.uuidOids, UUID);
        addAll(this.xmlOids, XML);
        addAll(this.moneyOids, MONEY);
        this.namedOids.put("money", MONEY.getObjectId());
        this.arrayOids.put("xml", XML_ARRAY.getObjectId());
        this.arrayOids.put("money", MONEY_ARRAY.getObjectId());
        this.dateOids.addAll(this.textOids);
        this.timeOids.addAll(this.textOids);
        this.timestampOids.addAll(this.textOids);
        this.bitOids.addAll(this.textOids);
        this.numericOids.addAll(this.textOids);
        this.booleanOids.addAll(this.textOids);
        this.uuidOids.addAll(this.textOids);
    }

    void accept(String typname, int oid, String namespace) {
        accept(typname, oid, namespace, 0);
    }

    void accept(String typname, int oid, String namespace, int arrayOid) {
        if (typname == null) {
            return;
        }
        boolean pgCatalog = PG_CATALOG.equals(namespace);
        String name = typname.toLowerCase(Locale.ROOT);
        Integer previousNamedOid = this.namedOids.get(name);
        if (previousNamedOid == null || !pgCatalog) {
            this.namedOids.put(name, oid);
        }
        if (arrayOid > 0 && (!this.arrayOids.containsKey(name) || !pgCatalog)) {
            this.arrayOids.put(name, arrayOid);
        }
        switch (name) {
            case "date":
                this.dateOids.add(oid);
                this.timestampOids.add(oid);
                if (!pgCatalog) {
                    this.encodeDate = oid;
                } else {
                    setEncodeDate(oid);
                }
                break;
            case "time":
                this.timeOids.add(oid);
                setEncodeTime(oid);
                break;
            case "datetime":
            case "smalldatetime":
                this.timestampOids.add(oid);
                this.dateOids.add(oid);
                if (!pgCatalog) {
                    this.encodeDatetime = oid;
                }
                break;
            case "datetime2":
                this.timestampOids.add(oid);
                this.dateOids.add(oid);
                if (!pgCatalog) {
                    this.encodeDatetime2 = oid;
                }
                break;
            case "timestamp":
                if (pgCatalog) {
                    this.timestampOids.add(oid);
                    setEncodeTimestamp(oid);
                } else {
                    this.binaryOids.add(oid);
                    this.rowVersionOids.add(oid);
                }
                break;
            case "datetimeoffset":
                this.timestampOids.add(oid);
                if (!pgCatalog) {
                    this.encodeTimestamptz = oid;
                } else {
                    setEncodeTimestamptz(oid);
                }
                break;
            case "bit":
                this.bitOids.add(oid);
                this.booleanOids.add(oid);
                if (!pgCatalog) {
                    this.encodeBitAsInt = oid;
                    this.encodeBoolean = oid;
                } else {
                    setEncodeBitAsIntForBit(oid);
                    setEncodeBooleanForBit(oid);
                }
                break;
            case "tinyint":
            case "smallint":
                addNumericType(oid, Short.class);
                break;
            case "int":
            case "integer":
                addNumericType(oid, Integer.class);
                break;
            case "bigint":
                addNumericType(oid, Long.class);
                break;
            case "real":
                addNumericType(oid, Float.class);
                break;
            case "float":
            case "double":
                addNumericType(oid, Double.class);
                break;
            case "decimal":
            case "numeric":
                addNumericType(oid, BigDecimal.class);
                break;
            case "smallmoney":
                addNumericType(oid, BigDecimal.class);
                this.moneyOids.add(oid);
                break;
            case "money":
                this.moneyOids.add(oid);
                setEncodeMoney(oid);
                break;
            case "nchar":
                this.textOids.add(oid);
                this.characterOids.add(oid);
                this.dateOids.add(oid);
                this.timeOids.add(oid);
                this.timestampOids.add(oid);
                setPreferredEncodeText(oid, pgCatalog);
                break;
            case "varchar":
                this.textOids.add(oid);
                this.characterOids.add(oid);
                if (!pgCatalog) {
                    this.encodeText = oid;
                } else {
                    setEncodeText(oid, VARCHAR);
                }
                break;
            case "char":
                this.textOids.add(oid);
                this.characterOids.add(oid);
                break;
            case "bpchar":
                this.textOids.add(oid);
                this.characterOids.add(oid);
                setEncodeText(oid, BPCHAR);
                break;
            case "bpcharbyte":
            case "varcharbyte":
                this.textOids.add(oid);
                this.characterOids.add(oid);
                this.dateOids.add(oid);
                this.timeOids.add(oid);
                this.timestampOids.add(oid);
                break;
            case "nvarchar":
                this.textOids.add(oid);
                this.characterOids.add(oid);
                this.dateOids.add(oid);
                this.timeOids.add(oid);
                this.timestampOids.add(oid);
                setPreferredEncodeText(oid, pgCatalog);
                break;
            case "ntext":
                this.textOids.add(oid);
                this.characterOids.add(oid);
                this.dateOids.add(oid);
                this.timeOids.add(oid);
                this.timestampOids.add(oid);
                setEncodeText(oid, TEXT);
                break;
            case "text":
                this.textOids.add(oid);
                this.characterOids.add(oid);
                this.dateOids.add(oid);
                this.timeOids.add(oid);
                this.timestampOids.add(oid);
                setEncodeText(oid, TEXT);
                break;
            case "clob":
            case "nclob":
                this.textOids.add(oid);
                break;
            case "binary":
            case "varbinary":
            case "image":
                this.binaryOids.add(oid);
                this.encodeBinary = oid;
                break;
            case "bytea":
                this.binaryOids.add(oid);
                setEncodeBinary(oid, BYTEA);
                break;
            case "rowversion":
                this.binaryOids.add(oid);
                this.rowVersionOids.add(oid);
                this.bitOids.add(oid);
                setEncodeBinary(oid, BYTEA);
                break;
            case "uniqueidentifier":
            case "uuid":
                this.uuidOids.add(oid);
                this.textOids.add(oid);
                setEncodeUuid(oid);
                break;
            case "xml":
                this.xmlOids.add(oid);
                this.textOids.add(oid);
                setEncodeXml(oid);
                break;
            case "bool":
            case "boolean":
                this.numericOids.add(oid);
                this.booleanOids.add(oid);
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

    private void addNumericType(int oid, Class<?> javaType) {
        this.numericOids.add(oid);
        this.bitOids.add(oid);
        this.booleanOids.add(oid);
        this.numericJavaTypes.put(oid, javaType);
    }

    int oid(String typname) {
        Integer oid = this.namedOids.get(typname.toLowerCase(Locale.ROOT));
        return oid == null ? 0 : oid;
    }

    /**
     * Prefer Kingbase SQL Server-mode OIDs over stock {@link PostgresqlObjectId} catalog values.
     * {@code pg_type} often returns both (e.g. {@code nvarchar} + {@code pg_catalog.varchar}).
     */
    private void setEncodeDate(int oid) {
        if (preferEncodeOid(this.encodeDate, oid, DATE)) {
            this.encodeDate = oid;
        }
    }

    private void setEncodeTime(int oid) {
        if (preferEncodeOid(this.encodeTime, oid, TIME)) {
            this.encodeTime = oid;
        }
    }

    private void setEncodeTimestamp(int oid) {
        if (preferEncodeOid(this.encodeTimestamp, oid, TIMESTAMP)) {
            this.encodeTimestamp = oid;
        }
    }

    private void setEncodeTimestamptz(int oid) {
        if (preferEncodeOid(this.encodeTimestamptz, oid, TIMESTAMPTZ)) {
            this.encodeTimestamptz = oid;
        }
    }

    private void setEncodeText(int oid, PostgresqlObjectId pgDefault) {
        if (preferEncodeOid(this.encodeText, oid, pgDefault)) {
            this.encodeText = oid;
        }
    }

    private void setEncodeBinary(int oid, PostgresqlObjectId pgDefault) {
        if (preferEncodeOid(this.encodeBinary, oid, pgDefault)) {
            this.encodeBinary = oid;
        }
    }

    private void setEncodeUuid(int oid) {
        if (preferEncodeOid(this.encodeUuid, oid, UUID)) {
            this.encodeUuid = oid;
        }
    }

    private void setEncodeXml(int oid) {
        if (preferEncodeOid(this.encodeXml, oid, XML)) {
            this.encodeXml = oid;
        }
    }

    private void setEncodeMoney(int oid) {
        if (preferEncodeOid(this.encodeMoney, oid, MONEY)) {
            this.encodeMoney = oid;
        }
    }

    private void setPreferredEncodeText(int oid, boolean pgCatalog) {
        if (!pgCatalog) {
            this.encodeText = oid;
        } else {
            setEncodeText(oid, VARCHAR);
        }
    }

    private void setEncodeBitAsIntForBit(int oid) {
        if (preferEncodeOid(this.encodeBitAsInt, oid, BIT)) {
            this.encodeBitAsInt = oid;
        }
    }

    private void setEncodeBooleanForBit(int oid) {
        if (preferEncodeOid(this.encodeBoolean, oid, BIT)) {
            this.encodeBoolean = oid;
        }
    }

    private void setEncodeBitAsInt(int oid) {
        if (preferEncodeOid(this.encodeBitAsInt, oid, INT2)) {
            this.encodeBitAsInt = oid;
        }
    }

    private void setEncodeBoolean(int oid) {
        if (preferEncodeOid(this.encodeBoolean, oid, INT2)) {
            this.encodeBoolean = oid;
        }
    }

    private static boolean preferEncodeOid(int current, int candidate, PostgresqlObjectId pgDefault) {
        int pgOid = pgDefault.getObjectId();
        boolean currentIsStock = current == pgOid;
        boolean candidateIsStock = isStockPgOid(candidate, pgDefault);
        if (currentIsStock && !candidateIsStock) {
            return true;
        }
        if (!currentIsStock && candidateIsStock) {
            return false;
        }
        return true;
    }

    private static boolean isStockPgOid(int oid, PostgresqlObjectId pgDefault) {
        return PostgresqlObjectId.isValid(oid) && PostgresqlObjectId.valueOf(oid) == pgDefault;
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

    boolean isDefaultNumeric(int oid, Class<?> javaType) {
        return javaType.equals(this.numericJavaTypes.get(oid));
    }

    boolean isText(int oid) {
        return this.textOids.contains(oid);
    }

    boolean isCharacter(int oid) {
        return this.characterOids.contains(oid);
    }

    boolean isBinary(int oid) {
        return this.binaryOids.contains(oid) || equalsPg(oid, BYTEA);
    }

    boolean isRowVersion(int oid) {
        return this.rowVersionOids.contains(oid);
    }

    boolean isBit(int oid) {
        return this.bitOids.contains(oid);
    }

    boolean isBoolean(int oid) {
        return this.booleanOids.contains(oid);
    }

    boolean isDefaultBoolean(int oid) {
        return oid == this.encodeBitAsInt || equalsPg(oid, BOOL);
    }

    boolean isUuid(int oid) {
        return this.uuidOids.contains(oid);
    }

    boolean isXml(int oid) {
        return this.xmlOids.contains(oid);
    }

    boolean isMoney(int oid) {
        return this.moneyOids.contains(oid);
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

    /**
     * Default OID for {@link java.time.LocalDateTime} / {@link java.time.Instant} binds.
     * Prefers Kingbase {@code datetime2} so second and fractional-second precision are retained.
     */
    int encodeLocalDateTime() {
        if (!isStockTemporalEncode(this.encodeDatetime2)) {
            return this.encodeDatetime2;
        }
        if (!isStockTemporalEncode(this.encodeDatetime)) {
            return this.encodeDatetime;
        }
        return this.encodeTimestamp;
    }

    /**
     * Default OID for {@link java.time.OffsetDateTime}/{@link java.time.ZonedDateTime} on the PG wire.
     * Prefers Kingbase {@code datetimeoffset}, then {@code datetime2}/{@code datetime}.
     */
    int encodeOffsetDateTime() {
        if (!isStockTemporalEncode(this.encodeTimestamptz)) {
            return this.encodeTimestamptz;
        }
        if (!isStockTemporalEncode(this.encodeDatetime2)) {
            return this.encodeDatetime2;
        }
        if (!isStockTemporalEncode(this.encodeDatetime)) {
            return this.encodeDatetime;
        }
        return this.encodeTimestamp;
    }

    int encodeDatetime() {
        return this.encodeDatetime;
    }

    int encodeDatetime2() {
        return this.encodeDatetime2;
    }

    /**
     * {@code true} when {@code bindOid} is the loaded Kingbase {@code datetimeoffset} encode default.
     */
    boolean isDatetimeoffsetEncodeOid(int bindOid) {
        return bindOid == this.encodeTimestamptz && !isStockTemporalEncode(this.encodeTimestamptz);
    }

    int encodeTimestamptz() {
        return this.encodeTimestamptz;
    }

    int encodeBoolean() {
        return this.encodeBoolean;
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

    int encodeUuid() {
        return this.encodeUuid;
    }

    int encodeXml() {
        return this.encodeXml;
    }

    int encodeMoney() {
        return this.encodeMoney;
    }

    int arrayOid(String typname) {
        Integer oid = this.arrayOids.get(typname.toLowerCase(Locale.ROOT));
        return oid == null ? 0 : oid;
    }

    int encodeOid(KingbaseSqlServerType type) {
        switch (type) {
            case BIT:
                return this.encodeBitAsInt;
            case BOOLEAN:
                return this.encodeBoolean;
            case DATE:
                return this.encodeDate;
            case TIME:
                return this.encodeTime;
            case DATETIME:
            case DATETIME2:
            case SMALLDATETIME:
                return encodeLocalDateTime();
            case DATETIMEOFFSET:
                return encodeOffsetDateTime();
            case CHAR:
            case VARCHAR:
            case NCHAR:
            case NVARCHAR:
            case TEXT:
            case NTEXT:
                return this.encodeText;
            case BINARY:
            case VARBINARY:
            case IMAGE:
            case ROWVERSION:
                return this.encodeBinary;
            case UNIQUEIDENTIFIER:
                return this.encodeUuid;
            case XML:
                return this.encodeXml;
            case MONEY:
                return this.encodeMoney;
            default:
                return type.getObjectId();
        }
    }

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
