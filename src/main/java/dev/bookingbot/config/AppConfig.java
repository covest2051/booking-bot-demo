package dev.bookingbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.interfaces.BackOff;
import org.telegram.telegrambots.longpolling.util.TelegramOkHttpClientFactory;
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

    /**
     * То же, что создаёт стартер по умолчанию, кроме паузы между повторами: у библиотеки она растёт
     * до 15 минут, и после короткого сбоя сети бот молчал четверть часа. Здесь — не дольше 30 секунд.
     */
    @Bean
    public TelegramBotsLongPollingApplication telegramBotsApplication() {
        return new TelegramBotsLongPollingApplication(
                ObjectMapper::new,
                new TelegramOkHttpClientFactory.DefaultOkHttpClientCreator(),
                Executors::newSingleThreadScheduledExecutor,
                CappedBackOff::new);
    }

    static final class CappedBackOff implements BackOff {

        private static final long INITIAL_MILLIS = 500;
        private static final long MAX_MILLIS = 30_000;

        private long current = INITIAL_MILLIS;

        @Override
        public void reset() {
            current = INITIAL_MILLIS;
        }

        @Override
        public long nextBackOffMillis() {
            long next = current;
            current = Math.min(current * 2, MAX_MILLIS);
            return next;
        }
    }
}
