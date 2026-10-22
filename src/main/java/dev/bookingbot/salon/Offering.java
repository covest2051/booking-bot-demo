package dev.bookingbot.salon;

import java.math.BigDecimal;
import java.time.Duration;

/** Услуга салона. */
public record Offering(String code, String name, Duration duration, BigDecimal price) {
}
