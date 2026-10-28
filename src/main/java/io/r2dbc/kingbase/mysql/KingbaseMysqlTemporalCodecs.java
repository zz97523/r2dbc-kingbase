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
import java.time.Year;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TIMETZ;
import static io.r2dbc.kingbase.mysql.KingbaseMysqlCodecSupport.AbstractMysqlCodec;
import static io.r2dbc.kingbase.mysql.KingbaseMysqlCodecSupport.decodeNumber;
import static io.r2dbc.kingbase.mysql.KingbaseMysqlCodecSupport.encodeText;
import static io.r2dbc.kingbase.mysql.KingbaseMysqlCodecSupport.resolveBindOid;
import static io.r2dbc.kingbase.mysql.KingbaseMysqlTypeOids.equalsPg;

/**
 * Temporal codecs for Kingbase MySQL mode over the <strong>PostgreSQL wire</strong>.
 * <p>
 * Values are decoded from PG text/binary payloads. Type OIDs come from {@link KingbaseMysqlTypeOids}
 * ({@code pg_type}), not from the MySQL client protocol.
 */
final class KingbaseMysqlTemporalCodecs {

    private static final Pattern DURATION_TEXT = Pattern.compile(
        "^([+-])?(?:(\\d+)\\s+days?\\s+)?(\\d+):([0-5]\\d):([0-5]\\d)(?:\\.(\\d{1,9}))?$",
        Pattern.CASE_INSENSITIVE);

    private KingbaseMysqlTemporalCodecs() {
    }

    static List<Codec<?>> create(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
        List<Codec<?>> list = new ArrayList<>();
        list.add(new LocalDateCodec(allocator, typeOids));
        list.add(new LocalTimeCodec(allocator, typeOids));
        list.add(new LocalDateTimeCodec(allocator, typeOids));
        list.add(new InstantCodec(allocator, typeOids));
        list.add(new OffsetDateTimeCodec(allocator, typeOids));
        list.add(new OffsetTimeCodec(allocator, typeOids));
        list.add(new ZonedDateTimeCodec(allocator, typeOids));
        list.add(new DurationCodec(allocator, typeOids));
        list.add(new YearCodec(allocator, typeOids));
        return list;
    }

    static final class LocalDateCodec extends AbstractMysqlCodec<LocalDate> {

        LocalDateCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, LocalDate.class, KingbaseMysqlType.DATE, typeOids);
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
            int oid = resolveBindOid(dataType, this.typeOids.encodeDate());
            return encodeText(this.allocator, oid, value.toString());
        }
    }

    static final class LocalTimeCodec extends AbstractMysqlCodec<LocalTime> {

        LocalTimeCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, LocalTime.class, KingbaseMysqlType.TIME, typeOids);
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

    static final class LocalDateTimeCodec extends AbstractMysqlCodec<LocalDateTime> {

        LocalDateTimeCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, LocalDateTime.class, KingbaseMysqlType.DATETIME, typeOids);
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
            return encode(value, this.typeOids.encodeTimestamp());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            LocalDateTime ldt = (LocalDateTime) value;
            int oid = resolveBindOid(dataType, this.typeOids.encodeTimestamp());
            return encodeText(this.allocator, oid, ldt.toString().replace('T', ' '));
        }
    }

    static final class InstantCodec extends AbstractMysqlCodec<Instant> {

        InstantCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, Instant.class, KingbaseMysqlType.TIMESTAMP, typeOids);
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
            return encode(value, this.typeOids.encodeTimestamptz());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveBindOid(dataType, this.typeOids.encodeTimestamptz());
            return encodeText(this.allocator, oid, value.toString());
        }
    }

    static final class OffsetDateTimeCodec extends AbstractMysqlCodec<OffsetDateTime> {

        OffsetDateTimeCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, OffsetDateTime.class, KingbaseMysqlType.TIMESTAMP, typeOids);
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
            return encode(value, this.typeOids.encodeTimestamptz());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            // r2dbc-mysql converts to server-zone LocalDateTime; on PG wire keep ISO with offset
            int oid = resolveBindOid(dataType, this.typeOids.encodeTimestamptz());
            return encodeText(this.allocator, oid, value.toString());
        }
    }

    /**
     * {@link OffsetTime} over PG wire. Kingbase MySQL-mode {@code time} may use a custom OID;
     * payloads remain PG text (no MySQL binary TIME packet).
     */
    static final class OffsetTimeCodec extends AbstractMysqlCodec<OffsetTime> {

        OffsetTimeCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, OffsetTime.class, KingbaseMysqlType.TIME, typeOids);
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
            // r2dbc-mysql: convert to server-zone local time; default UTC on PG wire
            LocalTime local = ot.withOffsetSameInstant(ZoneOffset.UTC).toLocalTime();
            return encodeText(this.allocator, oid, local.toString());
        }
    }

    static final class ZonedDateTimeCodec extends AbstractMysqlCodec<ZonedDateTime> {

        ZonedDateTimeCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, ZonedDateTime.class, KingbaseMysqlType.TIMESTAMP, typeOids);
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
            return encode(value, this.typeOids.encodeTimestamptz());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveBindOid(dataType, this.typeOids.encodeTimestamptz());
            return encodeText(this.allocator, oid, value.toString());
        }
    }

    static final class DurationCodec extends AbstractMysqlCodec<Duration> {

        DurationCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, Duration.class, KingbaseMysqlType.TIME, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == Duration.class
                && this.typeOids.canDecodeTextExtension(dataType, format, this.typeOids.isTime(dataType));
        }

        @Override
        Duration doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Duration> type) {
            return parseDuration(ByteBufUtils.decode(buffer).trim());
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeTime());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveBindOid(dataType, this.typeOids.encodeTime());
            return encodeText(this.allocator, oid, formatDuration((Duration) value));
        }
    }

    private static Duration parseDuration(String text) {
        try {
            return Duration.parse(text);
        } catch (RuntimeException ignored) {
            // MySQL TIME and PostgreSQL interval text are handled below.
        }

        Matcher matcher = DURATION_TEXT.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Cannot decode Duration from '" + text + "'");
        }

        long days = matcher.group(2) == null ? 0 : Long.parseLong(matcher.group(2));
        long hours = Long.parseLong(matcher.group(3));
        long minutes = Long.parseLong(matcher.group(4));
        long seconds = Long.parseLong(matcher.group(5));
        long totalSeconds = Math.addExact(Math.multiplyExact(days, 86_400L),
            Math.addExact(Math.multiplyExact(hours, 3_600L),
                Math.addExact(Math.multiplyExact(minutes, 60L), seconds)));

        String fraction = matcher.group(6);
        int nanos = fraction == null ? 0
            : Integer.parseInt((fraction + "000000000").substring(0, 9));
        Duration duration = Duration.ofSeconds(totalSeconds, nanos);
        return "-".equals(matcher.group(1)) ? duration.negated() : duration;
    }

    private static String formatDuration(Duration duration) {
        boolean negative = duration.isNegative();
        Duration absolute = duration.abs();
        long seconds = absolute.getSeconds();
        String text = String.format(Locale.ROOT, "%s%d:%02d:%02d",
            negative ? "-" : "", seconds / 3_600, (seconds % 3_600) / 60, seconds % 60);
        if (absolute.getNano() == 0) {
            return text;
        }

        String fraction = String.format(Locale.ROOT, "%09d", absolute.getNano());
        int end = fraction.length();
        while (end > 0 && fraction.charAt(end - 1) == '0') {
            end--;
        }
        return text + "." + fraction.substring(0, end);
    }

    static final class YearCodec extends AbstractMysqlCodec<Year> {

        YearCodec(ByteBufAllocator allocator, KingbaseMysqlTypeOids typeOids) {
            super(allocator, Year.class, KingbaseMysqlType.YEAR, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == Year.class
                && this.typeOids.canDecodeTextExtension(dataType, format, this.typeOids.isYear(dataType));
        }

        @Override
        Year doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Year> type) {
            String text = ByteBufUtils.decode(buffer).trim();
            // YEAR may arrive as date-like "2026-01-01" (JDBC maps YEAR→Date) or bare "2026"
            if (text.length() >= 4 && Character.isDigit(text.charAt(0))) {
                try {
                    return Year.of(Integer.parseInt(text.substring(0, 4)));
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
            try {
                return Year.of(new java.math.BigDecimal(text).intValue());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Cannot decode Year from '" + text + "'", ex);
            }
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeYear());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int year = ((Year) value).getValue();
            int oid = resolveBindOid(dataType, this.typeOids.encodeYear());
            return encodeText(this.allocator, oid, Integer.toString(year));
        }
    }

}
