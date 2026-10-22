package dev.bookingbot.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.bookingbot.booking.BookingService.Outcome;
import dev.bookingbot.booking.BookingService.Result;
import dev.bookingbot.bot.TelegramSender;
import dev.bookingbot.bot.TelegramSender.Delivery;
import dev.bookingbot.salon.Offering;
import dev.bookingbot.salon.SalonCatalog;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "bot.enabled=false",
        "SALON_CONFIG=src/test/resources/salon-test.yml"
})
@Testcontainers
class BookingIntegrationTest {

    private static final ZoneId MINSK = ZoneId.of("Europe/Minsk");
    /** Понедельник 07.01.2030, 09:00 по Минску. */
    private static final Instant MONDAY_9AM = LocalDate.of(2030, 1, 7).atTime(9, 0).atZone(MINSK).toInstant();
    private static final LocalDate TUESDAY = LocalDate.of(2030, 1, 8);
    private static final LocalTime TEN = LocalTime.of(10, 0);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(MONDAY_9AM);
        }
    }

    @Autowired
    private BookingService bookingService;
    @Autowired
    private BookingRepository bookings;
    @Autowired
    private ClientRepository clients;
    @Autowired
    private SalonCatalog catalog;
    @Autowired
    private ReminderJob reminderJob;
    @Autowired
    private MutableClock clock;

    @MockitoBean
    private TelegramSender sender;

    private Offering cut;

    @BeforeEach
    void setUp() {
        bookings.deleteAll();
        clients.deleteAll();
        clock.set(MONDAY_9AM);
        cut = catalog.offering("cut");
        when(sender.send(anyLong(), any(), any())).thenReturn(Delivery.SENT);
    }

    @Test
    void databaseRejectsOverlappingActiveBookingsOfOneMaster() {
        ClientEntity client = client(1);
        bookings.saveAndFlush(booking(client, "m1", TUESDAY.atTime(10, 0), Duration.ofMinutes(60)));

        // Начинается в другое время, но пересекается — уникальный индекс по началу такое бы пропустил.
        assertThatThrownBy(() -> bookings.saveAndFlush(
                booking(client, "m1", TUESDAY.atTime(10, 30), Duration.ofMinutes(30))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void adjacentBookingsAndCancelledOnesDoNotConflict() {
        ClientEntity client = client(1);
        BookingEntity first = bookings.saveAndFlush(booking(client, "m1", TUESDAY.atTime(10, 0), Duration.ofMinutes(60)));
        bookings.saveAndFlush(booking(client, "m1", TUESDAY.atTime(11, 0), Duration.ofMinutes(60)));

        first.cancel(BookingStatus.CANCELLED_BY_CLIENT, clock.instant());
        bookings.saveAndFlush(first);
        bookings.saveAndFlush(booking(client, "m1", TUESDAY.atTime(10, 0), Duration.ofMinutes(60)));

        assertThat(bookings.count()).isEqualTo(3);
    }

    @Test
    void concurrentBookingsOfSameSlotSucceedExactlyOnce() throws Exception {
        int attempts = 8;
        List<ClientEntity> clientList = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            clientList.add(client(i));
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        List<Future<Result>> futures = new ArrayList<>();
        for (ClientEntity c : clientList) {
            futures.add(pool.submit(() -> {
                start.await();
                return bookingService.book(c, cut, "m1", TUESDAY, TEN);
            }));
        }
        start.countDown();

        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Result> f : futures) {
            outcomes.add(f.get().outcome());
        }
        pool.shutdown();

        assertThat(outcomes).filteredOn(o -> o == Outcome.BOOKED).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o == Outcome.SLOT_TAKEN).hasSize(attempts - 1);
    }

    @Test
    void anyMasterGoesToNextFreeMaster() {
        assertThat(bookingService.book(client(1), cut, "m1", TUESDAY, TEN).outcome()).isEqualTo(Outcome.BOOKED);

        Result result = bookingService.book(client(2), cut, SalonCatalog.ANY_MASTER, TUESDAY, TEN);

        assertThat(result.outcome()).isEqualTo(Outcome.BOOKED);
        assertThat(result.booking().getMasterCode()).isEqualTo("m2");
    }

    @Test
    void clientCannotExceedActiveBookingLimit() {
        ClientEntity client = client(1);
        bookingService.book(client, cut, "m1", TUESDAY, TEN);
        bookingService.book(client, cut, "m1", TUESDAY, LocalTime.of(12, 0));

        assertThat(bookingService.book(client, cut, "m1", TUESDAY, LocalTime.of(14, 0)).outcome())
                .isEqualTo(Outcome.LIMIT_REACHED);
    }

    @Test
    void clientCannotCancelShortlyBeforeVisit() {
        ClientEntity client = client(1);
        BookingEntity booking = bookingService.book(client, cut, "m1", TUESDAY, TEN).booking();

        clock.set(TUESDAY.atTime(8, 0).atZone(MINSK).toInstant());

        assertThat(bookingService.cancelByClient(client.getId(), booking.getId()).outcome()).isEqualTo(Outcome.TOO_LATE);
    }

    @Test
    void eachReminderIsSentOnceAndRetriedAfterFailure() {
        ClientEntity client = client(1);
        bookingService.book(client, cut, "m1", TUESDAY, TEN);

        // Понедельник 11:00 — до визита 23 часа: окно «за сутки».
        clock.set(LocalDate.of(2030, 1, 7).atTime(11, 0).atZone(MINSK).toInstant());
        reminderJob.run();
        reminderJob.run();
        verify(sender, times(1)).send(anyLong(), contains("Напоминаем"), any());

        // Вторник 08:30 — окно «за 2 часа». Первая отправка падает, следующий запуск повторяет.
        clock.set(TUESDAY.atTime(8, 30).atZone(MINSK).toInstant());
        when(sender.send(anyLong(), contains("Скоро"), any())).thenReturn(Delivery.FAILED, Delivery.SENT);
        reminderJob.run();
        reminderJob.run();
        reminderJob.run();
        verify(sender, times(2)).send(anyLong(), contains("Скоро"), any());
    }

    private ClientEntity client(int n) {
        return clients.save(new ClientEntity(1000L + n, 1000L + n, "Клиент " + n, "+37529000000" + n, clock.instant()));
    }

    private BookingEntity booking(ClientEntity client, String master, LocalDateTime start, Duration duration) {
        Instant from = start.atZone(MINSK).toInstant();
        return new BookingEntity(client, master, "cut", from, from.plus(duration), clock.instant());
    }
}
