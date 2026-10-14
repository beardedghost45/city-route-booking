package com.cityroutebooking.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-booking-unconfigured;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.cors.allowed-origins=http://localhost:5173",
        "ADMIN_USERNAME=",
        "ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
class AdminBookingAuthenticationFailClosedTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void absentAdminCredentialsCannotAccessAdminDataButCustomerCreateRemainsAvailable() throws Exception {
        String guessedCredentials = "Basic " + Base64.getEncoder().encodeToString(
                "admin-test:test-secret".getBytes(StandardCharsets.UTF_8));
        mvc.perform(get("/api/admin/bookings"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/bookings").header("Authorization", guessedCredentials))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", "customer-no-admin-identity")
                        .contentType("application/json")
                        .content("""
                                {
                                  "origin": "Delhi",
                                  "destination": "Mumbai",
                                  "travelDate": "2027-03-14",
                                  "passengerCount": 1,
                                  "leadPassengerName": "Customer",
                                  "leadPassengerContact": "customer@example.test"
                                }
                                """))
                .andExpect(status().isCreated());
    }
}
