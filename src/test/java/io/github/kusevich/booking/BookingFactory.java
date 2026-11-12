package io.github.kusevich.booking;

import io.github.kusevich.booking.model.Booking;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Generates unique, valid test data so tests never depend on each other. */
public final class BookingFactory {

    private static final List<String> FIRST_NAMES = List.of("Aziz", "Olga", "John", "Dilnoza", "Petr", "Maria");
    private static final List<String> LAST_NAMES = List.of("Karimov", "Ivanova", "Smith", "Rakhimova", "Novak");

    private BookingFactory() {
    }

    public static Booking random() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        LocalDate checkin = LocalDate.now().plusDays(rnd.nextInt(1, 60));
        LocalDate checkout = checkin.plusDays(rnd.nextInt(1, 14));
        String uniqueSuffix = Long.toString(System.nanoTime(), 36);
        return new Booking(
                FIRST_NAMES.get(rnd.nextInt(FIRST_NAMES.size())) + "-" + uniqueSuffix,
                LAST_NAMES.get(rnd.nextInt(LAST_NAMES.size())),
                rnd.nextInt(50, 2_000),
                rnd.nextBoolean(),
                new Booking.BookingDates(checkin.toString(), checkout.toString()),
                "Breakfast"
        );
    }
}
