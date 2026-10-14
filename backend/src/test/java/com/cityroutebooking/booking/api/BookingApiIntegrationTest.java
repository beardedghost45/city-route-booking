package com.cityroutebooking.booking.api;

import com.cityroutebooking.booking.persistence.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.cors.allowed-origins=http://localhost:5173,https://booking.example.test"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class BookingApiIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    private static final String CREATE_BODY = """
            {
              "origin": "Delhi",
              "destination": "Mumbai",
              "travelDate": "2027-03-14",
              "passengerCount": 2,
              "leadPassengerName": "Ada Lovelace",
              "leadPassengerContact": "ada@example.test"
            }
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BookingRepository repository;

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void clearBookings() {
        repository.deleteAll();
    }

    @Test
    void createGetAndIdempotencyReturnOpaqueBookingAndCurrentEtag() throws Exception {
        MvcResult created = mvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", "  create-ada  ")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingNumber").value(org.hamcrest.Matchers.matchesRegex("BK-[0-9a-f]{32}")))
                .andExpect(jsonPath("$.bookingStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.requestFingerprint").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andReturn();

        String bookingNumber = json(created, "bookingNumber");
        String initialTag = created.getResponse().getHeader("ETag");
        assertThat(initialTag).isEqualTo("\"" + bookingNumber + "-v0\"");
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/bookings/" + bookingNumber);

        mvc.perform(get("/api/bookings/{bookingNumber}", bookingNumber))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", initialTag))
                .andExpect(jsonPath("$.leadPassengerName").value("Ada Lovelace"));

        String normalizedBody = CREATE_BODY.replace("Delhi", "  DELHI ")
                .replace("Mumbai", "mumbai")
                .replace("Ada Lovelace", "  Ada Lovelace  ")
                .replace("ada@example.test", " ada@example.test ");
        MvcResult replay = mvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", "create-ada")
                        .contentType("application/json")
                        .content(normalizedBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingNumber").value(bookingNumber))
                .andReturn();
        assertThat(replay.getResponse().getHeader("ETag")).isEqualTo(initialTag);

        mvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", "create-ada")
                        .contentType("application/json")
                        .content(CREATE_BODY.replace("\"passengerCount\": 2", "\"passengerCount\": 3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("idempotency_conflict"));
    }

    @Test
    void acceptsEveryDistinctPairAmongTheFiveSupportedCities() throws Exception {
        List<String> cities = List.of("DELHI", "MUMBAI", "HYDERABAD", "KOLKATA", "BENGALURU");
        Set<String> bookingNumbers = new LinkedHashSet<>();

        for (String origin : cities) {
            for (String destination : cities) {
                if (origin.equals(destination)) {
                    continue;
                }
                String originInput = bookingNumbers.size() % 2 == 0
                        ? origin.toLowerCase(java.util.Locale.ROOT)
                        : origin;
                String body = """
                        {
                          "origin": "%s",
                          "destination": "%s",
                          "travelDate": "2027-03-14",
                          "passengerCount": 1,
                          "leadPassengerName": "Route Tester",
                          "leadPassengerContact": "route@example.test"
                        }
                        """.formatted(originInput, destination);

                MvcResult created = mvc.perform(post("/api/bookings")
                                .header("Idempotency-Key", "route-" + origin + "-" + destination)
                                .contentType("application/json")
                                .content(body))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.origin").value(origin))
                        .andExpect(jsonPath("$.destination").value(destination))
                        .andExpect(jsonPath("$.bookingNumber")
                                .value(org.hamcrest.Matchers.matchesRegex("BK-[0-9a-f]{32}")))
                        .andReturn();
                bookingNumbers.add(json(created, "bookingNumber"));
            }
        }

        assertThat(bookingNumbers).hasSize(20);
    }

    @Test
    void validatesCreateAndRequiresIdempotencyKey() throws Exception {
        mvc.perform(post("/api/bookings").contentType("application/json").content(CREATE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));

        mvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", "missing-fields")
                        .contentType("application/json")
                        .content("""
                                {"passengerCount":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.origin").exists())
                .andExpect(jsonPath("$.fieldErrors.destination").exists())
                .andExpect(jsonPath("$.fieldErrors.passengerCount").exists())
                .andExpect(jsonPath("$.fieldErrors.travelDate").exists())
                .andExpect(jsonPath("$.fieldErrors.leadPassengerName").exists())
                .andExpect(jsonPath("$.fieldErrors.leadPassengerContact").exists());

        mvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", "same-city")
                        .contentType("application/json")
                        .content(CREATE_BODY.replace("Mumbai", "Delhi")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.destination").exists());

        mvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", "unsupported-route")
                        .contentType("application/json")
                        .content(CREATE_BODY.replace("Delhi", "Chennai")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.origin").exists());
    }

    @Test
    void paymentAndCancellationFollowSimulatedLifecycleAndConditionalUpdates() throws Exception {
        MvcResult created = create("lifecycle-ada");
        String bookingNumber = json(created, "bookingNumber");
        String initialTag = created.getResponse().getHeader("ETag");

        mvc.perform(post("/api/bookings/{bookingNumber}/cancellation", bookingNumber))
                .andExpect(status().isPreconditionRequired());

        mvc.perform(post("/api/bookings/{bookingNumber}/payment", bookingNumber)
                        .contentType("application/json")
                        .content("{\"result\":\"FAILURE\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("precondition_required"));

        MvcResult failed = mvc.perform(post("/api/bookings/{bookingNumber}/payment", bookingNumber)
                        .header("If-Match", initialTag)
                        .contentType("application/json")
                        .content("{\"result\":\"FAILURE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.paymentStatus").value("FAILED"))
                .andReturn();
        String failedTag = failed.getResponse().getHeader("ETag");
        assertThat(failedTag).isEqualTo("\"" + bookingNumber + "-v1\"").isNotEqualTo(initialTag);
        mvc.perform(get("/api/bookings/{bookingNumber}", bookingNumber))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", failedTag))
                .andExpect(jsonPath("$.paymentStatus").value("FAILED"));

        mvc.perform(post("/api/bookings/{bookingNumber}/cancellation", bookingNumber)
                        .header("If-Match", initialTag))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("precondition_failed"));

        MvcResult paid = mvc.perform(post("/api/bookings/{bookingNumber}/payment", bookingNumber)
                        .header("If-Match", failedTag)
                        .contentType("application/json")
                        .content("{\"result\":\"SUCCESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentStatus").value("SUCCEEDED"))
                .andReturn();

        MvcResult cancelled = mvc.perform(post("/api/bookings/{bookingNumber}/cancellation", bookingNumber)
                        .header("If-Match", paid.getResponse().getHeader("ETag")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.refundStatus").value("PENDING"))
                .andReturn();

        MvcResult replayedAfterChanges = mvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", "lifecycle-ada")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.refundStatus").value("PENDING"))
                .andReturn();
        assertThat(replayedAfterChanges.getResponse().getHeader("ETag"))
                .isEqualTo(cancelled.getResponse().getHeader("ETag"));

        mvc.perform(post("/api/bookings/{bookingNumber}/cancellation", bookingNumber)
                        .header("If-Match", cancelled.getResponse().getHeader("ETag")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invalid_state_transition"));

        mvc.perform(post("/api/bookings/{bookingNumber}/payment", bookingNumber)
                        .header("If-Match", cancelled.getResponse().getHeader("ETag"))
                        .contentType("application/json")
                        .content("{\"result\":\"SUCCESS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invalid_state_transition"));

        MvcResult unpaid = create("lifecycle-unpaid");
        mvc.perform(post("/api/bookings/{bookingNumber}/cancellation", json(unpaid, "bookingNumber"))
                        .header("If-Match", unpaid.getResponse().getHeader("ETag")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.refundStatus").value("NONE"));
    }

    @Test
    void concurrentMutationsUsingTheSameEtagCannotBothSucceed() throws Exception {
        MvcResult created = create("concurrent-ada");
        String bookingNumber = json(created, "bookingNumber");
        String tag = created.getResponse().getHeader("ETag");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> success = executor.submit(() -> concurrentPayment(start, bookingNumber, tag, "SUCCESS"));
            Future<Integer> failure = executor.submit(() -> concurrentPayment(start, bookingNumber, tag, "FAILURE"));
            start.countDown();
            List<Integer> statuses = List.of(success.get(), failure.get());
            assertThat(statuses).containsExactlyInAnyOrder(200, 412);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentDuplicateCreateRequestsReturnOneBookingAndOneRow() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = executor.submit(() -> concurrentCreate(start, "concurrent-create-same-key"));
            Future<MvcResult> second = executor.submit(() -> concurrentCreate(start, "concurrent-create-same-key"));
            start.countDown();

            MvcResult firstResult = first.get();
            MvcResult secondResult = second.get();
            assertThat(List.of(firstResult.getResponse().getStatus(), secondResult.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 201);
            assertThat(json(firstResult, "bookingNumber")).isEqualTo(json(secondResult, "bookingNumber"));
            assertThat(repository.count()).isEqualTo(1);
            assertThat(repository.findByIdempotencyKey("concurrent-create-same-key")).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void missingBookingUsesConsistentNotFoundError() throws Exception {
        mvc.perform(get("/api/bookings/BK-00000000000000000000000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking_not_found"));
    }

    @Test
    void corsPreflightAllowsConfiguredFrontendAndRejectsUnrelatedOrigin() throws Exception {
        MvcResult allowed = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .options("/api/bookings")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,idempotency-key,if-match"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(allowed.getResponse().getHeader("Access-Control-Allow-Origin"))
                .isEqualTo("http://localhost:5173");
        assertThat(allowed.getResponse().getHeader("Access-Control-Allow-Methods"))
                .contains("GET", "POST", "OPTIONS");
        assertThat(allowed.getResponse().getHeader("Access-Control-Allow-Headers").toLowerCase(java.util.Locale.ROOT))
                .contains("content-type", "idempotency-key", "if-match");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .options("/api/bookings")
                        .header("Origin", "https://unrelated.example.test")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,idempotency-key,if-match"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

        MvcResult created = mvc.perform(post("/api/bookings")
                        .header("Origin", "https://booking.example.test")
                        .header("Idempotency-Key", "cors-exposed-headers")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(created.getResponse().getHeader("Access-Control-Allow-Origin"))
                .isEqualTo("https://booking.example.test");
        assertThat(created.getResponse().getHeader("Access-Control-Expose-Headers"))
                .contains("ETag", "Location");
    }

    private MvcResult create(String idempotencyKey) throws Exception {
        return mvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private Integer concurrentPayment(CountDownLatch start, String bookingNumber, String tag, String result) {
        try {
            start.await();
            return mvc.perform(post("/api/bookings/{bookingNumber}/payment", bookingNumber)
                            .header("If-Match", tag)
                            .contentType("application/json")
                            .content("{\"result\":\"" + result + "\"}"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private MvcResult concurrentCreate(CountDownLatch start, String idempotencyKey) {
        try {
            start.await();
            return mvc.perform(post("/api/bookings")
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType("application/json")
                            .content(CREATE_BODY))
                    .andReturn();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String json(MvcResult result, String field) throws Exception {
        return com.jayway.jsonpath.JsonPath.parse(result.getResponse().getContentAsString())
                .read("$." + field, String.class);
    }
}
