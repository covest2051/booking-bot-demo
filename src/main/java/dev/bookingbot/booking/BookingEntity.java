package dev.bookingbot.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "booking")
public class BookingEntity {

    public static final Duration REMIND_DAY_BEFORE = Duration.ofHours(24);
    public static final Duration REMIND_SHORTLY_BEFORE = Duration.ofHours(2);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientEntity client;

    @Column(name = "master_code", nullable = false)
    private String masterCode;

    @Column(name = "offering_code", nullable = false)
    private String offeringCode;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "reminded_24h_at")
    private Instant remindedDayBeforeAt;

    @Column(name = "reminded_2h_at")
    private Instant remindedShortlyBeforeAt;

    protected BookingEntity() {
    }

    public BookingEntity(ClientEntity client, String masterCode, String offeringCode,
                         Instant startsAt, Instant endsAt, Instant now) {
        this.client = client;
        this.masterCode = masterCode;
        this.offeringCode = offeringCode;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = BookingStatus.ACTIVE;
        this.createdAt = now;
        // Окно напоминания уже наступило в момент записи — такое напоминание не шлём:
        // запись за 5 часов не должна тут же получать «напоминание за сутки».
        if (!startsAt.isAfter(now.plus(REMIND_DAY_BEFORE))) {
            this.remindedDayBeforeAt = now;
        }
        if (!startsAt.isAfter(now.plus(REMIND_SHORTLY_BEFORE))) {
            this.remindedShortlyBeforeAt = now;
        }
    }

    public void cancel(BookingStatus cancelStatus, Instant now) {
        this.status = cancelStatus;
        this.cancelledAt = now;
    }

    public boolean isActive() {
        return status == BookingStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public ClientEntity getClient() {
        return client;
    }

    public String getMasterCode() {
        return masterCode;
    }

    public String getOfferingCode() {
        return offeringCode;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public BookingStatus getStatus() {
        return status;
    }
}
