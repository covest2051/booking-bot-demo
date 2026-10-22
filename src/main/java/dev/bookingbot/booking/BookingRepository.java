package dev.bookingbot.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {

    @Query("""
            select b from BookingEntity b
            where b.masterCode = :masterCode and b.status = :status
              and b.startsAt < :to and b.endsAt > :from
            """)
    List<BookingEntity> findOverlapping(String masterCode, BookingStatus status, Instant from, Instant to);

    @Query("select b from BookingEntity b join fetch b.client where b.id = :id")
    Optional<BookingEntity> findWithClientById(Long id);

    @Query("""
            select b from BookingEntity b join fetch b.client
            where b.status = :status and b.startsAt >= :from and b.startsAt < :to
            order by b.startsAt, b.masterCode
            """)
    List<BookingEntity> findStartingBetween(BookingStatus status, Instant from, Instant to);

    @Query("""
            select b from BookingEntity b join fetch b.client
            where b.client.id = :clientId and b.status = :status and b.startsAt > :now
            order by b.startsAt
            """)
    List<BookingEntity> findUpcomingOfClient(Long clientId, BookingStatus status, Instant now);

    long countByClient_IdAndStatusAndStartsAtAfter(Long clientId, BookingStatus status, Instant now);

    @Query("""
            select b from BookingEntity b join fetch b.client
            where b.status = :status and b.remindedDayBeforeAt is null
              and b.startsAt > :now and b.startsAt <= :until
            """)
    List<BookingEntity> findDueDayBefore(BookingStatus status, Instant now, Instant until);

    @Query("""
            select b from BookingEntity b join fetch b.client
            where b.status = :status and b.remindedShortlyBeforeAt is null
              and b.startsAt > :now and b.startsAt <= :until
            """)
    List<BookingEntity> findDueShortlyBefore(BookingStatus status, Instant now, Instant until);

    /** Захват напоминания: 1 — захватили мы, 0 — уже отправлено другим запуском. */
    @Transactional
    @Modifying
    @Query("update BookingEntity b set b.remindedDayBeforeAt = :now where b.id = :id and b.remindedDayBeforeAt is null")
    int claimDayBefore(Long id, Instant now);

    @Transactional
    @Modifying
    @Query("update BookingEntity b set b.remindedDayBeforeAt = null where b.id = :id")
    void releaseDayBefore(Long id);

    @Transactional
    @Modifying
    @Query("update BookingEntity b set b.remindedShortlyBeforeAt = :now where b.id = :id and b.remindedShortlyBeforeAt is null")
    int claimShortlyBefore(Long id, Instant now);

    @Transactional
    @Modifying
    @Query("update BookingEntity b set b.remindedShortlyBeforeAt = null where b.id = :id")
    void releaseShortlyBefore(Long id);
}
