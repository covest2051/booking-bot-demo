package dev.bookingbot.booking;

import dev.bookingbot.booking.SlotCalculator.Interval;
import dev.bookingbot.config.BotProperties;
import dev.bookingbot.salon.Master;
import dev.bookingbot.salon.Offering;
import dev.bookingbot.salon.SalonCatalog;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Свободное время для показа клиенту. Это подсказка, а не гарантия: между показом и нажатием
 * «Записаться» время могут занять, окончательно решает ограничение в БД (см. BookingService).
 */
@Service
public class SlotService {

    private final SalonCatalog catalog;
    private final BookingRepository bookings;
    private final TimeOffRepository timeOffs;
    private final Clock clock;
    private final ZoneId zone;

    public SlotService(SalonCatalog catalog, BookingRepository bookings, TimeOffRepository timeOffs,
                       Clock clock, BotProperties props) {
        this.catalog = catalog;
        this.bookings = bookings;
        this.timeOffs = timeOffs;
        this.clock = clock;
        this.zone = props.zone();
    }

    public List<LocalTime> freeStarts(Offering offering, Master master, LocalDate date) {
        if (!master.provides(offering.code())) {
            return List.of();
        }
        return master.hoursOn(date.getDayOfWeek())
                .map(hours -> SlotCalculator.freeStarts(date, hours, offering.duration(),
                        catalog.settings().slotStep(), busy(master, date), notBefore(), zone))
                .orElse(List.of());
    }

    /** masterCode — код мастера или {@link SalonCatalog#ANY_MASTER}. */
    public List<LocalTime> freeStarts(Offering offering, String masterCode, LocalDate date) {
        return candidates(offering, masterCode).stream()
                .flatMap(master -> freeStarts(offering, master, date).stream())
                .distinct()
                .sorted()
                .toList();
    }

    public List<LocalDate> availableDates(Offering offering, String masterCode) {
        LocalDate today = today();
        return IntStream.range(0, catalog.settings().bookingHorizonDays())
                .mapToObj(today::plusDays)
                .filter(date -> !freeStarts(offering, masterCode, date).isEmpty())
                .toList();
    }

    public List<Master> mastersFreeAt(Offering offering, String masterCode, LocalDate date, LocalTime time) {
        return candidates(offering, masterCode).stream()
                .filter(master -> freeStarts(offering, master, date).contains(time))
                .toList();
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    private List<Master> candidates(Offering offering, String masterCode) {
        return SalonCatalog.ANY_MASTER.equals(masterCode)
                ? catalog.mastersFor(offering.code())
                : List.of(catalog.master(masterCode));
    }

    private List<Interval> busy(Master master, LocalDate date) {
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        return Stream.concat(
                bookings.findOverlapping(master.code(), BookingStatus.ACTIVE, from, to).stream()
                        .map(b -> new Interval(b.getStartsAt(), b.getEndsAt())),
                timeOffs.findOverlapping(master.code(), from, to).stream()
                        .map(t -> new Interval(t.getStartsAt(), t.getEndsAt()))
        ).toList();
    }

    private Instant notBefore() {
        return clock.instant().plus(catalog.settings().minLeadTime());
    }
}
