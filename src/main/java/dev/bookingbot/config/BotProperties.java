package dev.bookingbot.config;

import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bot")
public record BotProperties(
        boolean enabled,
        String token,
        List<Long> adminIds,
        boolean demoMode,
        ZoneId zone) {

    public BotProperties {
        adminIds = adminIds != null ? List.copyOf(adminIds) : List.of();
        zone = zone != null ? zone : ZoneId.of("Europe/Minsk");
    }

    public boolean isAdmin(Long userId) {
        return adminIds.contains(userId);
    }
}
