package dev.bookingbot.salon;

import java.time.DayOfWeek;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record Master(String code, String name, Set<String> offeringCodes, Map<DayOfWeek, WorkHours> schedule) {

    public boolean provides(String offeringCode) {
        return offeringCodes.contains(offeringCode);
    }

    public Optional<WorkHours> hoursOn(DayOfWeek day) {
        return Optional.ofNullable(schedule.get(day));
    }
}
