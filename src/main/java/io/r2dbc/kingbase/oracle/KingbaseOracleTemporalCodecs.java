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
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.AbstractOracleCodec;
import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.encodeText;
import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.nullParameter;
import static io.r2dbc.kingbase.oracle.KingbaseOracleCodecSupport.resolveBindOid;
import static io.r2dbc.kingbase.oracle.KingbaseOracleTypeOids.equalsPg;
import static io.r2dbc.postgresql.codec.PostgresqlObjectId.TIMETZ;

/**
 * Temporal codecs for Kingbase Oracle mode.
 * <p>
 * Oracle {@code DATE} includes time-of-day → default Java type is {@link LocalDateTime}.
 */
final class KingbaseOracleTemporalCodecs {

    private static final DateTimeFormatter SHORT_OFFSET_DATE_TIME_FORMATTER =
        new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .appendOffset("+HH:mm", "Z")
            .toFormatter();

    private static final DateTimeFormatter FULL_OFFSET_DATE_TIME_FORMATTER =
        new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .appendOffset("+HH:MM:ss", "Z")
            .toFormatter();

    private static final DateTimeFormatter SHORT_OFFSET_TIME_FORMATTER =
        new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .appendOffset("+HH:mm", "Z")
            .toFormatter();

    private static final DateTimeFormatter FULL_OFFSET_TIME_FORMATTER =
        new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .appendOffset("+HH:MM:ss", "Z")
            .toFormatter();

    private static final Pattern INTERVAL_DS = Pattern.compile(
        "(?:([+-]?\\d+)\\s+days?\\s*)?(?:([+-]?\\d+):(\\d+):(\\d+)(?:\\.(\\d+))?)?",
        Pattern.CASE_INSENSITIVE);

    // e.g. "1 day 2 hours 3 minutes 4 seconds"
    private static final Pattern INTERVAL_DS_WORDS = Pattern.compile(
        "(?:([+-]?\\d+)\\s+days?\\s*)?"
            + "(?:([+-]?\\d+)\\s+hours?\\s*)?"
            + "(?:([+-]?\\d+)\\s+minutes?\\s*)?"
            + "(?:([+-]?\\d+)\\s+seconds?\\s*)?",
        Pattern.CASE_INSENSITIVE);

    // e.g. "+01 02:03:04.000000"
    private static final Pattern INTERVAL_DS_COMPACT = Pattern.compile(
        "([+-]?\\d+)\\s+([+-]?\\d+):(\\d+):(\\d+)(?:\\.(\\d+))?",
        Pattern.CASE_INSENSITIVE);

    // Kingbase yminterval: "1-2" or "-1-2"
    private static final Pattern INTERVAL_YM_COMPACT = Pattern.compile(
        "^([+-])?(\\d+)-(\\d+)$");

    private KingbaseOracleTemporalCodecs() {
    }

    private static long fractionToNanos(String fraction) {
        if (fraction == null || fraction.isEmpty()) {
            return 0;
        }
        String nanos = (fraction + "000000000").substring(0, 9);
        return Long.parseLong(nanos);
    }

    /**
     * Parse PG/Kingbase/Oracle text dates and timestamps.
     */
    static LocalDateTime parseLocalDateTime(String raw) {
        String text = raw.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Cannot decode LocalDateTime from empty text");
        }
        if (text.endsWith("Z") || text.endsWith("z")) {
            text = text.substring(0, text.length() - 1).trim();
        } else {
            int len = text.length();
            if (len > 11) {
                int plus = text.indexOf('+', 10);
                if (plus > 10) {
                    text = text.substring(0, plus).trim();
                } else {
                    int colonTime = text.indexOf(':');
                    if (colonTime > 0) {
                        int minusTz = text.indexOf('-', colonTime);
                        if (minusTz > colonTime) {
                            text = text.substring(0, minusTz).trim();
                        }
                    }
                }
            }
        }

        // Oracle-style: 18-JUL-26 / 18-JUL-2026
        if (text.length() >= 9 && text.charAt(2) == '-' && Character.isLetter(text.charAt(3))) {
            return parseOracleDateTime(text);
        }

