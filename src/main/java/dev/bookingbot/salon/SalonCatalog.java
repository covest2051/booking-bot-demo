package dev.bookingbot.salon;

import dev.bookingbot.config.SalonProperties;
import dev.bookingbot.config.SalonProperties.MasterProps;
import dev.bookingbot.config.SalonProperties.OfferingProps;
import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Услуги и мастера из salon.yml, проверенные при старте: ошибка в конфиге должна валить запуск,
 * а не всплывать у клиента посреди записи.
 */
@Component
public class SalonCatalog {

    /** Выбор «любой мастер» в данных кнопок. */
    public static final String ANY_MASTER = "*";

    // Коды попадают в callback_data кнопок, а Telegram ограничивает её 64 байтами.
    private static final Pattern CODE = Pattern.compile("[a-z0-9]{1,8}");

    private final SalonProperties settings;
    private final Map<String, Offering> offerings = new LinkedHashMap<>();
    private final Map<String, Master> masters = new LinkedHashMap<>();

    public SalonCatalog(SalonProperties settings) {
        this.settings = settings;
        require(settings.name() != null && !settings.name().isBlank(),
                "salon.name не задан — проверьте, что salon.yml лежит рядом с приложением");
        require(!settings.services().isEmpty(), "В salon.yml нет ни одной услуги (salon.services)");
        require(!settings.masters().isEmpty(), "В salon.yml нет ни одного мастера (salon.masters)");
        require(settings.slotStep().isPositive(), "salon.slot-step должен быть больше нуля");
        require(settings.bookingHorizonDays() > 0, "salon.booking-horizon-days должен быть больше нуля");

        for (OfferingProps o : settings.services()) {
            requireCode(o.code(), "услуги");
            require(o.duration() != null && o.duration().isPositive(), "У услуги " + o.code() + " не задана длительность");
            require(offerings.putIfAbsent(o.code(), new Offering(o.code(), o.name(), o.duration(), o.price())) == null,
                    "Код услуги повторяется: " + o.code());
        }
        for (MasterProps m : settings.masters()) {
            requireCode(m.code(), "мастера");
            require(m.services() != null && !m.services().isEmpty(), "У мастера " + m.code() + " нет услуг");
            for (String code : m.services()) {
                require(offerings.containsKey(code), "У мастера " + m.code() + " неизвестная услуга: " + code);
            }
            Map<DayOfWeek, WorkHours> schedule = new EnumMap<>(DayOfWeek.class);
            if (m.schedule() != null) {
                m.schedule().forEach((day, hours) -> schedule.put(day, WorkHours.parse(hours)));
            }
            Master master = new Master(m.code(), m.name(), new LinkedHashSet<>(m.services()), schedule);
            require(masters.putIfAbsent(m.code(), master) == null, "Код мастера повторяется: " + m.code());
        }
    }

    public SalonProperties settings() {
        return settings;
    }

    public List<Offering> offerings() {
        return List.copyOf(offerings.values());
    }

    public Offering offering(String code) {
        Offering offering = offerings.get(code);
        if (offering == null) {
            throw new IllegalArgumentException("Неизвестная услуга: " + code);
        }
        return offering;
    }

    public List<Master> masters() {
        return List.copyOf(masters.values());
    }

    public Master master(String code) {
        Master master = masters.get(code);
        if (master == null) {
            throw new IllegalArgumentException("Неизвестный мастер: " + code);
        }
        return master;
    }

    public List<Master> mastersFor(String offeringCode) {
        return masters.values().stream().filter(m -> m.provides(offeringCode)).toList();
    }

    /** Для старых записей: мастера или услугу могли убрать из конфига, а запись осталась. */
    public String masterName(String code) {
        Master master = masters.get(code);
        return master != null ? master.name() : code;
    }

    public String offeringName(String code) {
        Offering offering = offerings.get(code);
        return offering != null ? offering.name() : code;
    }

    private static void requireCode(String code, String what) {
        require(code != null && CODE.matcher(code).matches(),
                "Код " + what + " должен быть из латиницы/цифр, до 8 символов: " + code);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
