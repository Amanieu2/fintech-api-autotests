package io.github.kusevich.config;

/**
 * Test configuration. Every value can be overridden with -D system properties
 * or environment variables, so the same suite runs locally and in CI.
 */
public final class Config {

    private Config() {
    }

    public static String bookingBaseUrl() {
        return get("booking.baseUrl", "BOOKING_BASE_URL", "https://restful-booker.herokuapp.com");
    }

    public static String bookingUser() {
        return get("booking.user", "BOOKING_USER", "admin");
    }

    public static String bookingPassword() {
        return get("booking.password", "BOOKING_PASSWORD", "password123");
    }

    private static String get(String property, String env, String defaultValue) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(env);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
