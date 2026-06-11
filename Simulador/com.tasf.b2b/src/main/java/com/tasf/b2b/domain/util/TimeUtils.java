package com.tasf.b2b.domain.util;

import java.time.*;
import java.time.temporal.ChronoUnit;

public class TimeUtils {

    // De Local a UTC (para guardar en el STNode)
    public static Instant localToUtc(LocalDateTime localDateTime, int gmtOffset) {
        if (localDateTime == null) return null;
        return localDateTime.atOffset(ZoneOffset.ofHours(gmtOffset))
                .toInstant();
    }

    // De UTC a Local (para mostrar en consola o tickets)
    public static LocalDateTime utcToLocal(Instant utcTime, int gmtOffset) {
        if (utcTime == null) return null;
        return LocalDateTime.ofInstant(utcTime, ZoneOffset.ofHours(gmtOffset));
    }

    // De un Instant suma el LocalTime de un local
    // El mismo día del dateBase en UTC-0
    public static Instant combineDateAndLocalTime(Instant dateBase, LocalTime localTime, int gmtOffset) {
        if (dateBase == null || localTime == null) return null;
        // Se corta al inicio del día en UTC-0
        Instant startOfDay = dateBase.truncatedTo(ChronoUnit.DAYS);

        // Convertir localTime para que funcione en utc-0
        OffsetDateTime anyDay = OffsetDateTime.of(LocalDate.of(1970, 1, 1), localTime, ZoneOffset.ofHours(gmtOffset));
        LocalTime localTimeUtc0 = anyDay.toInstant().atOffset(ZoneOffset.UTC).toLocalTime();
        long nanosToAdd = localTimeUtc0.toNanoOfDay();

        //Sumarle el localTime adaptado al inicio del día
        return startOfDay.plusNanos(nanosToAdd);
    }
}
