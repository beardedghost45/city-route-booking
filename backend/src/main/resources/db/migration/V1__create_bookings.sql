CREATE TABLE bookings
(
    id                       UUID         PRIMARY KEY,
    booking_number           VARCHAR(35)  NOT NULL,
    origin                   VARCHAR(16)  NOT NULL,
    destination              VARCHAR(16)  NOT NULL,
    travel_date              DATE         NOT NULL,
    passenger_count          INTEGER      NOT NULL,
    lead_passenger_name      VARCHAR(200) NOT NULL,
    lead_passenger_contact   VARCHAR(254) NOT NULL,
    booking_status           VARCHAR(24)  NOT NULL,
    payment_status           VARCHAR(16)  NOT NULL,
    refund_status            VARCHAR(16)  NOT NULL,
    idempotency_key          VARCHAR(255) NOT NULL,
    request_fingerprint      VARCHAR(64)  NOT NULL,
    version                  BIGINT       NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uk_bookings_booking_number UNIQUE (booking_number),
    CONSTRAINT uk_bookings_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_bookings_booking_number_format
        CHECK (booking_number ~ '^BK-[0-9a-f]{32}$'),
    CONSTRAINT ck_bookings_origin
        CHECK (origin IN ('DELHI', 'MUMBAI', 'HYDERABAD', 'KOLKATA', 'BENGALURU')),
    CONSTRAINT ck_bookings_destination
        CHECK (destination IN ('DELHI', 'MUMBAI', 'HYDERABAD', 'KOLKATA', 'BENGALURU')),
    CONSTRAINT ck_bookings_distinct_route
        CHECK (origin <> destination),
    CONSTRAINT ck_bookings_passenger_count_positive
        CHECK (passenger_count > 0),
    CONSTRAINT ck_bookings_status
        CHECK (booking_status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_bookings_payment_status
        CHECK (payment_status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_bookings_refund_status
        CHECK (refund_status IN ('NONE', 'PENDING')),
    CONSTRAINT ck_bookings_lifecycle_consistency
        CHECK (
            (booking_status <> 'CONFIRMED' OR payment_status = 'SUCCEEDED')
            AND (payment_status <> 'SUCCEEDED' OR booking_status IN ('CONFIRMED', 'CANCELLED'))
            AND ((refund_status = 'PENDING') = (booking_status = 'CANCELLED' AND payment_status = 'SUCCEEDED'))
        ),
    CONSTRAINT ck_bookings_request_fingerprint
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$')
);
