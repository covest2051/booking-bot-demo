package dev.bookingbot.booking;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TimeOffRepository extends JpaRepository<TimeOffEntity, Long> {

    @Query("""
            select t from TimeOffEntity t
            where t.masterCode = :masterCode and t.startsAt < :to and t.endsAt > :from
            """)
    List<TimeOffEntity> findOverlapping(String masterCode, Instant from, Instant to);

    List<TimeOffEntity> findByEndsAtAfterOrderByStartsAt(Instant now);
}
