package dev.bookingbot.bot;

import dev.bookingbot.booking.BookingEntity;
import dev.bookingbot.config.BotProperties;
import dev.bookingbot.config.SalonProperties;
import dev.bookingbot.salon.Offering;
import dev.bookingbot.salon.SalonCatalog;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Все тексты и форматирование дат в одном месте — под заказчика правятся здесь. */
@Component
public class Texts {

    private static final Locale RU = Locale.of("ru");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EE, d MMMM", RU);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final SalonCatalog catalog;
    private final ZoneId zone;

    public Texts(SalonCatalog catalog, BotProperties props) {
        this.catalog = catalog;
        this.zone = props.zone();
    }

    public String day(LocalDate date) {
        return DAY.format(date);
    }

    public String time(LocalTime time) {
        return TIME.format(time);
    }

    public String when(Instant instant) {
        ZonedDateTime local = instant.atZone(zone);
        return day(local.toLocalDate()) + ", " + time(local.toLocalTime());
    }

    public String period(BookingEntity b) {
        return time(b.getStartsAt().atZone(zone).toLocalTime()) + "–" + time(b.getEndsAt().atZone(zone).toLocalTime());
    }

    public String price(BigDecimal price) {
        return price == null ? "" : price.stripTrailingZeros().toPlainString() + " BYN";
    }

    public String offeringButton(Offering o) {
        String label = o.name() + " · " + minutes(o.duration());
        return o.price() == null ? label : label + " · " + price(o.price());
    }

    public String booking(BookingEntity b) {
        return "🗓 " + when(b.getStartsAt()) + "\n"
                + "💇 " + catalog.offeringName(b.getOfferingCode()) + ", мастер " + catalog.masterName(b.getMasterCode());
    }

    public String welcome() {
        SalonProperties s = catalog.settings();
        return "Здравствуйте! Это бот записи в " + s.name() + ".\n\n"
                + "Здесь можно записаться к мастеру, посмотреть и отменить свои записи. "
                + "Мы напомним о визите за сутки и за 2 часа.\n\n"
                + "Чтобы начать, поделитесь номером телефона — кнопка внизу.\n\n"
                + "Нажимая кнопку, вы соглашаетесь на обработку имени и номера телефона салоном "
                + "для записи и напоминаний. Данные не передаются третьим лицам.";
    }

    public String about() {
        SalonProperties s = catalog.settings();
        StringBuilder text = new StringBuilder(s.name());
        if (s.address() != null) {
            text.append("\n📍 ").append(s.address());
        }
        if (s.phone() != null) {
            text.append("\n📞 ").append(s.phone());
        }
        text.append("\n\nУслуги:");
        for (Offering o : catalog.offerings()) {
            text.append("\n• ").append(offeringButton(o));
        }
        return text.toString();
    }

    public String callUs() {
        String phone = catalog.settings().phone();
        return phone == null ? "свяжитесь с салоном" : "позвоните: " + phone;
    }

    public String reminder(BookingEntity b, boolean dayBefore) {
        String header = dayBefore ? "⏰ Напоминаем о записи" : "⏰ Скоро ваша запись";
        String address = catalog.settings().address();
        return header + "\n\n" + booking(b)
                + (address == null ? "" : "\n📍 " + address)
                + "\n\nЕсли не получается прийти — отмените запись в «Мои записи» или " + callUs() + ".";
    }

    public String adminNewBooking(BookingEntity b) {
        return "🆕 Новая запись\n\n" + booking(b) + "\n" + clientLine(b) + "\nОтменить: /cancel_" + b.getId();
    }

    public String adminCancelledByClient(BookingEntity b) {
        return "❌ Клиент отменил запись\n\n" + booking(b) + "\n" + clientLine(b);
    }

    public String cancelledBySalon(BookingEntity b) {
        return "😔 К сожалению, салон отменил вашу запись:\n\n" + booking(b)
                + "\n\nЧтобы выбрать другое время, нажмите «Записаться» или " + callUs() + ".";
    }

    public String clientLine(BookingEntity b) {
        return "👤 " + b.getClient().getName() + ", " + b.getClient().getPhone();
    }

    private static String minutes(Duration duration) {
        return duration.toMinutes() + " мин";
    }
}
