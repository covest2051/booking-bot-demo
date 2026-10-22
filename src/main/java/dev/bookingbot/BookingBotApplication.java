package dev.bookingbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BookingBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingBotApplication.class, args);
    }
}
