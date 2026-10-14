package com.cityroutebooking.booking.api;

import com.cityroutebooking.booking.domain.Booking;
import com.cityroutebooking.booking.domain.BookingRequestFingerprint;
import com.cityroutebooking.booking.domain.City;
import com.cityroutebooking.booking.persistence.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-booking-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.cors.allowed-origins=http://localhost:5173",
        "ADMIN_USERNAME=admin-test",
        "ADMIN_PASSWORD=test-secret"
})
@AutoConfigureMockMvc
class AdminBookingApiIntegrationTest {

    private static final String CUSTOMER_REQUEST = """
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearBookings() {
        repository.deleteAll();
    }

    @Test
    void adminRequiresBasicAuthAndReturnsStablePublicPaginatedData() throws Exception {
        List<Booking> bookings = new ArrayList<>();
        for (int index = 0; index < 23; index++) {
            LocalDate travelDate = LocalDate.of(2027, 3, 14);
            String name = "Admin Passenger " + index;
            String contact = "admin" + index + "@example.test";
            String idempotencyKey = "admin-booking-" + index;
            int passengerCount = index + 1;
            bookings.add(Booking.create(
                    City.DELHI,
                    City.MUMBAI,
                    travelDate,
                    passengerCount,
                    name,
                    contact,
                    idempotencyKey,
                    BookingRequestFingerprint.calculate(
                            City.DELHI, City.MUMBAI, travelDate, passengerCount, name, contact)));
        }
        repository.saveAll(bookings);
        repository.flush();
        jdbcTemplate.update(
                "UPDATE bookings SET created_at = ?",
                Timestamp.from(Instant.parse("2027-01-01T00:00:00Z")));

        List<String> expectedBookingNumbers = bookings.stream()
                .map(Booking::getBookingNumber)
                .sorted(Comparator.reverseOrder())
                .toList();

        mvc.perform(get("/api/admin/bookings"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/bookings").header("Authorization", basic("admin-test", "wrong-secret")))
                .andExpect(status().isUnauthorized());

        MvcResult firstPage = mvc.perform(get("/api/admin/bookings")
                        .header("Authorization", basic("admin-test", "test-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(23))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].bookingNumber").value(expectedBookingNumbers.get(0)))
                .andExpect(jsonPath("$.items[0].id").doesNotExist())
                .andReturn();
        assertThat(firstPage.getResponse().getContentAsString())
                .contains("leadPassengerContact", "bookingStatus", "paymentStatus", "refundStatus", "createdAt", "updatedAt")
                .doesNotContain("idempotencyKey", "requestFingerprint", "\"version\"");

        mvc.perform(get("/api/admin/bookings")
                        .queryParam("page", "1")
                        .queryParam("size", "5")
                        .header("Authorization", basic("admin-test", "test-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(23))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].bookingNumber").value(expectedBookingNumbers.get(5)));

        mvc.perform(get("/api/admin/bookings")
                        .queryParam("size", "100")
                        .header("Authorization", basic("admin-test", "test-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(23));
        mvc.perform(get("/api/admin/bookings")
                        .queryParam("size", "101")
                        .header("Authorization", basic("admin-test", "test-secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.size").exists());
        mvc.perform(get("/api/admin/bookings")
                        .queryParam("page", "not-a-number")
                        .header("Authorization", basic("admin-test", "test-secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.page").exists());
        mvc.perform(get("/api/admin/bookings")
                        .queryParam("size", "not-a-number")
                        .header("Authorization", basic("admin-test", "test-secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.size").exists());
        mvc.perform(get("/api/admin/bookings")
                        .queryParam("size", "0")
                        .header("Authorization", basic("admin-test", "test-secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.size").exists());
        mvc.perform(get("/api/admin/bookings")
                        .queryParam("page", "-1")
                        .header("Authorization", basic("admin-test", "test-secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.page").exists());

        // Customer routes remain public; admin credentials are not required for customer flows.
        mvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", "customer-without-admin-auth")
                        .contentType("application/json")
                        .content(CUSTOMER_REQUEST))
                .andExpect(status().isCreated());
    }

    @Test
    void corsPreflightAllowsAuthorizationOnlyForConfiguredOrigin() throws Exception {
        MvcResult allowed = mvc.perform(options("/api/admin/bookings")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(allowed.getResponse().getHeader("Access-Control-Allow-Origin"))
                .isEqualTo("http://localhost:5173");
        assertThat(allowed.getResponse().getHeader("Access-Control-Allow-Headers").toLowerCase(java.util.Locale.ROOT))
                .contains("authorization");

        mvc.perform(options("/api/admin/bookings")
                        .header("Origin", "https://unrelated.example.test")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private String basic(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
