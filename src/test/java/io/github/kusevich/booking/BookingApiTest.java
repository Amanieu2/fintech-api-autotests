package io.github.kusevich.booking;

import io.github.kusevich.booking.model.Booking;
import io.github.kusevich.booking.model.CreatedBooking;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("Booking API")
@Tag("booking")
@DisplayName("Booking API — CRUD, auth and contract checks")
class BookingApiTest {

    private static final BookingApi api = new BookingApi();
    private static String token;

    @BeforeAll
    static void authenticate() {
        token = api.token();
    }

    private static CreatedBooking createBooking(Booking booking) {
        return api.create(booking)
                .then().statusCode(200)
                .extract().as(CreatedBooking.class);
    }

    @Nested
    @Feature("Create")
    @DisplayName("Create booking")
    class Create {

        @Test
        @Severity(SeverityLevel.BLOCKER)
        @DisplayName("Created booking is returned with an id and identical data")
        void createReturnsSameData() {
            Booking booking = BookingFactory.random();

            CreatedBooking created = createBooking(booking);

            assertThat(created.bookingid()).isPositive();
            assertThat(created.booking()).isEqualTo(booking);
        }

        @Test
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("Create response matches the JSON schema contract")
        void createResponseMatchesSchema() {
            api.create(BookingFactory.random())
                    .then().statusCode(200)
                    .body(matchesJsonSchemaInClasspath("schemas/created-booking.json"));
        }
    }

    @Nested
    @Feature("Read")
    @DisplayName("Read booking")
    class Read {

        @Test
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("GET by id returns the stored booking")
        void getById() {
            Booking booking = BookingFactory.random();
            int id = createBooking(booking).bookingid();

            Booking stored = api.get(id).then().statusCode(200).extract().as(Booking.class);

            assertThat(stored).isEqualTo(booking);
        }

        @Test
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("Search by name finds the created booking")
        void searchByName() {
            Booking booking = BookingFactory.random();
            int id = createBooking(booking).bookingid();

            List<Integer> ids = api.findByName(booking.firstname(), booking.lastname())
                    .then().statusCode(200)
                    .extract().jsonPath().getList("bookingid", Integer.class);

            assertThat(ids).contains(id);
        }

        @Test
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("Unknown id returns 404")
        void unknownIdReturns404() {
            api.get(Integer.MAX_VALUE).then().statusCode(404);
        }
    }

    @Nested
    @Feature("Update")
    @DisplayName("Update booking")
    class Update {

        @Test
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("PUT with a valid token replaces the booking")
        void fullUpdate() {
            int id = createBooking(BookingFactory.random()).bookingid();
            Booking replacement = BookingFactory.random();

            Booking updated = api.update(id, replacement, token)
                    .then().statusCode(200)
                    .extract().as(Booking.class);

            assertThat(updated).isEqualTo(replacement);
            assertThat(api.get(id).as(Booking.class)).isEqualTo(replacement);
        }

        @Test
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("PATCH changes only the sent fields")
        void partialUpdate() {
            Booking original = BookingFactory.random();
            int id = createBooking(original).bookingid();

            Booking patched = api.patch(id, Map.of("firstname", "Patched"), token)
                    .then().statusCode(200)
                    .extract().as(Booking.class);

            assertThat(patched).isEqualTo(original.withFirstname("Patched"));
        }

        @Test
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("PUT without a token is forbidden and changes nothing")
        void updateWithoutTokenIsForbidden() {
            Booking original = BookingFactory.random();
            int id = createBooking(original).bookingid();

            api.update(id, BookingFactory.random(), null).then().statusCode(403);

            assertThat(api.get(id).as(Booking.class)).isEqualTo(original);
        }

        @Test
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("PUT with a forged token is forbidden")
        void updateWithForgedTokenIsForbidden() {
            int id = createBooking(BookingFactory.random()).bookingid();

            api.update(id, BookingFactory.random(), "forged-token").then().statusCode(403);
        }
    }

    @Nested
    @Feature("Delete")
    @DisplayName("Delete booking")
    class Delete {

        @Test
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("Deleted booking is no longer available")
        void deleteRemovesBooking() {
            int id = createBooking(BookingFactory.random()).bookingid();

            api.delete(id, token).then().statusCode(201);

            api.get(id).then().statusCode(404);
        }

        @Test
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("DELETE without a token is forbidden and booking survives")
        void deleteWithoutTokenIsForbidden() {
            int id = createBooking(BookingFactory.random()).bookingid();

            Response response = api.delete(id, null);

            assertThat(response.statusCode()).isEqualTo(403);
            api.get(id).then().statusCode(200);
        }
    }
}
