package io.github.kusevich.booking;

import io.github.kusevich.config.Config;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

@Epic("Booking API")
@Feature("Authentication")
@Tag("booking")
@DisplayName("Booking API — authentication")
class AuthTest {

    private final BookingApi api = new BookingApi();

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Valid credentials return a token")
    void validCredentialsReturnToken() {
        api.auth(Config.bookingUser(), Config.bookingPassword())
                .then()
                .statusCode(200)
                .body("token", not(blankOrNullString()));
    }

    @ParameterizedTest(name = "user=''{0}'', password=''{1}''")
    @CsvSource({
            "admin, wrong-password",
            "unknown-user, password123",
            "'', ''"
    })
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Invalid credentials never return a token")
    void invalidCredentialsAreRejected(String user, String password) {
        api.auth(user, password)
                .then()
                .statusCode(200)                       // the API reports auth errors in the body
                .body("reason", equalTo("Bad credentials"))
                .body("token", nullValue());
    }
}
