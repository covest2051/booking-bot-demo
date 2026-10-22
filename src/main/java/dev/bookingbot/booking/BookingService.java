package dev.bookingbot.booking;

import dev.bookingbot.config.BotProperties;
import dev.bookingbot.salon.Master;
import dev.bookingbot.salon.Offering;
import dev.bookingbot.salon.SalonCatalog;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    /** SQLSTATE PostgreSQL для нарушения EXCLUDE-ограничения booking_no_overlap. */
    private static final String EXCLUSION_VIOLATION = "23P01";

    public enum Outcome { BOOKED, CANCELLED, SLOT_TAKEN, LIMIT_REACHED, TOO_LATE, NOT_FOUND }

    public record Result(Outcome outcome, BookingEntity booking) {

        static Result of(Outcome outcome) {
            return new Result(outcome, null);
        }
    }

    private final BookingRepository bookings;
    private final SlotService slots;
    private final SalonCatalog catalog;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ZoneId zone;

    public BookingService(BookingRepository bookings, SlotService slots, SalonCatalog catalog,
                          TransactionTemplate tx, Clock clock, BotProperties props) {
        this.bookings = bookings;
        this.slots = slots;
        this.catalog = catalog;
        this.tx = tx;
        this.clock = clock;
        this.zone = props.zone();
    }

    /**
     * Не @Transactional целиком: каждая попытка вставки — своя транзакция. После нарушения
     * ограничения PostgreSQL помечает транзакцию как сломанную, и «попробовать другого мастера»
     * в ней уже нельзя.
     */
    public Result book(ClientEntity client, Offering offering, String masterCode, LocalDate date, LocalTime time) {
        Instant now = clock.instant();
        Instant start = date.atTime(time).atZone(zone).toInstant();
        if (start.isBefore(now.plus(catalog.settings().minLeadTime()))) {
            return Result.of(Outcome.TOO_LATE);
        }
        // Мягкий лимит: два одновременных нажатия одного клиента могут его обойти — для салона не критично.
        long active = bookings.countByClient_IdAndStatusAndStartsAtAfter(client.getId(), BookingStatus.ACTIVE, now);
        if (active >= catalog.settings().maxActiveBookings()) {
            return Result.of(Outcome.LIMIT_REACHED);
        }
        for (Master master : slots.mastersFreeAt(offering, masterCode, date, time)) {
            try {
                BookingEntity saved = tx.execute(status -> bookings.saveAndFlush(new BookingEntity(
                        client, master.code(), offering.code(), start, start.plus(offering.duration()), now)));
                return new Result(Outcome.BOOKED, saved);
            } catch (DataIntegrityViolationException e) {
                if (!isExclusionViolation(e)) {
                    throw e;
                }
                log.info("Время {} у мастера {} заняли параллельно, пробуем следующего", start, master.code());
            }
        }
        return Result.of(Outcome.SLOT_TAKEN);
    }

    @Transactional
    public Result cancelByClient(Long clientId, Long bookingId) {
        Optional<BookingEntity> found = bookings.findWithClientById(bookingId)
                .filter(b -> b.getClient().getId().equals(clientId) && b.isActive());
        if (found.isEmpty()) {
            return Result.of(Outcome.NOT_FOUND);
        }
        BookingEntity booking = found.get();
        Instant now = clock.instant();
        if (booking.getStartsAt().isBefore(now.plus(catalog.settings().cancelBefore()))) {
            return new Result(Outcome.TOO_LATE, booking);
        }
        booking.cancel(BookingStatus.CANCELLED_BY_CLIENT, now);
        return new Result(Outcome.CANCELLED, booking);
    }

    @Transactional
    public Result cancelByAdmin(Long bookingId) {
        Optional<BookingEntity> found = bookings.findWithClientById(bookingId).filter(BookingEntity::isActive);
        if (found.isEmpty()) {
            return Result.of(Outcome.NOT_FOUND);
        }
        found.get().cancel(BookingStatus.CANCELLED_BY_ADMIN, clock.instant());
        return new Result(Outcome.CANCELLED, found.get());
    }

    public Optional<BookingEntity> findActive(Long bookingId) {
        return bookings.findWithClientById(bookingId).filter(BookingEntity::isActive);
    }

    public List<BookingEntity> upcomingOf(Long clientId) {
        return bookings.findUpcomingOfClient(clientId, BookingStatus.ACTIVE, clock.instant());
    }

    public List<BookingEntity> activeBetween(LocalDate from, LocalDate toExclusive) {
        return bookings.findStartingBetween(BookingStatus.ACTIVE,
                from.atStartOfDay(zone).toInstant(), toExclusive.atStartOfDay(zone).toInstant());
    }

    private static boolean isExclusionViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && EXCLUSION_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
