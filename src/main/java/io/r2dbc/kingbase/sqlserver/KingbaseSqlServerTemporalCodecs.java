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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.r2dbc.postgresql.client.EncodedParameter;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.postgresql.util.ByteBufUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TIMETZ;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.AbstractSqlServerCodec;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.encodeText;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.formatOffsetDateTimeText;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.formatZonedDateTimeText;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.resolveBindOid;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.resolveDateBindOid;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerCodecSupport.resolveTimestampBindOid;
import static io.r2dbc.kingbase.sqlserver.KingbaseSqlServerTypeOids.equalsPg;

/**
 * Temporal codecs for Kingbase SQL Server mode over the <strong>PostgreSQL wire</strong>.
 * <p>
 * Values are decoded from PG text/binary payloads. Type OIDs come from {@link KingbaseSqlServerTypeOids}
 * ({@code pg_type}), not from the SQL Server client protocol.
 */
final class KingbaseSqlServerTemporalCodecs {

    private KingbaseSqlServerTemporalCodecs() {
    }

    static List<Codec<?>> create(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
        List<Codec<?>> list = new ArrayList<>();
        list.add(new LocalDateCodec(allocator, typeOids));
        list.add(new LocalTimeCodec(allocator, typeOids));
        list.add(new LocalDateTimeCodec(allocator, typeOids));
        list.add(new InstantCodec(allocator, typeOids));
        list.add(new OffsetDateTimeCodec(allocator, typeOids));
        list.add(new OffsetTimeCodec(allocator, typeOids));
        list.add(new ZonedDateTimeCodec(allocator, typeOids));
        list.add(new DurationCodec(allocator, typeOids));
        return list;
    }

    static final class LocalDateCodec extends AbstractSqlServerCodec<LocalDate> {

        LocalDateCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, LocalDate.class, KingbaseSqlServerType.DATE, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == LocalDate.class
                && this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isDate(dataType) || this.typeOids.isTimestamp(dataType));
        }

        @Override
        LocalDate doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends LocalDate> type) {
            String text = ByteBufUtils.decode(buffer).trim();
            if (text.contains(" ")) {
                return LocalDateTime.parse(text.replace(' ', 'T')).toLocalDate();
            }
            return LocalDate.parse(text.substring(0, Math.min(10, text.length())));
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeDate());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveDateBindOid(dataType, this.typeOids.encodeDate());
            return encodeText(this.allocator, oid, value.toString());
        }
    }

    static final class LocalTimeCodec extends AbstractSqlServerCodec<LocalTime> {

        LocalTimeCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, LocalTime.class, KingbaseSqlServerType.TIME, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == LocalTime.class
                && this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isTime(dataType) || this.typeOids.isTimestamp(dataType));
        }

        @Override
        LocalTime doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends LocalTime> type) {
            String text = ByteBufUtils.decode(buffer).trim();
            if (text.contains(" ")) {
                return LocalDateTime.parse(text.replace(' ', 'T')).toLocalTime();
            }
            return LocalTime.parse(text);
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeTime());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveBindOid(dataType, this.typeOids.encodeTime());
            return encodeText(this.allocator, oid, value.toString());
        }
    }

    static final class LocalDateTimeCodec extends AbstractSqlServerCodec<LocalDateTime> {

        LocalDateTimeCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, LocalDateTime.class, KingbaseSqlServerType.DATETIME, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == LocalDateTime.class
                && this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isTimestamp(dataType) || this.typeOids.isDate(dataType));
        }

        @Override
        LocalDateTime doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends LocalDateTime> type) {
            String text = ByteBufUtils.decode(buffer).trim().replace(' ', 'T');
            if (text.length() == 10) {
                return LocalDate.parse(text).atStartOfDay();
            }
            int plus = text.indexOf('+');
            int z = text.indexOf('Z');
            if (z > 0) {
                text = text.substring(0, z);
            } else if (plus > 10) {
                text = text.substring(0, plus);
            }
            return LocalDateTime.parse(text);
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeLocalDateTime());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            LocalDateTime ldt = (LocalDateTime) value;
            int oid = resolveTimestampBindOid(dataType, this.typeOids.encodeLocalDateTime());
            return encodeText(this.allocator, oid, ldt.toString().replace('T', ' '));
        }
    }

    static final class InstantCodec extends AbstractSqlServerCodec<Instant> {

        InstantCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, Instant.class, KingbaseSqlServerType.DATETIMEOFFSET, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == Instant.class
                && this.typeOids.canDecodeTextExtension(dataType, format, this.typeOids.isTimestamp(dataType));
        }

        @Override
        Instant doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Instant> type) {
            String text = ByteBufUtils.decode(buffer).trim().replace(' ', 'T');
            try {
                return Instant.parse(text.endsWith("Z") ? text : text + "Z");
            } catch (Exception ex) {
                return LocalDateTime.parse(text.replace("Z", "")).toInstant(ZoneOffset.UTC);
            }
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeLocalDateTime());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveTimestampBindOid(dataType, this.typeOids.encodeLocalDateTime());
            return encodeText(this.allocator, oid, value.toString());
        }
    }

    static final class OffsetDateTimeCodec extends AbstractSqlServerCodec<OffsetDateTime> {

        OffsetDateTimeCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, OffsetDateTime.class, KingbaseSqlServerType.DATETIMEOFFSET, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == OffsetDateTime.class
                && this.typeOids.canDecodeTextExtension(dataType, format, this.typeOids.isTimestamp(dataType));
        }

        @Override
        OffsetDateTime doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends OffsetDateTime> type) {
            String text = ByteBufUtils.decode(buffer).trim().replace(' ', 'T');
            try {
                return OffsetDateTime.parse(text);
            } catch (Exception ex) {
                return LocalDateTime.parse(text).atOffset(ZoneOffset.UTC);
            }
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeOffsetDateTime());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            OffsetDateTime odt = (OffsetDateTime) value;
            int oid = resolveTimestampBindOid(dataType, this.typeOids.encodeOffsetDateTime());
            String text = formatOffsetDateTimeText(odt, oid, this.typeOids);
            return encodeText(this.allocator, oid, text);
        }
    }

    /**
     * {@link OffsetTime} over PG wire. Kingbase SQL Server-mode {@code time} may use a custom OID;
     * payloads remain PG text (no SQL Server binary TIME packet).
     */
    static final class OffsetTimeCodec extends AbstractSqlServerCodec<OffsetTime> {

        OffsetTimeCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, OffsetTime.class, KingbaseSqlServerType.TIME, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == OffsetTime.class
                && this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isTime(dataType) || this.typeOids.isTimestamp(dataType));
        }

        @Override
        OffsetTime doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends OffsetTime> type) {
            String text = ByteBufUtils.decode(buffer).trim();
            if (text.contains(" ")) {
                text = text.substring(text.indexOf(' ') + 1);
            }
            try {
                return OffsetTime.parse(text);
            } catch (Exception ignored) {
                return OffsetTime.of(LocalTime.parse(text), ZoneOffset.UTC);
            }
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeTime());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            OffsetTime ot = (OffsetTime) value;
            int oid = resolveBindOid(dataType, this.typeOids.encodeTime());
            if (equalsPg(oid, TIMETZ) || equalsPg(dataType, TIMETZ)) {
                return encodeText(this.allocator, oid, ot.toString());
            }
            // r2dbc-mssql: convert to server-zone local time; default UTC on PG wire
            LocalTime local = ot.withOffsetSameInstant(ZoneOffset.UTC).toLocalTime();
            return encodeText(this.allocator, oid, local.toString());
        }
    }

    static final class ZonedDateTimeCodec extends AbstractSqlServerCodec<ZonedDateTime> {

        ZonedDateTimeCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, ZonedDateTime.class, KingbaseSqlServerType.DATETIMEOFFSET, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == ZonedDateTime.class
                && this.typeOids.canDecodeTextExtension(dataType, format, this.typeOids.isTimestamp(dataType));
        }

        @Override
        ZonedDateTime doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends ZonedDateTime> type) {
            String text = ByteBufUtils.decode(buffer).trim().replace(' ', 'T');
            try {
                return ZonedDateTime.parse(text);
            } catch (Exception ex) {
                return LocalDateTime.parse(text).atZone(ZoneOffset.UTC);
            }
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeOffsetDateTime());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            ZonedDateTime zdt = (ZonedDateTime) value;
            int oid = resolveTimestampBindOid(dataType, this.typeOids.encodeOffsetDateTime());
            String text = formatZonedDateTimeText(zdt, oid, this.typeOids);
            return encodeText(this.allocator, oid, text);
        }
    }

    static final class DurationCodec extends AbstractSqlServerCodec<Duration> {

        DurationCodec(ByteBufAllocator allocator, KingbaseSqlServerTypeOids typeOids) {
            super(allocator, Duration.class, KingbaseSqlServerType.TIME, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == Duration.class
                && this.typeOids.canDecodeTextExtension(dataType, format, this.typeOids.isTime(dataType));
        }

        @Override
        Duration doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Duration> type) {
            String text = ByteBufUtils.decode(buffer).trim();
            if (text.startsWith("P") || text.contains("day") || text.contains(":")) {
                if (text.contains(":") && !text.startsWith("P")) {
                    LocalTime time = LocalTime.parse(text.length() > 8 && text.charAt(2) != ':'
                        ? text.substring(text.length() - 8) : text);
                    return Duration.ofNanos(time.toNanoOfDay());
                }
                try {
                    return Duration.parse(text);
                } catch (Exception ignored) {
                    // fall through
                }
            }
            LocalTime time = LocalTime.parse(text);
            return Duration.ofNanos(time.toNanoOfDay());
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeTime());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            Duration duration = (Duration) value;
            long seconds = duration.getSeconds();
            long abs = Math.abs(seconds);
            String sign = seconds < 0 ? "-" : "";
            String text = String.format("%s%d:%02d:%02d", sign, abs / 3600, (abs % 3600) / 60, abs % 60);
            int oid = resolveBindOid(dataType, this.typeOids.encodeTime());
            return encodeText(this.allocator, oid, text);
        }
    }

}
