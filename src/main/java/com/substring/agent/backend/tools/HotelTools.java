package com.substring.agent.backend.tools;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class HotelTools {

    private static final int MAX_NIGHTS = 30;

    public record Hotel(String hotelId, String name, String city, String neighborhood, int stars,
            double guestRating, double pricePerNightUsd, int totalRooms, List<String> amenities) {
    }

    public record HotelBooking(String bookingReference, String hotelId, String hotelName, String city,
            String guestName, String checkIn, String checkOut, long nights, int rooms, double totalPriceUsd,
            String status) {
    }

    // Stay dates after validation, so the booking methods don't parse twice
    private record Stay(LocalDate checkIn, LocalDate checkOut, long nights) {
    }

    // Fake data: fictional hotels in the same cities FlightTools flies to
    private final Map<String, Hotel> hotels = List.of(
            new Hotel("H101", "Magnolia Grand", "Dallas", "Downtown", 4, 8.7, 210, 6, List.of("Pool", "Gym", "Free Wi-Fi", "Restaurant")),
            new Hotel("H102", "Deep Ellum Lofts", "Dallas", "Deep Ellum", 3, 8.2, 135, 5, List.of("Free Wi-Fi", "Kitchenette", "Bar")),
            new Hotel("H201", "Hudson Park Hotel", "New York", "Midtown", 4, 8.5, 320, 8, List.of("Gym", "Free Wi-Fi", "Rooftop bar")),
            new Hotel("H202", "The Bowery Nest", "New York", "Lower East Side", 3, 8.1, 210, 5, List.of("Free Wi-Fi", "Café")),
            new Hotel("H203", "Central Crown", "New York", "Upper East Side", 5, 9.3, 590, 4, List.of("Spa", "Gym", "Concierge", "Fine dining", "Free Wi-Fi")),
            new Hotel("H301", "Palm & Pacific", "Los Angeles", "Santa Monica", 4, 8.8, 285, 6, List.of("Pool", "Beach access", "Free Wi-Fi", "Parking")),
            new Hotel("H302", "Sunset Row Inn", "Los Angeles", "Hollywood", 3, 7.9, 165, 7, List.of("Pool", "Free Wi-Fi", "Parking")),
            new Hotel("H401", "Kensington Rose", "London", "Kensington", 4, 8.9, 290, 6, List.of("Free Wi-Fi", "Restaurant", "Afternoon tea")),
            new Hotel("H402", "Southbank Studios", "London", "Southbank", 3, 8.3, 175, 8, List.of("Free Wi-Fi", "Kitchenette")),
            new Hotel("H403", "The Mayfair Regent", "London", "Mayfair", 5, 9.4, 620, 3, List.of("Spa", "Butler service", "Fine dining", "Gym", "Free Wi-Fi")),
            new Hotel("H501", "Hôtel Lumière Marais", "Paris", "Le Marais", 4, 9.0, 260, 5, List.of("Free Wi-Fi", "Breakfast included", "Courtyard")),
            new Hotel("H502", "Montmartre Petit", "Paris", "Montmartre", 3, 8.4, 150, 6, List.of("Free Wi-Fi", "Café")),
            new Hotel("H601", "Shinjuku Skyline", "Tokyo", "Shinjuku", 4, 8.8, 230, 8, List.of("Free Wi-Fi", "Gym", "City views", "Restaurant")),
            new Hotel("H602", "Asakusa Ryokan Hana", "Tokyo", "Asakusa", 3, 9.1, 140, 4, List.of("Traditional rooms", "Onsen bath", "Breakfast included")),
            new Hotel("H603", "Ginza Imperial Suites", "Tokyo", "Ginza", 5, 9.5, 540, 3, List.of("Spa", "Concierge", "Fine dining", "Free Wi-Fi")),
            new Hotel("H701", "Marina Pearl", "Dubai", "Dubai Marina", 5, 9.0, 380, 6, List.of("Infinity pool", "Beach access", "Spa", "Free Wi-Fi")),
            new Hotel("H702", "Deira Souk Hotel", "Dubai", "Deira", 3, 7.8, 95, 10, List.of("Free Wi-Fi", "Airport shuttle")),
            new Hotel("H801", "Lodhi Garden Residency", "New Delhi", "Lodhi Colony", 4, 8.6, 120, 7, List.of("Pool", "Free Wi-Fi", "Restaurant")),
            new Hotel("H802", "Connaught Heritage", "New Delhi", "Connaught Place", 5, 9.2, 210, 5, List.of("Spa", "Pool", "Fine dining", "Free Wi-Fi")),
            new Hotel("H803", "Paharganj Comfort Inn", "New Delhi", "Paharganj", 2, 7.2, 35, 12, List.of("Free Wi-Fi")))
            .stream()
            .collect(Collectors.toMap(Hotel::hotelId, Function.identity()));

    private final Map<String, HotelBooking> bookings = new ConcurrentHashMap<>();
    private final AtomicInteger nextReference = new AtomicInteger(5001);

    @Tool(description = "Search hotels in a city, best rated first. Optionally filter by a maximum nightly price and minimum star rating. An empty list means no hotels match")
    public List<Hotel> searchHotels(
            String city,
            @ToolParam(description = "Maximum price per night in USD", required = false) Double maxPricePerNight,
            @ToolParam(description = "Minimum star rating from 1 to 5", required = false) Integer minStars) {
        return hotels.values().stream()
                .filter(h -> matchesCity(city, h.city()))
                .filter(h -> maxPricePerNight == null || h.pricePerNightUsd() <= maxPricePerNight)
                .filter(h -> minStars == null || h.stars() >= minStars)
                .sorted(Comparator.comparingDouble(Hotel::guestRating).reversed())
                .toList();
    }

    @Tool(description = "Get the full details of a hotel by its hotel id, e.g. H101")
    public String getHotelDetails(String hotelId) {
        Hotel hotel = hotels.get(hotelId.trim().toUpperCase());
        return hotel == null ? "Hotel not found: " + hotelId : hotel.toString();
    }

    @Tool(description = "Check whether a hotel has rooms free for the given dates, and what the stay would cost")
    public String checkHotelAvailability(
            String hotelId,
            @ToolParam(description = "Check-in date in yyyy-MM-dd format") String checkIn,
            @ToolParam(description = "Check-out date in yyyy-MM-dd format") String checkOut,
            @ToolParam(description = "Number of rooms needed") int rooms) {
        Hotel hotel = hotels.get(hotelId.trim().toUpperCase());
        if (hotel == null) {
            return "Hotel not found: " + hotelId;
        }
        if (rooms <= 0) {
            return "Number of rooms must be greater than zero";
        }
        Object stay = parseStay(checkIn, checkOut);
        if (stay instanceof String error) {
            return error;
        }
        Stay dates = (Stay) stay;
        int roomsLeft = roomsLeft(hotel, dates);
        double total = hotel.pricePerNightUsd() * dates.nights() * rooms;
        if (roomsLeft < rooms) {
            return hotel.name() + " only has " + roomsLeft + " room(s) free for those dates";
        }
        return hotel.name() + " has " + roomsLeft + " room(s) free. " + rooms + " room(s) for " + dates.nights()
                + " night(s) would cost $" + total;
    }

    @Tool(description = "Book rooms at a hotel for the given dates. Returns a booking reference")
    public synchronized String bookHotel(
            String hotelId,
            @ToolParam(description = "Full name of the guest") String guestName,
            @ToolParam(description = "Check-in date in yyyy-MM-dd format") String checkIn,
            @ToolParam(description = "Check-out date in yyyy-MM-dd format") String checkOut,
            @ToolParam(description = "Number of rooms, must be positive") int rooms) {
        Hotel hotel = hotels.get(hotelId.trim().toUpperCase());
        if (hotel == null) {
            return "Hotel not found: " + hotelId;
        }
        if (rooms <= 0) {
            return "Number of rooms must be greater than zero";
        }
        Object stay = parseStay(checkIn, checkOut);
        if (stay instanceof String error) {
            return error;
        }
        Stay dates = (Stay) stay;
        int roomsLeft = roomsLeft(hotel, dates);
        if (roomsLeft < rooms) {
            return "Not enough rooms at " + hotel.name() + ". Only " + roomsLeft + " free for those dates";
        }

        String reference = "HT-" + nextReference.getAndIncrement();
        HotelBooking booking = new HotelBooking(reference, hotel.hotelId(), hotel.name(), hotel.city(), guestName,
                dates.checkIn().toString(), dates.checkOut().toString(), dates.nights(), rooms,
                hotel.pricePerNightUsd() * dates.nights() * rooms, "CONFIRMED");
        bookings.put(reference, booking);
        return "Booking confirmed: " + booking;
    }

    @Tool(description = "Look up a hotel booking by its booking reference, e.g. HT-5001")
    public String getHotelBooking(String bookingReference) {
        HotelBooking booking = bookings.get(bookingReference.trim().toUpperCase());
        return booking == null ? "Booking not found: " + bookingReference : booking.toString();
    }

    @Tool(description = "Cancel a hotel booking by its booking reference, which frees up the rooms")
    public synchronized String cancelHotelBooking(String bookingReference) {
        String key = bookingReference.trim().toUpperCase();
        HotelBooking booking = bookings.get(key);
        if (booking == null) {
            return "Booking not found: " + bookingReference;
        }
        if ("CANCELLED".equals(booking.status())) {
            return "Booking " + key + " is already cancelled";
        }
        bookings.put(key, new HotelBooking(booking.bookingReference(), booking.hotelId(), booking.hotelName(),
                booking.city(), booking.guestName(), booking.checkIn(), booking.checkOut(), booking.nights(),
                booking.rooms(), booking.totalPriceUsd(), "CANCELLED"));
        return "Booking " + key + " has been cancelled";
    }

    // Counts rooms taken by any confirmed booking whose dates overlap the requested stay
    private int roomsLeft(Hotel hotel, Stay stay) {
        int taken = bookings.values().stream()
                .filter(b -> b.hotelId().equals(hotel.hotelId()) && "CONFIRMED".equals(b.status()))
                .filter(b -> LocalDate.parse(b.checkIn()).isBefore(stay.checkOut())
                        && stay.checkIn().isBefore(LocalDate.parse(b.checkOut())))
                .mapToInt(HotelBooking::rooms)
                .sum();
        return hotel.totalRooms() - taken;
    }

    // Returns a Stay when the dates are valid, otherwise an error message for the model
    private Object parseStay(String checkIn, String checkOut) {
        try {
            LocalDate in = LocalDate.parse(checkIn);
            LocalDate out = LocalDate.parse(checkOut);
            if (in.isBefore(LocalDate.now())) {
                return "Check-in date " + checkIn + " is in the past. Today is " + LocalDate.now();
            }
            long nights = ChronoUnit.DAYS.between(in, out);
            if (nights <= 0) {
                return "Check-out must be after check-in";
            }
            if (nights > MAX_NIGHTS) {
                return "Stays are limited to " + MAX_NIGHTS + " nights";
            }
            return new Stay(in, out, nights);
        } catch (DateTimeParseException | NullPointerException e) {
            return "Invalid dates. Use the yyyy-MM-dd format for check-in and check-out";
        }
    }

    private boolean matchesCity(String input, String city) {
        if (input == null || input.isBlank()) {
            return false;
        }
        String value = input.trim().toLowerCase();
        value = CITY_ALIASES.getOrDefault(value, value);
        return value.contains(city.toLowerCase());
    }

    // Nicknames and metro/alternate airport codes the model might use instead of the main airport code
    private static final Map<String, String> CITY_ALIASES = Map.of(
            "nyc", "new york",
            "ewr", "new york",
            "lga", "new york",
            "la", "los angeles",
            "dal", "dallas",
            "lon", "london",
            "par", "paris",
            "tyo", "tokyo",
            "nrt", "tokyo",
            "delhi", "new delhi");
}
