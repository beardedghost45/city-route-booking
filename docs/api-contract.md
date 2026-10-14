# Booking API contract (issue #3)

Base path: `/api/bookings`. Requests and responses use JSON. City names are case-insensitive and are returned in uppercase. The supported cities are Delhi, Mumbai, Hyderabad, Kolkata, and Bengaluru; origin and destination must differ.

## Browser access (CORS)

Configure the exact frontend origins with the comma-separated `FRONTEND_ORIGINS` environment variable. The default is the local Vite origin `http://localhost:5173`. For example:

```sh
FRONTEND_ORIGINS=http://localhost:5173,https://booking.example.com
```

Entries must be explicit HTTP(S) origins without paths; wildcard origins and wildcard subdomains are rejected. Set this variable to the deployment's specific frontend origins in production. The API allows `GET`, `POST`, and `OPTIONS`, and request headers `Content-Type`, `Idempotency-Key`, and `If-Match`. Browser responses expose `ETag` and `Location`. Credentials are not enabled.

## Create a booking

`POST /api/bookings`

Required header: `Idempotency-Key` (trimmed, 1–255 characters).

```http
POST /api/bookings
Idempotency-Key: web-form-2027-03-14-ada
Content-Type: application/json
```

```json
{
  "origin": "Delhi",
  "destination": "Mumbai",
  "travelDate": "2027-03-14",
  "passengerCount": 2,
  "leadPassengerName": "Ada Lovelace",
  "leadPassengerContact": "ada@example.test"
}
```

A new request returns `201 Created`, a `Location` header, and an `ETag`. The response body contains a public booking number and booking fields; it does not expose the database UUID, idempotency key, or request fingerprint.

```http
HTTP/1.1 201 Created
Location: /api/bookings/BK-1f75d5278fb24a6d99c287a1a2e18ce2
ETag: "BK-1f75d5278fb24a6d99c287a1a2e18ce2-v0"
Content-Type: application/json
```

```json
{
  "bookingNumber": "BK-1f75d5278fb24a6d99c287a1a2e18ce2",
  "origin": "DELHI",
  "destination": "MUMBAI",
  "travelDate": "2027-03-14",
  "passengerCount": 2,
  "leadPassengerName": "Ada Lovelace",
  "leadPassengerContact": "ada@example.test",
  "bookingStatus": "PENDING_PAYMENT",
  "paymentStatus": "PENDING",
  "refundStatus": "NONE",
  "createdAt": "2026-10-14T10:00:00Z",
  "updatedAt": "2026-10-14T10:00:00Z"
}
```

The idempotency key is global in this unauthenticated MVP and remains associated with the booking for its lifetime. Repeating the same normalized request with the same key returns `200 OK` with the original booking's current body and ETag. Reusing the key with a different normalized request returns `409 Conflict`.

## Read a booking

`GET /api/bookings/{bookingNumber}` returns `200 OK`, the booking response body above, and its current `ETag`. Unknown booking numbers return `404 Not Found`.

## Record a simulated payment result

`POST /api/bookings/{bookingNumber}/payment` requires the current `If-Match` value and accepts one of the following results:

```http
If-Match: "BK-1f75d5278fb24a6d99c287a1a2e18ce2-v0"
Content-Type: application/json
```

```json
{"result":"SUCCESS"}
```

Use `{"result":"FAILURE"}` to record a failed attempt. Success changes the booking to `CONFIRMED` with payment `SUCCEEDED`. Failure keeps the booking `PENDING_PAYMENT` with payment `FAILED`; a later simulated attempt may still succeed. Both successful state changes return `200 OK`, the updated body, and a new ETag. A payment result on a confirmed or cancelled booking returns `409 Conflict`.

## Cancel a booking

`POST /api/bookings/{bookingNumber}/cancellation` requires the current `If-Match` value. It returns `200 OK` and the updated body and ETag. The booking becomes `CANCELLED`; `refundStatus` becomes `PENDING` only if payment previously succeeded, otherwise it remains `NONE`. A second cancellation returns `409 Conflict`.

## Conditional updates and errors

ETags are strong tags in the form `"{bookingNumber}-v{version}"`. Send the exact current tag in `If-Match` for every payment or cancellation request.

- Missing `If-Match`: `428 Precondition Required`.
- An old tag, including a concurrent update that has already committed: `412 Precondition Failed`.
- Malformed or unsupported `If-Match` syntax: `400 Bad Request`.
- Invalid fields, unknown city, same-city route, or non-positive passenger count: `400 Bad Request`.
- Reused idempotency key with a different request or invalid state transition: `409 Conflict`.
- Unknown booking number: `404 Not Found`.

Error responses use the same JSON shape. `fieldErrors` is an empty object when an error is not associated with specific input fields.

```json
{
  "status": 412,
  "code": "precondition_failed",
  "message": "The booking changed after the supplied If-Match tag was issued",
  "fieldErrors": {}
}
```

Payment is only a demo status action: no payment provider, charge, amount, or actual money movement is implemented. Cancellation records only a refund-pending status when applicable; it does not issue a refund. The API does not calculate fares, enforce capacity, or require authentication.
