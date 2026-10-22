package dev.bookingbot.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/** Экран диалога: текст и кнопки под ним. */
record Screen(String text, InlineKeyboardMarkup markup) {

    static Screen of(String text) {
        return new Screen(text, null);
    }
}
