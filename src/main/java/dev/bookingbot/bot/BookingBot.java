package dev.bookingbot.bot;

import dev.bookingbot.config.BotProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Точка входа апдейтов. Long polling: не нужны домен и HTTPS, но запущен может быть только один
 * экземпляр бота — второй получит от Telegram 409 Conflict.
 */
@Component
@ConditionalOnProperty(name = "bot.enabled", havingValue = "true")
public class BookingBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingBot.class);

    private final BotProperties props;
    private final ClientFlow clientFlow;
    private final AdminFlow adminFlow;
    private final TelegramSender sender;
    private final TelegramClient telegramClient;

    public BookingBot(BotProperties props, ClientFlow clientFlow, AdminFlow adminFlow,
                      TelegramSender sender, TelegramClient telegramClient) {
        if (props.token() == null || props.token().isBlank()) {
            throw new IllegalStateException("BOT_TOKEN не задан — получите токен у @BotFather и пропишите в .env");
        }
        this.props = props;
        this.clientFlow = clientFlow;
        this.adminFlow = adminFlow;
        this.sender = sender;
        this.telegramClient = telegramClient;
    }

    @Override
    public String getBotToken() {
        return props.token();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                onCallback(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().isUserMessage()) {
                onMessage(update.getMessage());
            }
        } catch (RuntimeException e) {
            log.error("Ошибка обработки апдейта {}", update.getUpdateId(), e);
            Long chatId = chatIdOf(update);
            if (chatId != null) {
                sender.send(chatId, "Что-то пошло не так. Начните заново: /start", null);
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerCommands() {
        try {
            telegramClient.execute(SetMyCommands.builder()
                    .commands(List.of(
                            new BotCommand("book", "Записаться"),
                            new BotCommand("my", "Мои записи"),
                            new BotCommand("about", "О салоне")))
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Не удалось зарегистрировать меню команд: {}", e.getMessage());
        }
        log.info("Бот запущен. Администраторы: {}, демо-режим: {}", props.adminIds(), props.demoMode());
    }

    private void onCallback(CallbackQuery query) {
        sender.answer(query.getId());
        if (AdminFlow.isAdminCallback(query.getData())) {
            if (props.isAdmin(query.getFrom().getId())) {
                adminFlow.onCallback(query);
            }
            return;
        }
        clientFlow.onCallback(query);
    }

    private void onMessage(Message message) {
        if (message.hasContact()) {
            clientFlow.onContact(message);
        } else if (message.hasText()) {
            if (props.isAdmin(message.getFrom().getId()) && adminFlow.onCommand(message)) {
                return;
            }
            clientFlow.onText(message);
        }
    }

    private static Long chatIdOf(Update update) {
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return update.hasMessage() ? update.getMessage().getChatId() : null;
    }
}
