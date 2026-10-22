package dev.bookingbot.salon;

import java.time.LocalTime;

public record WorkHours(LocalTime from, LocalTime to) {

    /** Формат "10:00-19:00". */
    public static WorkHours parse(String value) {
        String[] parts = value.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("График ожидается в формате HH:mm-HH:mm, получено: " + value);
        }
        LocalTime from = LocalTime.parse(parts[0].trim());
        LocalTime to = LocalTime.parse(parts[1].trim());
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("Начало рабочего дня должно быть раньше конца: " + value);
        }
        return new WorkHours(from, to);
    }
}
