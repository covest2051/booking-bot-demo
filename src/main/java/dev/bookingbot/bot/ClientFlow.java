package dev.bookingbot.bot;

import static dev.bookingbot.bot.Keyboards.button;
import static dev.bookingbot.bot.Keyboards.grid;
import static dev.bookingbot.bot.Keyboards.inline;
import static dev.bookingbot.bot.Keyboards.row;

import dev.bookingbot.booking.BookingEntity;
import dev.bookingbot.booking.BookingService;
import dev.bookingbot.booking.BookingService.Result;
import dev.bookingbot.booking.ClientEntity;
import dev.bookingbot.booking.ClientRepository;
import dev.bookingbot.booking.SlotService;
import dev.bookingbot.salon.Master;
import dev.bookingbot.salon.Offering;
import dev.bookingbot.salon.SalonCatalog;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

/**
 * Диалог клиента. Состояние между шагами не хранится: всё выбранное лежит в callback_data кнопок
 * ("t:услуга:мастер:дата"), поэтому перезапуск бота не ломает начатую запись. Номер телефона
 * спрашиваем один раз при /start — иначе пришлось бы хранить незавершённую запись, пока клиент ищет кнопку.
 */
@Component
public class ClientFlow {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");

    private final ClientRepository clients;
    private final SalonCatalog catalog;
    private final SlotService slots;
    private final BookingService bookings;
    private final TelegramSender sender;
    private final Notifier notifier;
    private final Texts texts;
    private final Clock clock;

    public ClientFlow(ClientRepository clients, SalonCatalog catalog, SlotService slots, BookingService bookings,
                      TelegramSender sender, Notifier notifier, Texts texts, Clock clock) {
        this.clients = clients;
        this.catalog = catalog;
        this.slots = slots;
        this.bookings = bookings;
        this.sender = sender;
        this.notifier = notifier;
        this.texts = texts;
        this.clock = clock;
    }

    void onText(Message message) {
        Long chatId = message.getChatId();
        Optional<ClientEntity> client = clients.findByTelegramUserId(message.getFrom().getId());
        if (client.isEmpty()) {
            sender.send(chatId, texts.welcome(), Keyboards.contactRequest());
            return;
        }
        switch (message.getText().trim()) {
            case "/start" -> sender.send(chatId, "С возвращением! Выберите действие в меню ниже.", Keyboards.mainMenu());
            case Keyboards.BOOK, "/book" -> send(chatId, services());
            case Keyboards.MY, "/my" -> send(chatId, myBookings(client.get()));
            case Keyboards.ABOUT, "/about" -> sender.send(chatId, texts.about(), Keyboards.mainMenu());
            default -> sender.send(chatId, "Выберите действие в меню ниже 👇", Keyboards.mainMenu());
        }
    }

    void onContact(Message message) {
        User from = message.getFrom();
        Contact contact = message.getContact();
        // Через «скрепку» можно отправить чужой контакт — принимаем только свой номер.
        if (!from.getId().equals(contact.getUserId())) {
            sender.send(message.getChatId(), "Отправьте, пожалуйста, свой номер — кнопкой ниже.",
                    Keyboards.contactRequest());
            return;
        }
        String name = from.getLastName() == null ? from.getFirstName() : from.getFirstName() + " " + from.getLastName();
        String phone = contact.getPhoneNumber().startsWith("+") ? contact.getPhoneNumber() : "+" + contact.getPhoneNumber();
        ClientEntity client = clients.findByTelegramUserId(from.getId())
                .map(existing -> {
                    existing.updateContact(message.getChatId(), name, phone, clock.instant());
                    return existing;
                })
                .orElseGet(() -> new ClientEntity(from.getId(), message.getChatId(), name, phone, clock.instant()));
        clients.save(client);
        sender.send(message.getChatId(), "Спасибо, " + from.getFirstName() + "! Теперь можно записаться 👇",
                Keyboards.mainMenu());
    }

