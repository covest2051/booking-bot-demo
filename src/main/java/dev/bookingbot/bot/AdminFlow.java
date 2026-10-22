package dev.bookingbot.bot;

import static dev.bookingbot.bot.Keyboards.button;
import static dev.bookingbot.bot.Keyboards.grid;
import static dev.bookingbot.bot.Keyboards.inline;
import static dev.bookingbot.bot.Keyboards.row;

import dev.bookingbot.booking.BookingEntity;
import dev.bookingbot.booking.BookingService;
import dev.bookingbot.booking.BookingService.Outcome;
import dev.bookingbot.booking.BookingService.Result;
import dev.bookingbot.booking.SlotService;
import dev.bookingbot.booking.TimeOffEntity;
import dev.bookingbot.booking.TimeOffService;
import dev.bookingbot.booking.TimeOffService.CloseDayResult;
import dev.bookingbot.config.BotProperties;
import dev.bookingbot.salon.Master;
import dev.bookingbot.salon.SalonCatalog;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

/** Команды администратора салона. Права проверяет BookingBot по bot.admin-ids. */
@Component
public class AdminFlow {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private static final String HELP = """
            Команды администратора:
            /today — записи на сегодня
            /tomorrow — записи на завтра
            /week — записи на 7 дней
            /off — поставить мастеру выходной
            /offs — запланированные выходные
            Отменить запись — нажмите /cancel_N рядом с ней.""";

    private final BookingService bookings;
    private final TimeOffService timeOffs;
    private final SlotService slots;
    private final SalonCatalog catalog;
    private final TelegramSender sender;
    private final Notifier notifier;
    private final Texts texts;
    private final ZoneId zone;

    public AdminFlow(BookingService bookings, TimeOffService timeOffs, SlotService slots, SalonCatalog catalog,
                     TelegramSender sender, Notifier notifier, Texts texts, BotProperties props) {
        this.bookings = bookings;
        this.timeOffs = timeOffs;
        this.slots = slots;
        this.catalog = catalog;
        this.sender = sender;
        this.notifier = notifier;
        this.texts = texts;
        this.zone = props.zone();
    }

    /** Админские кнопки начинаются с "a" — у клиентских такого префикса нет. */
    static boolean isAdminCallback(String data) {
        return data.startsWith("a");
    }

