package dev.bookingbot.booking;

import static dev.bookingbot.booking.BookingEntity.REMIND_DAY_BEFORE;
import static dev.bookingbot.booking.BookingEntity.REMIND_SHORTLY_BEFORE;

import dev.bookingbot.bot.TelegramSender;
import dev.bookingbot.bot.TelegramSender.Delivery;
import dev.bookingbot.bot.Texts;
import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Напоминания за сутки и за 2 часа. Запись сначала «захватывается» условным UPDATE и только потом
 * отправляется: так не будет дублей ни при пересекающихся запусках, ни после перезапуска.
 * Если Telegram не принял сообщение — захват снимается, и следующий запуск попробует снова.
 */
@Component
public class ReminderJob {

    private final BookingRepository bookings;
    private final TelegramSender sender;
    private final Texts texts;
    private final Clock clock;

    public ReminderJob(BookingRepository bookings, TelegramSender sender, Texts texts, Clock clock) {
        this.bookings = bookings;
        this.sender = sender;
        this.texts = texts;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${bot.reminder-interval:PT1M}")
    public void run() {
        Instant now = clock.instant();
        for (BookingEntity b : bookings.findDueDayBefore(BookingStatus.ACTIVE, now, now.plus(REMIND_DAY_BEFORE))) {
            if (bookings.claimDayBefore(b.getId(), now) == 0) {
                continue;
            }
            // Бот лежал, и до визита уже меньше 2 часов — хватит одного напоминания «скоро».
            if (!b.getStartsAt().isAfter(now.plus(REMIND_SHORTLY_BEFORE))) {
                continue;
            }
            if (deliver(b, true) == Delivery.FAILED) {
                bookings.releaseDayBefore(b.getId());
            }
        }
        for (BookingEntity b : bookings.findDueShortlyBefore(BookingStatus.ACTIVE, now, now.plus(REMIND_SHORTLY_BEFORE))) {
            if (bookings.claimShortlyBefore(b.getId(), now) == 0) {
                continue;
            }
            if (deliver(b, false) == Delivery.FAILED) {
                bookings.releaseShortlyBefore(b.getId());
            }
        }
    }

    private Delivery deliver(BookingEntity booking, boolean dayBefore) {
        return sender.send(booking.getClient().getChatId(), texts.reminder(booking, dayBefore), null);
    }
}