    void onCallback(CallbackQuery query) {
        Long chatId = query.getMessage().getChatId();
        Optional<ClientEntity> client = clients.findByTelegramUserId(query.getFrom().getId());
        if (client.isEmpty()) {
            sender.send(chatId, texts.welcome(), Keyboards.contactRequest());
            return;
        }
        String[] p = query.getData().split(":");
        Screen screen = switch (p[0]) {
            case "s" -> services();
            case "m" -> masters(catalog.offering(p[1]));
            case "d" -> dates(catalog.offering(p[1]), p[2]);
            case "t" -> times(catalog.offering(p[1]), p[2], date(p[3]), null);
            case "c" -> confirm(catalog.offering(p[1]), p[2], date(p[3]), time(p[4]));
            case "ok" -> book(client.get(), catalog.offering(p[1]), p[2], date(p[3]), time(p[4]));
            case "my" -> myBookings(client.get());
            case "x" -> confirmCancel(client.get(), Long.parseLong(p[1]));
            case "xx" -> cancel(client.get(), Long.parseLong(p[1]));
            default -> throw new IllegalArgumentException("Неизвестная кнопка: " + query.getData());
        };
        sender.edit(chatId, query.getMessage().getMessageId(), screen.text(), screen.markup());
    }

    private Screen services() {
        List<InlineKeyboardButton> buttons = catalog.offerings().stream()
                .map(o -> button(texts.offeringButton(o), "m:" + o.code()))
                .toList();
        return new Screen("Выберите услугу:", grid(buttons, 1));
    }

    private Screen masters(Offering offering) {
        List<Master> masters = catalog.mastersFor(offering.code());
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        if (masters.size() > 1) {
            buttons.add(button("Любой свободный мастер", "d:" + offering.code() + ":" + SalonCatalog.ANY_MASTER));
        }
        masters.forEach(m -> buttons.add(button(m.name(), "d:" + offering.code() + ":" + m.code())));
        return new Screen(offering.name() + "\n\nВыберите мастера:", grid(buttons, 1, row(button("← Назад", "s"))));
    }

    private Screen dates(Offering offering, String masterCode) {
        String header = header(offering, masterCode) + "\n";
        InlineKeyboardRow back = row(button("← Назад", "m:" + offering.code()));
        List<LocalDate> dates = slots.availableDates(offering, masterCode);
        if (dates.isEmpty()) {
            return new Screen(header + "Свободного времени на ближайшие " + catalog.settings().bookingHorizonDays()
                    + " дней нет. Попробуйте другого мастера или " + texts.callUs() + ".", inline(back));
        }
        String prefix = "t:" + offering.code() + ":" + masterCode + ":";
        List<InlineKeyboardButton> buttons = dates.stream()
                .map(d -> button(texts.day(d), prefix + DATE.format(d)))
                .toList();
        return new Screen(header + "Выберите дату:", grid(buttons, 2, back));
    }

    private Screen times(Offering offering, String masterCode, LocalDate date, String notice) {
        String header = (notice == null ? "" : notice + "\n\n") + header(offering, masterCode) + "📅 " + texts.day(date) + "\n\n";
        InlineKeyboardRow back = row(button("← Другая дата", "d:" + offering.code() + ":" + masterCode));
        List<LocalTime> times = slots.freeStarts(offering, masterCode, date);
        if (times.isEmpty()) {
            return new Screen(header + "На этот день свободного времени уже нет.", inline(back));
        }
        String prefix = "c:" + offering.code() + ":" + masterCode + ":" + DATE.format(date) + ":";
        List<InlineKeyboardButton> buttons = times.stream()
                .map(t -> button(texts.time(t), prefix + TIME.format(t)))
                .toList();
        return new Screen(header + "Выберите время:", grid(buttons, 4, back));
    }

