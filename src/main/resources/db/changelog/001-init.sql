--liquibase formatted sql

--changeset bookingbot:001-init
-- btree_gist нужен, чтобы в EXCLUDE-ограничении сравнивать master_code через "=" вместе с диапазоном времени
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE client
(
    id               BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT       NOT NULL UNIQUE,
    chat_id          BIGINT       NOT NULL,
    name             VARCHAR(255) NOT NULL,
    phone            VARCHAR(32)  NOT NULL,
    consent_at       TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL
);

-- booking_no_overlap: у мастера не может быть двух активных записей, пересекающихся по времени.
-- Уникальный индекс по (мастер, начало) не спасает: 10:00 на 60 мин и 10:30 на 30 мин начинаются по-разному,
-- но пересекаются. Диапазон [начало, конец) — запись 11:00 сразу после 10:00–11:00 разрешена.
CREATE TABLE booking
(
    id              BIGSERIAL PRIMARY KEY,
    client_id       BIGINT      NOT NULL REFERENCES client (id),
    master_code     VARCHAR(16) NOT NULL,
    offering_code   VARCHAR(16) NOT NULL,
    starts_at       TIMESTAMPTZ NOT NULL,
    ends_at         TIMESTAMPTZ NOT NULL,
    status          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    cancelled_at    TIMESTAMPTZ,
    reminded_24h_at TIMESTAMPTZ,
    reminded_2h_at  TIMESTAMPTZ,
    CONSTRAINT booking_period_valid CHECK (starts_at < ends_at),
    CONSTRAINT booking_no_overlap EXCLUDE USING gist (
        master_code WITH =,
        tstzrange(starts_at, ends_at) WITH &&
    ) WHERE (status = 'ACTIVE')
);

CREATE INDEX booking_client_idx ON booking (client_id, starts_at);
CREATE INDEX booking_active_starts_at_idx ON booking (starts_at) WHERE status = 'ACTIVE';

CREATE TABLE time_off
(
    id          BIGSERIAL PRIMARY KEY,
    master_code VARCHAR(16) NOT NULL,
    starts_at   TIMESTAMPTZ NOT NULL,
    ends_at     TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT time_off_period_valid CHECK (starts_at < ends_at)
);

CREATE INDEX time_off_master_idx ON time_off (master_code, starts_at);