    /** @return true, если это была админская команда. */
    boolean onCommand(Message message) {
        Long chatId = message.getChatId();
        String text = message.getText().trim();
        LocalDate today = slots.today();
        switch (text) {
            case "/admin", "/help" -> sender.send(chatId, HELP, null);
            case "/today" -> sender.send(chatId, schedule("Сегодня", today, 1), null);
            case "/tomorrow" -> sender.send(chatId, schedule("Завтра", today.plusDays(1), 1), null);
            case "/week" -> sender.send(chatId, schedule("Ближайшие 7 дней", today, 7), null);
            case "/off" -> send(chatId, chooseMasterForDayOff());
            case "/offs" -> sender.send(chatId, dayOffs(), null);
            default -> {
                if (text.startsWith("/cancel_")) {
                    send(chatId, askCancel(parseId(text, "/cancel_")));
                } else if (text.startsWith("/unoff_")) {
                    boolean removed = parseId(text, "/unoff_").map(timeOffs::remove).orElse(false);
                    sender.send(chatId, removed ? "Выходной удалён." : "Выходной не найден.", null);
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    void onCallback(CallbackQuery query) {
        String[] p = query.getData().split(":");
        Screen screen = switch (p[0]) {
            case "ax" -> cancel(Long.parseLong(p[1]));
            case "aom" -> chooseDayOff(catalog.master(p[1]));
            case "aod" -> closeDay(catalog.master(p[1]), LocalDate.parse(p[2], DATE));
            default -> throw new IllegalArgumentException("Неизвестная кнопка: " + query.getData());
        };
        sender.edit(query.getMessage().getChatId(), query.getMessage().getMessageId(), screen.text(), screen.markup());
    }

    private String schedule(String title, LocalDate from, int days) {
        List<BookingEntity> list = bookings.activeBetween(from, from.plusDays(days));
        if (list.isEmpty()) {
            return title + ": записей нет.";
        }
        StringBuilder text = new StringBuilder(title + ":");
        LocalDate currentDay = null;
        for (BookingEntity b : list) {
            LocalDate day = b.getStartsAt().atZone(zone).toLocalDate();
            if (!day.equals(currentDay)) {
                text.append("\n\n📆 ").append(texts.day(day));
                currentDay = day;
            }
            text.append("\n").append(texts.period(b)).append(" · ").append(catalog.masterName(b.getMasterCode()))
                    .append(" · ").append(catalog.offeringName(b.getOfferingCode()))
                    .append("\n   ").append(b.getClient().getName()).append(", ").append(b.getClient().getPhone())
                    .append(" · /cancel_").append(b.getId());
        }
        return text.toString();
    }

    private Screen askCancel(Optional<Long> bookingId) {
        return bookingId.flatMap(bookings::findActive)
                .map(b -> new Screen("Отменить запись? Клиент получит уведомление.\n\n"
                        + texts.booking(b) + "\n" + texts.clientLine(b),
                        inline(row(button("Отменить запись", "ax:" + b.getId())))))
                .orElseGet(() -> Screen.of("Запись не найдена или уже отменена."));
    }

    private Screen cancel(Long bookingId) {
        Result result = bookings.cancelByAdmin(bookingId);
        if (result.outcome() != Outcome.CANCELLED) {
            return Screen.of("Запись не найдена или уже отменена.");
        }
        notifier.cancelledByAdmin(result.booking());
        return Screen.of("Запись отменена, клиент уведомлён.\n\n" + texts.booking(result.booking()));
    }

    private Screen chooseMasterForDayOff() {
        List<InlineKeyboardButton> buttons = catalog.masters().stream()
                .map(m -> button(m.name(), "aom:" + m.code()))
                .toList();
        return new Screen("Кому поставить выходной?", grid(buttons, 1));
    }

    private Screen chooseDayOff(Master master) {
        LocalDate today = slots.today();
        List<InlineKeyboardButton> buttons = IntStream.range(0, catalog.settings().bookingHorizonDays())
                .mapToObj(today::plusDays)
                .filter(d -> master.hoursOn(d.getDayOfWeek()).isPresent())
                .map(d -> button(texts.day(d), "aod:" + master.code() + ":" + DATE.format(d)))
                .toList();
        return new Screen(master.name() + ": выберите день", grid(buttons, 2));
    }

    private Screen closeDay(Master master, LocalDate date) {
        CloseDayResult result = timeOffs.closeDay(master, date);
        if (result.closed()) {
            return Screen.of("Выходной поставлен: " + master.name() + ", " + texts.day(date)
                    + ".\nКлиенты не увидят этот день при записи. Убрать: /offs");
        }
        StringBuilder text = new StringBuilder("Нельзя закрыть день — на него есть записи. Отмените их и повторите /off:\n");
        for (BookingEntity b : result.conflicts()) {
            text.append("\n").append(texts.period(b)).append(" · ")
                    .append(catalog.offeringName(b.getOfferingCode())).append(" · /cancel_").append(b.getId());
        }
        return Screen.of(text.toString());
    }

    private String dayOffs() {
        List<TimeOffEntity> list = timeOffs.upcoming();
        if (list.isEmpty()) {
            return "Выходных не запланировано. Поставить: /off";
        }
        StringBuilder text = new StringBuilder("Запланированные выходные:");
        for (TimeOffEntity t : list) {
            text.append("\n").append(catalog.masterName(t.getMasterCode())).append(" — ")
                    .append(texts.day(t.getStartsAt().atZone(zone).toLocalDate()))
                    .append(" · убрать: /unoff_").append(t.getId());
        }
        return text.toString();
    }

    private void send(Long chatId, Screen screen) {
        sender.send(chatId, screen.text(), screen.markup());
    }

    private static Optional<Long> parseId(String text, String prefix) {
        try {
            return Optional.of(Long.parseLong(text.substring(prefix.length())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
