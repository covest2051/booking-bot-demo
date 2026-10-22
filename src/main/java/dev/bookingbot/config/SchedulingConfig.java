package dev.bookingbot.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Напоминания включаются вместе с ботом: в тестах (bot.enabled=false) планировщик не стартует. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "bot.enabled", havingValue = "true")
public class SchedulingConfig {
}