        text = text.replace(' ', 'T');
        if (text.length() == 10) {
            return LocalDate.parse(text).atStartOfDay();
        }
        if (text.length() == 16 && text.charAt(13) == ':') {
            text = text + ":00";
        }
        try {
            return LocalDateTime.parse(text);
        } catch (Exception ex) {
            if (text.length() >= 10 && text.charAt(4) == '-') {
                return LocalDate.parse(text.substring(0, 10)).atStartOfDay();
            }
            throw new IllegalArgumentException("Cannot decode LocalDateTime from '" + raw + "'", ex);
        }
    }

    static OffsetDateTime parseOffsetDateTime(String raw) {
        String text = raw.trim().replace(' ', 'T');
        if (text.endsWith("Z") || text.endsWith("z") || text.indexOf('+', 10) > 10
            || text.indexOf('-', 10) > 10) {
            try {
                return OffsetDateTime.parse(text, SHORT_OFFSET_DATE_TIME_FORMATTER);
            } catch (DateTimeParseException ignored) {
                return OffsetDateTime.parse(text, FULL_OFFSET_DATE_TIME_FORMATTER);
            }
        }
        return LocalDateTime.parse(text).atOffset(ZoneOffset.UTC);
    }

    static OffsetTime parseOffsetTime(String raw) {
        String text = raw.trim();
        int dateTimeSeparator = Math.max(text.lastIndexOf(' '), text.lastIndexOf('T'));
        if (dateTimeSeparator >= 0) {
            text = text.substring(dateTimeSeparator + 1);
        }
        if (text.endsWith("Z") || text.endsWith("z") || text.indexOf('+') > 0
            || text.indexOf('-') > 0) {
            try {
                return OffsetTime.parse(text, SHORT_OFFSET_TIME_FORMATTER);
            } catch (DateTimeParseException ignored) {
                return OffsetTime.parse(text, FULL_OFFSET_TIME_FORMATTER);
            }
        }
        return LocalTime.parse(text).atOffset(ZoneOffset.UTC);
    }

    private static LocalDateTime parseOracleDateTime(String text) {
        // 18-JUL-26 or 18-JUL-2026[, HH:mm[:ss]]
        String datePart = text;
        String timePart = null;
        int sp = text.indexOf(' ');
        if (sp > 0) {
            datePart = text.substring(0, sp);
            timePart = text.substring(sp + 1).trim();
        }
        String[] bits = datePart.split("-");
        if (bits.length != 3) {
            throw new IllegalArgumentException("Cannot parse Oracle date '" + text + "'");
        }
        int day = Integer.parseInt(bits[0]);
        int month = oracleMonth(bits[1]);
        int year = Integer.parseInt(bits[2]);
        if (year < 100) {
            // Oracle RR: 00-49 → 2000-2049, 50-99 → 1950-1999
            year += (year <= 49) ? 2000 : 1900;
        }
        LocalDate date = LocalDate.of(year, month, day);
        if (timePart == null || timePart.isEmpty()) {
            return date.atStartOfDay();
        }
        if (timePart.length() == 5) {
            timePart = timePart + ":00";
        }
        return LocalDateTime.parse(date + "T" + timePart);
    }

    private static int oracleMonth(String mon) {
        switch (mon.toUpperCase(java.util.Locale.ROOT)) {
            case "JAN":
                return 1;
            case "FEB":
                return 2;
            case "MAR":
                return 3;
            case "APR":
                return 4;
            case "MAY":
                return 5;
            case "JUN":
                return 6;
            case "JUL":
                return 7;
            case "AUG":
                return 8;
            case "SEP":
                return 9;
            case "OCT":
                return 10;
            case "NOV":
                return 11;
            case "DEC":
                return 12;
            default:
                throw new IllegalArgumentException("Unknown Oracle month: " + mon);
        }
    }

    static List<Codec<?>> create(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
        List<Codec<?>> list = new ArrayList<>();
        list.add(new LocalDateTimeCodec(allocator, typeOids));
        list.add(new TimestampWithLocalTimeZoneCodec(allocator, typeOids));
        list.add(new LocalDateCodec(allocator, typeOids));
        list.add(new LocalTimeCodec(allocator, typeOids));
        list.add(new OffsetTimeCodec(allocator, typeOids));
        list.add(new InstantCodec(allocator, typeOids));
        list.add(new OffsetDateTimeCodec(allocator, typeOids));
        list.add(new ZonedDateTimeCodec(allocator, typeOids));
        list.add(new DurationCodec(allocator, typeOids));
        list.add(new PeriodCodec(allocator, typeOids));
        return list;
    }

    static final class LocalDateTimeCodec extends AbstractOracleCodec<LocalDateTime> {

        LocalDateTimeCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            // Prefer TIMESTAMP OID for binds — Kingbase "date" may be date-only and drop time-of-day.
            super(allocator, LocalDateTime.class, KingbaseOracleType.TIMESTAMP, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == LocalDateTime.class
                && this.typeOids.canDecodeTextExtension(dataType, format,
                (this.typeOids.isDate(dataType) || this.typeOids.isTimestamp(dataType))
                    && !this.typeOids.isTimestampltz(dataType));
        }

        @Override
        LocalDateTime doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends LocalDateTime> type) {
            return parseLocalDateTime(ByteBufUtils.decode(buffer).trim());
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeTimestamp());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            LocalDateTime ldt = (LocalDateTime) value;
            int oid = resolveBindOid(dataType, this.typeOids.encodeTimestamp());
            // Always include seconds so servers that normalize "15:30" → "15:30:00" stay consistent
            String text = String.format("%04d-%02d-%02d %02d:%02d:%02d",
                ldt.getYear(), ldt.getMonthValue(), ldt.getDayOfMonth(),
                ldt.getHour(), ldt.getMinute(), ldt.getSecond());
            if (ldt.getNano() > 0) {
                text = text + String.format(".%09d", ldt.getNano()).replaceAll("0+$", "");
            }
            return encodeText(this.allocator, oid, text);
        }
    }

    /**
     * Oracle {@code TIMESTAMP WITH LOCAL TIME ZONE} → {@link LocalDateTime} (oracle-r2dbc habit).
     */
    static final class TimestampWithLocalTimeZoneCodec extends AbstractOracleCodec<LocalDateTime> {

        TimestampWithLocalTimeZoneCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, LocalDateTime.class, KingbaseOracleType.TIMESTAMP_LTZ, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == LocalDateTime.class
                && this.typeOids.canDecodeTextExtension(dataType, format, this.typeOids.isTimestampltz(dataType));
        }

        @Override
        LocalDateTime doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends LocalDateTime> type) {
            return parseLocalDateTime(ByteBufUtils.decode(buffer).trim());
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeTimestampltz());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            LocalDateTime ldt = (LocalDateTime) value;
            int oid = resolveBindOid(dataType, this.typeOids.encodeTimestampltz());
            String text = String.format("%04d-%02d-%02d %02d:%02d:%02d",
                ldt.getYear(), ldt.getMonthValue(), ldt.getDayOfMonth(),
                ldt.getHour(), ldt.getMinute(), ldt.getSecond());
            if (ldt.getNano() > 0) {
                text = text + String.format(".%09d", ldt.getNano()).replaceAll("0+$", "");
            }
            return encodeText(this.allocator, oid, text);
        }
    }

    static final class LocalDateCodec extends AbstractOracleCodec<LocalDate> {

        LocalDateCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, LocalDate.class, KingbaseOracleType.DATE, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == LocalDate.class
                && this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isDate(dataType) || this.typeOids.isTimestamp(dataType));
        }

        @Override
        LocalDate doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends LocalDate> type) {
            return parseLocalDateTime(ByteBufUtils.decode(buffer).trim()).toLocalDate();
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeDate());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveBindOid(dataType, this.typeOids.encodeDate());
            // Date-only text — avoid "yyyy-MM-dd 00:00:00" on date-only Kingbase types
            return encodeText(this.allocator, oid, value.toString());
        }
    }

    static final class LocalTimeCodec extends AbstractOracleCodec<LocalTime> {

        LocalTimeCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, LocalTime.class, KingbaseOracleType.TIME, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == LocalTime.class
                && this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isTime(dataType) || this.typeOids.isTimestamp(dataType)
                    || this.typeOids.isDate(dataType));
        }

        @Override
        LocalTime doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends LocalTime> type) {
            String text = ByteBufUtils.decode(buffer).trim();
            if (text.contains(" ")) {
                return parseLocalDateTime(text).toLocalTime();
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

    /**
     * {@link OffsetTime} over PG wire ({@code TIME WITH TIME ZONE} / {@code timetz}).
     */
    static final class OffsetTimeCodec extends AbstractOracleCodec<OffsetTime> {

        OffsetTimeCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, OffsetTime.class, KingbaseOracleType.TIME, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == OffsetTime.class
                && this.typeOids.canDecodeTextExtension(dataType, format,
                this.typeOids.isTime(dataType) || this.typeOids.isTimestamp(dataType));
        }

        @Override
        OffsetTime doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends OffsetTime> type) {
            return parseOffsetTime(ByteBufUtils.decode(buffer));
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
            LocalTime local = ot.withOffsetSameInstant(ZoneOffset.UTC).toLocalTime();
            return encodeText(this.allocator, oid, local.toString());
        }
    }

    static final class InstantCodec extends AbstractOracleCodec<Instant> {

        InstantCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, Instant.class, KingbaseOracleType.TIMESTAMPTZ, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == Instant.class
                && this.typeOids.canDecodeTextExtension(dataType, format, this.typeOids.isTimestamp(dataType));
        }

        @Override
        Instant doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Instant> type) {
            return parseOffsetDateTime(ByteBufUtils.decode(buffer)).toInstant();
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeTimestamptz());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveBindOid(dataType, this.typeOids.encodeTimestamptz());
            return encodeText(this.allocator, oid, ((Instant) value).toString());
        }
    }

    static final class OffsetDateTimeCodec extends AbstractOracleCodec<OffsetDateTime> {

        OffsetDateTimeCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, OffsetDateTime.class, KingbaseOracleType.TIMESTAMPTZ, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == OffsetDateTime.class
                && this.typeOids.canDecodeTextExtension(dataType, format, this.typeOids.isTimestamp(dataType));
        }

        @Override
        OffsetDateTime doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends OffsetDateTime> type) {
            return parseOffsetDateTime(ByteBufUtils.decode(buffer));
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

    static final class ZonedDateTimeCodec extends AbstractOracleCodec<ZonedDateTime> {

        ZonedDateTimeCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, ZonedDateTime.class, KingbaseOracleType.TIMESTAMPTZ, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            return type == ZonedDateTime.class
                && this.typeOids.canDecodeTextExtension(dataType, format, this.typeOids.isTimestamp(dataType));
        }

        @Override
        ZonedDateTime doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends ZonedDateTime> type) {
            return parseOffsetDateTime(ByteBufUtils.decode(buffer)).toZonedDateTime();
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeTimestamptz());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            int oid = resolveBindOid(dataType, this.typeOids.encodeTimestamptz());
            return encodeText(this.allocator, oid, ((ZonedDateTime) value).toOffsetDateTime().toString());
        }
    }

    static final class DurationCodec extends AbstractOracleCodec<Duration> {

        DurationCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, Duration.class, KingbaseOracleType.INTERVAL_DS, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            if (type == Object.class) {
                return this.typeOids.isIntervalDs(dataType);
            }
            return type == Duration.class
                && this.typeOids.canDecodeTextExtension(dataType, format, this.typeOids.isInterval(dataType));
        }

        @Override
        Duration doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Duration> type) {
            String text = ByteBufUtils.decode(buffer).trim();
            try {
                return Duration.parse(text);
            } catch (Exception ignored) {
                // fall through
            }
            Matcher m = INTERVAL_DS.matcher(text);
            if (m.matches()
                && (m.group(1) != null || m.group(2) != null || m.group(3) != null || m.group(4) != null)) {
                long days = m.group(1) != null ? Long.parseLong(m.group(1)) : 0;
                long hours = m.group(2) != null ? Long.parseLong(m.group(2)) : 0;
                long minutes = m.group(3) != null ? Long.parseLong(m.group(3)) : 0;
                long seconds = m.group(4) != null ? Long.parseLong(m.group(4)) : 0;
                long nanos = fractionToNanos(m.group(5));
                return Duration.ofDays(days).plusHours(hours).plusMinutes(minutes).plusSeconds(seconds).plusNanos(nanos);
            }

            Matcher mw = INTERVAL_DS_WORDS.matcher(text);
            if (mw.matches()
                && (mw.group(1) != null || mw.group(2) != null || mw.group(3) != null || mw.group(4) != null)) {
                long days = mw.group(1) != null ? Long.parseLong(mw.group(1)) : 0;
                long hours = mw.group(2) != null ? Long.parseLong(mw.group(2)) : 0;
                long minutes = mw.group(3) != null ? Long.parseLong(mw.group(3)) : 0;
                long seconds = mw.group(4) != null ? Long.parseLong(mw.group(4)) : 0;
                return Duration.ofDays(days).plusHours(hours).plusMinutes(minutes).plusSeconds(seconds);
            }

            // Kingbase intervalds: "+01 02:03:04.000000"
            Matcher mc = INTERVAL_DS_COMPACT.matcher(text);
            if (mc.matches()) {
                long days = Long.parseLong(mc.group(1));
                long hours = Long.parseLong(mc.group(2));
                long minutes = Long.parseLong(mc.group(3));
                long seconds = Long.parseLong(mc.group(4));
                long nanos = fractionToNanos(mc.group(5));
                Duration d = Duration.ofDays(Math.abs(days))
                    .plusHours(Math.abs(hours))
                    .plusMinutes(minutes)
                    .plusSeconds(seconds)
                    .plusNanos(nanos);
                if (days < 0 || hours < 0 || text.startsWith("-")) {
                    d = d.negated();
                }
                return d;
            }

            throw new IllegalArgumentException("Cannot decode Duration from '" + text + "'");
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeIntervalDs());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            Duration d = (Duration) value;
            int oid = resolveBindOid(dataType, this.typeOids.encodeIntervalDs());
            return encodeText(this.allocator, oid, formatInterval(d));
        }

        @Override
        public EncodedParameter encodeNull() {
            return nullParameter(this.typeOids.encodeIntervalDs());
        }

        static String formatInterval(Duration value) {
            boolean negative = value.isNegative();
            Duration absolute = negative ? value.negated() : value;
            long days = absolute.toDays();
            long seconds = absolute.minusDays(days).getSeconds();
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long remainder = seconds % 60;
            int nanos = absolute.getNano();
            String fraction = nanos == 0 ? "" : String.format(java.util.Locale.ROOT, ".%09d", nanos)
                .replaceFirst("0+$", "");
            return (negative ? "-" : "+") + days + " "
                + String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder)
                + fraction;
        }
    }

    static final class PeriodCodec extends AbstractOracleCodec<Period> {

        PeriodCodec(ByteBufAllocator allocator, KingbaseOracleTypeOids typeOids) {
            super(allocator, Period.class, KingbaseOracleType.INTERVAL_YM, typeOids);
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            if (type == Object.class) {
                return this.typeOids.isIntervalYm(dataType);
            }
            return type == Period.class
                && this.typeOids.canDecodeTextExtension(dataType, format, this.typeOids.isInterval(dataType));
        }

        @Override
        Period doDecode(ByteBuf buffer, int dataType, Format format, Class<? extends Period> type) {
            String text = ByteBufUtils.decode(buffer).trim();
            try {
                return Period.parse(text);
            } catch (Exception ignored) {
                // fall through
            }

            // Kingbase yminterval compact: "1-2", "-1-2"
            Matcher compact = INTERVAL_YM_COMPACT.matcher(text);
            if (compact.matches()) {
                int years = Integer.parseInt(compact.group(2));
                int months = Integer.parseInt(compact.group(3));
                if ("-".equals(compact.group(1))) {
                    years = -years;
                    months = -months;
                }
                return Period.of(years, months, 0);
            }

            int years = 0;
            int months = 0;
            Matcher y = Pattern.compile("([+-]?\\d+)\\s+years?", Pattern.CASE_INSENSITIVE).matcher(text);
            if (y.find()) {
                years = Integer.parseInt(y.group(1));
            }
            Matcher mo = Pattern.compile("([+-]?\\d+)\\s+mons?", Pattern.CASE_INSENSITIVE).matcher(text);
            if (mo.find()) {
                months = Integer.parseInt(mo.group(1));
            }
            if (years != 0 || months != 0) {
                return Period.of(years, months, 0);
            }
            throw new IllegalArgumentException("Cannot decode Period from '" + text + "'");
        }

        @Override
        public EncodedParameter encode(Object value) {
            return encode(value, this.typeOids.encodeIntervalYm());
        }

        @Override
        public EncodedParameter encode(Object value, int dataType) {
            Period p = (Period) value;
            if (p.getDays() != 0) {
                throw new IllegalArgumentException("INTERVAL YEAR TO MONTH requires a Period with zero days");
            }
            int oid = resolveBindOid(dataType, this.typeOids.encodeIntervalYm());
            // Kingbase yminterval wire format: "years-months"
            long totalMonths = p.toTotalMonths();
            long absoluteMonths = Math.abs(totalMonths);
            String payload = (totalMonths < 0 ? "-" : "")
                + (absoluteMonths / 12) + "-" + (absoluteMonths % 12);
            return encodeText(this.allocator, oid, payload);
        }

        @Override
        public EncodedParameter encodeNull() {
            return nullParameter(this.typeOids.encodeIntervalYm());
        }
    }

}
