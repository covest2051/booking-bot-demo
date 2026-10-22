package dev.bookingbot.config;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки конкретного салона из salon.yml: услуги, мастера, график и правила записи.
 * Под нового заказчика меняется только этот файл.
 */
@ConfigurationProperties("salon")
public record SalonProperties(
        String name,
        String address,
        String phone,
        Duration slotStep,
        Duration minLeadTime,
        Integer bookingHorizonDays,
        Duration cancelBefore,
        Integer maxActiveBookings,
        List<OfferingProps> services,
        List<MasterProps> masters) {

    public SalonProperties {
        slotStep = slotStep != null ? slotStep : Duration.ofMinutes(30);
        minLeadTime = minLeadTime != null ? minLeadTime : Duration.ofHours(1);
        bookingHorizonDays = bookingHorizonDays != null ? bookingHorizonDays : 14;
        cancelBefore = cancelBefore != null ? cancelBefore : Duration.ofHours(3);
        maxActiveBookings = maxActiveBookings != null ? maxActiveBookings : 2;
        services = services != null ? List.copyOf(services) : List.of();
        masters = masters != null ? List.copyOf(masters) : List.of();
    }

    public record OfferingProps(String code, String name, Duration duration, BigDecimal price) {
    }

    /** schedule: день недели → "10:00-19:00"; день не указан — выходной. */
    public record MasterProps(String code, String name, List<String> services, Map<DayOfWeek, String> schedule) {
    }
}
