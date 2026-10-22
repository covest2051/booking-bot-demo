package dev.bookingbot.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "client")
public class ClientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_user_id", nullable = false, unique = true)
    private Long telegramUserId;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "consent_at", nullable = false)
    private Instant consentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ClientEntity() {
    }

    public ClientEntity(Long telegramUserId, Long chatId, String name, String phone, Instant now) {
        this.telegramUserId = telegramUserId;
        this.chatId = chatId;
        this.name = name;
        this.phone = phone;
        this.consentAt = now;
        this.createdAt = now;
    }

    /** Повторная отправка номера: обновляем контакты и время согласия. */
    public void updateContact(Long chatId, String name, String phone, Instant now) {
        this.chatId = chatId;
        this.name = name;
        this.phone = phone;
        this.consentAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getTelegramUserId() {
        return telegramUserId;
    }

    public Long getChatId() {
        return chatId;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }
}
