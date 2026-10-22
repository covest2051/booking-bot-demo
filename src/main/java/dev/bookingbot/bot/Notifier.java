package dev.bookingbot.bot;

import dev.bookingbot.booking.BookingEntity;
import dev.bookingbot.config.BotProperties;
import org.springframework.stereotype.Component;

/** Уведомления администраторам салона и клиентам о действиях другой стороны. */
@Component
public class Notifier {

    private static final String DEMO_PREFIX = "👀 Демо: так это сообщение увидит администратор салона\n\n";

    private final TelegramSender sender;
    private final Texts texts;
    private final BotProperties props;

    public Notifier(TelegramSender sender, Texts texts, BotProperties props) {
        this.sender = sender;
        this.texts = texts;
        this.props = props;
    }

    public void newBooking(BookingEntity booking) {
        toAdminsAndDemo(texts.adminNewBooking(booking), booking);
    }

    public void cancelledByClient(BookingEntity booking) {
        toAdminsAndDemo(texts.adminCancelledByClient(booking), booking);
    }

    public void cancelledByAdmin(BookingEntity booking) {
        sender.send(booking.getClient().getChatId(), texts.cancelledBySalon(booking), null);
    }

    /** В демо-режиме клиент получает копию — так заказчик за 30 секунд видит обе стороны бота. */
    private void toAdminsAndDemo(String text, BookingEntity booking) {
        props.adminIds().forEach(adminId -> sender.send(adminId, text, null));
        if (props.demoMode()) {
            sender.send(booking.getClient().getChatId(), DEMO_PREFIX + text, null);
        }
    }
}
