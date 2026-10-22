package dev.bookingbot.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Время, когда мастер не принимает (выходной, отпуск). */
@Entity
@Table(name = "time_off")
public class TimeOffEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "master_code", nullable = false)
    private String masterCode;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TimeOffEntity() {
    }

    public TimeOffEntity(String masterCode, Instant startsAt, Instant endsAt, Instant now) {
        this.masterCode = masterCode;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getMasterCode() {
        return masterCode;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }
}