    private Screen confirm(Offering offering, String masterCode, LocalDate date, LocalTime time) {
        String data = offering.code() + ":" + masterCode + ":" + DATE.format(date) + ":" + TIME.format(time);
        String text = "Проверьте запись:\n\n"
                + "💇 " + offering.name() + "\n"
                + "👩 " + masterLabel(masterCode) + "\n"
                + "🗓 " + texts.day(date) + ", " + texts.time(time) + "\n"
                + (offering.price() == null ? "" : "💳 " + texts.price(offering.price()) + "\n");
        return new Screen(text, inline(
                row(button("✅ Записаться", "ok:" + data)),
                row(button("← Другое время", "t:" + offering.code() + ":" + masterCode + ":" + DATE.format(date)))));
    }

    private Screen book(ClientEntity client, Offering offering, String masterCode, LocalDate date, LocalTime time) {
        Result result = bookings.book(client, offering, masterCode, date, time);
        return switch (result.outcome()) {
            case BOOKED -> {
                notifier.newBooking(result.booking());
                yield Screen.of("✅ Вы записаны!\n\n" + texts.booking(result.booking())
                        + "\n\nНапомним за сутки и за 2 часа. Отменить запись можно в «Мои записи».");
            }
            case SLOT_TAKEN -> times(offering, masterCode, date, "⚠️ Это время только что заняли. Выберите другое.");
            case TOO_LATE -> times(offering, masterCode, date, "⚠️ На это время записаться уже нельзя. Выберите другое.");
            case LIMIT_REACHED -> Screen.of("У вас уже " + catalog.settings().maxActiveBookings()
                    + " предстоящие записи — это максимум. Отмените одну в «Мои записи» или " + texts.callUs() + ".");
            default -> throw new IllegalStateException("Неожиданный результат записи: " + result.outcome());
        };
    }

    private Screen myBookings(ClientEntity client) {
        List<BookingEntity> upcoming = bookings.upcomingOf(client.getId());
        if (upcoming.isEmpty()) {
            return new Screen("У вас нет предстоящих записей.", inline(row(button(Keyboards.BOOK, "s"))));
        }
        StringBuilder text = new StringBuilder("Ваши записи:");
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (BookingEntity b : upcoming) {
            text.append("\n\n").append(texts.booking(b));
            rows.add(row(button("❌ Отменить " + texts.when(b.getStartsAt()), "x:" + b.getId())));
        }
        return new Screen(text.toString(), inline(rows));
    }

    private Screen confirmCancel(ClientEntity client, Long bookingId) {
        return bookings.upcomingOf(client.getId()).stream()
                .filter(b -> b.getId().equals(bookingId))
                .findFirst()
                .map(b -> new Screen("Отменить запись?\n\n" + texts.booking(b), inline(
                        row(button("Да, отменить", "xx:" + b.getId())),
                        row(button("← Назад", "my")))))
                .orElseGet(() -> myBookings(client));
    }

    private Screen cancel(ClientEntity client, Long bookingId) {
        Result result = bookings.cancelByClient(client.getId(), bookingId);
        return switch (result.outcome()) {
            case CANCELLED -> {
                notifier.cancelledByClient(result.booking());
                yield new Screen("Запись отменена.\n\n" + texts.booking(result.booking()),
                        inline(row(button(Keyboards.BOOK, "s"))));
            }
            case TOO_LATE -> new Screen("До визита меньше " + catalog.settings().cancelBefore().toHours()
                    + " ч — отменить через бота уже нельзя. Пожалуйста, " + texts.callUs() + ".",
                    inline(row(button("← Мои записи", "my"))));
            default -> new Screen("Запись не найдена или уже отменена.", inline(row(button("← Мои записи", "my"))));
        };
    }

    private void send(Long chatId, Screen screen) {
        sender.send(chatId, screen.text(), screen.markup());
    }

    private String header(Offering offering, String masterCode) {
        return "💇 " + offering.name() + "\n👩 " + masterLabel(masterCode) + "\n";
    }

    private String masterLabel(String masterCode) {
        return SalonCatalog.ANY_MASTER.equals(masterCode) ? "Любой свободный мастер" : catalog.master(masterCode).name();
    }

    private static LocalDate date(String value) {
        return LocalDate.parse(value, DATE);
    }

    private static LocalTime time(String value) {
        return LocalTime.parse(value, TIME);
    }
}
