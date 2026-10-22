package dev.bookingbot.config;

import java.time.Clock;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public TelegramClient telegramClient(BotProperties props) {
        return new OkHttpTelegramClient(Objects.requireNonNullElse(props.token(), ""));
    }
}
