package dev.bookingbot.bot;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/** Обёртка над Telegram API: ошибки отправки логируются и не роняют обработку апдейта. */
@Component
public class TelegramSender {

    private static final Logger log = LoggerFactory.getLogger(TelegramSender.class);

    /** Лимит Telegram — 4096 символов, берём с запасом. */
    private static final int MAX_LENGTH = 4000;

    public enum Delivery { SENT, BLOCKED, FAILED }

    private final TelegramClient client;

    public TelegramSender(TelegramClient client) {
        this.client = client;
    }

    public Delivery send(Long chatId, String text, ReplyKeyboard markup) {
        List<String> parts = split(text);
        for (int i = 0; i < parts.size(); i++) {
            boolean last = i == parts.size() - 1;
            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text(parts.get(i))
                    .replyMarkup(last ? markup : null)
                    .build();
            Delivery delivery = execute(chatId, message);
            if (delivery != Delivery.SENT) {
                return delivery;
            }
        }
        return Delivery.SENT;
    }

    /** Меняет сообщение с кнопками на месте; если не вышло (старое сообщение) — шлёт новое. */
    public void edit(Long chatId, Integer messageId, String text, InlineKeyboardMarkup markup) {
        if (text.length() > MAX_LENGTH) {
            send(chatId, text, markup);
            return;
        }
        try {
            client.execute(EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(text)
                    .replyMarkup(markup)
                    .build());
        } catch (TelegramApiRequestException e) {
            if (e.getApiResponse() != null && e.getApiResponse().contains("message is not modified")) {
                return;
            }
            log.debug("Не удалось изменить сообщение {} в чате {}: {}", messageId, chatId, e.getMessage());
            send(chatId, text, markup);
        } catch (TelegramApiException e) {
            log.debug("Не удалось изменить сообщение {} в чате {}: {}", messageId, chatId, e.getMessage());
            send(chatId, text, markup);
        }
    }

    /** Убирает «часики» на нажатой кнопке. */
    public void answer(String callbackQueryId) {
        try {
            client.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackQueryId).build());
        } catch (TelegramApiException e) {
            log.debug("Не удалось ответить на callback {}: {}", callbackQueryId, e.getMessage());
        }
    }

    private Delivery execute(Long chatId, SendMessage message) {
        try {
            client.execute(message);
            return Delivery.SENT;
        } catch (TelegramApiRequestException e) {
            if (Integer.valueOf(403).equals(e.getErrorCode())) {
                log.info("Чат {} заблокировал бота", chatId);
                return Delivery.BLOCKED;
            }
            log.warn("Не удалось отправить сообщение в чат {}: {}", chatId, e.getMessage());
            return Delivery.FAILED;
        } catch (TelegramApiException e) {
            log.warn("Не удалось отправить сообщение в чат {}: {}", chatId, e.getMessage());
            return Delivery.FAILED;
        }
    }

    private static List<String> split(String text) {
        if (text.length() <= MAX_LENGTH) {
            return List.of(text);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (current.length() + line.length() + 1 > MAX_LENGTH && !current.isEmpty()) {
                parts.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line.length() > MAX_LENGTH ? line.substring(0, MAX_LENGTH) : line);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }
}
