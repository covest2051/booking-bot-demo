package dev.bookingbot.booking;

import dev.bookingbot.salon.WorkHours;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Чистый расчёт свободного времени без БД — его удобно проверять юнит-тестами. */
public final class SlotCalculator {

    private SlotCalculator() {
    }

    public record Interval(Instant start, Instant end) {

        boolean overlaps(Instant otherStart, Instant otherEnd) {
            return otherStart.isBefore(end) && start.isBefore(otherEnd);
        }
    }

    /**
     * Начала, на которые можно записаться: сетка с шагом step внутри рабочих часов, услуга целиком
     * помещается до конца дня, не пересекается с занятыми интервалами и не раньше notBefore.
     */
    public static List<LocalTime> freeStarts(LocalDate date, WorkHours hours, Duration duration, Duration step,
                                             List<Interval> busy, Instant notBefore, ZoneId zone) {
        List<LocalTime> result = new ArrayList<>();
        LocalDateTime lastStart = date.atTime(hours.to()).minus(duration);
        for (LocalDateTime t = date.atTime(hours.from()); !t.isAfter(lastStart); t = t.plus(step)) {
            Instant start = t.atZone(zone).toInstant();
            Instant end = start.plus(duration);
            if (start.isBefore(notBefore)) {
                continue;
            }
            if (busy.stream().noneMatch(interval -> interval.overlaps(start, end))) {
                result.add(t.toLocalTime());
            }
        }
        return result;
    }
}
