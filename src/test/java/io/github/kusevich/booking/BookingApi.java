package io.github.kusevich.booking;

import io.github.kusevich.booking.model.Booking;
import io.github.kusevich.config.Config;
import io.qameta.allure.Step;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

import static io.restassured.RestAssured.given;

/** Thin API layer: tests talk to methods, not to raw HTTP. Every call is attached to the Allure report. */
public final class BookingApi {

    private final RequestSpecification spec = new RequestSpecBuilder()
            .setBaseUri(Config.bookingBaseUrl())
            .setContentType(ContentType.JSON)
            .setAccept("application/json")
            .addFilter(new AllureRestAssured())
            .build();

    @Step("POST /auth as {user}")
    public Response auth(String user, String password) {
        return given(spec).body(Map.of("username", user, "password", password)).post("/auth");
    }

    @Step("Get auth token")
    public String token() {
        return auth(Config.bookingUser(), Config.bookingPassword())
                .then().statusCode(200)
                .extract().path("token");
    }

    @Step("POST /booking")
    public Response create(Booking booking) {
        return given(spec).body(booking).post("/booking");
    }

    @Step("GET /booking/{id}")
    public Response get(int id) {
        return given(spec).get("/booking/{id}", id);
    }

    @Step("GET /booking?firstname={firstname}&lastname={lastname}")
    public Response findByName(String firstname, String lastname) {
        return given(spec)
                .queryParam("firstname", firstname)
                .queryParam("lastname", lastname)
                .get("/booking");
    }

    @Step("PUT /booking/{id}")
    public Response update(int id, Booking booking, String token) {
        return withToken(token).body(booking).put("/booking/{id}", id);
    }

    @Step("PATCH /booking/{id}")
    public Response patch(int id, Map<String, Object> fields, String token) {
        return withToken(token).body(fields).patch("/booking/{id}", id);
    }

    @Step("DELETE /booking/{id}")
    public Response delete(int id, String token) {
        return withToken(token).delete("/booking/{id}", id);
    }

    private RequestSpecification withToken(String token) {
        RequestSpecification request = given(spec);
        return token == null ? request : request.cookie("token", token);
    }
}
