package dev.bookingbot.bot;

import java.util.ArrayList;
import java.util.List;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

final class Keyboards {

    static final String BOOK = "📅 Записаться";
    static final String MY = "🗂 Мои записи";
    static final String ABOUT = "ℹ️ О салоне";

    private Keyboards() {
    }

    static ReplyKeyboardMarkup mainMenu() {
        return ReplyKeyboardMarkup.builder()
                .keyboardRow(new KeyboardRow(BOOK, MY))
                .keyboardRow(new KeyboardRow(ABOUT))
                .resizeKeyboard(true)
                .isPersistent(true)
                .build();
    }

    static ReplyKeyboardMarkup contactRequest() {
        return ReplyKeyboardMarkup.builder()
                .keyboardRow(new KeyboardRow(KeyboardButton.builder()
                        .text("📱 Поделиться номером")
                        .requestContact(true)
                        .build()))
                .resizeKeyboard(true)
                .oneTimeKeyboard(true)
                .build();
    }

    static InlineKeyboardButton button(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    static InlineKeyboardRow row(InlineKeyboardButton... buttons) {
        return new InlineKeyboardRow(buttons);
    }

    static InlineKeyboardMarkup inline(List<InlineKeyboardRow> rows) {
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    static InlineKeyboardMarkup inline(InlineKeyboardRow... rows) {
        return inline(List.of(rows));
    }

    /** Кнопки сеткой по perRow в ряд, последним рядом — дополнительные (например, «Назад»). */
    static InlineKeyboardMarkup grid(List<InlineKeyboardButton> buttons, int perRow, InlineKeyboardRow... tail) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += perRow) {
            rows.add(new InlineKeyboardRow(buttons.subList(i, Math.min(i + perRow, buttons.size()))));
        }
        rows.addAll(List.of(tail));
        return inline(rows);
    }
}
