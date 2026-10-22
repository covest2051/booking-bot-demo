package dev.bookingbot.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.bookingbot.booking.SlotCalculator.Interval;
import dev.bookingbot.salon.WorkHours;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlotCalculatorTest {

    private static final ZoneId MINSK = ZoneId.of("Europe/Minsk");
    private static final LocalDate DAY = LocalDate.of(2030, 1, 8);
    private static final WorkHours HOURS = WorkHours.parse("10:00-12:00");
    private static final Instant LONG_AGO = Instant.EPOCH;

    @Test
    void serviceMustFitBeforeEndOfDay() {
        List<LocalTime> starts = SlotCalculator.freeStarts(DAY, HOURS, Duration.ofMinutes(60), Duration.ofMinutes(30),
                List.of(), LONG_AGO, MINSK);

        assertThat(starts).containsExactly(LocalTime.of(10, 0), LocalTime.of(10, 30), LocalTime.of(11, 0));
    }

    @Test
    void startsOverlappingBusyIntervalAreExcluded() {
        Interval busy = new Interval(at(10, 30), at(11, 0));

        List<LocalTime> starts = SlotCalculator.freeStarts(DAY, HOURS, Duration.ofMinutes(60), Duration.ofMinutes(30),
                List.of(busy), LONG_AGO, MINSK);

        // 10:00–11:00 и 10:30–11:30 задевают занятое время, 11:00–12:00 начинается ровно в его конце.
        assertThat(starts).containsExactly(LocalTime.of(11, 0));
    }

    @Test
    void startsBeforeNotBeforeAreExcluded() {
        List<LocalTime> starts = SlotCalculator.freeStarts(DAY, HOURS, Duration.ofMinutes(30), Duration.ofMinutes(30),
                List.of(), at(11, 0), MINSK);

        assertThat(starts).containsExactly(LocalTime.of(11, 0), LocalTime.of(11, 30));
    }

    @Test
    void serviceLongerThanWorkingDayHasNoStarts() {
        List<LocalTime> starts = SlotCalculator.freeStarts(DAY, HOURS, Duration.ofHours(3), Duration.ofMinutes(30),
                List.of(), LONG_AGO, MINSK);

        assertThat(starts).isEmpty();
    }

    private static Instant at(int hour, int minute) {
        return DAY.atTime(hour, minute).atZone(MINSK).toInstant();
    }
}
