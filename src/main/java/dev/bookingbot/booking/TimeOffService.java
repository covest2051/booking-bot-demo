package dev.bookingbot.booking;

import dev.bookingbot.config.BotProperties;
import dev.bookingbot.salon.Master;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimeOffService {

    /** closed=false — на день есть записи, выходной не поставлен; их надо сначала отменить. */
    public record CloseDayResult(boolean closed, List<BookingEntity> conflicts) {
    }

    private final TimeOffRepository timeOffs;
    private final BookingRepository bookings;
    private final Clock clock;
    private final ZoneId zone;

    public TimeOffService(TimeOffRepository timeOffs, BookingRepository bookings, Clock clock, BotProperties props) {
        this.timeOffs = timeOffs;
        this.bookings = bookings;
        this.clock = clock;
        this.zone = props.zone();
    }

    @Transactional
    public CloseDayResult closeDay(Master master, LocalDate date) {
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<BookingEntity> conflicts = bookings.findOverlapping(master.code(), BookingStatus.ACTIVE, from, to);
        if (!conflicts.isEmpty()) {
            return new CloseDayResult(false, conflicts);
        }
        if (timeOffs.findOverlapping(master.code(), from, to).isEmpty()) {
            timeOffs.save(new TimeOffEntity(master.code(), from, to, clock.instant()));
        }
        return new CloseDayResult(true, List.of());
    }

    public List<TimeOffEntity> upcoming() {
        return timeOffs.findByEndsAtAfterOrderByStartsAt(clock.instant());
    }

    @Transactional
    public boolean remove(Long id) {
        if (!timeOffs.existsById(id)) {
            return false;
        }
        timeOffs.deleteById(id);
        return true;
    }
}
