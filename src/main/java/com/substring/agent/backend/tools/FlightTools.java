package com.substring.agent.backend.tools;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
public class FlightTools {

    public record Flight(String flightNumber, String airline, String fromCity, String fromAirport, String toCity,
            String toAirport, String departureTime, String arrivalTime, String duration, String stops,
            double priceUsd, int seatsPerDay) {
    }

    public record FlightOption(Flight flight, String travelDate, int seatsLeft) {
    }

    public record FlightBooking(String bookingReference, String flightNumber, String route, String travelDate,
            String departureTime, String passengerName, int passengers, double totalPriceUsd, String status) {
    }

    // Fake data: every flight runs daily. Times are local; "+1" means arrival the next day.
    private final Map<String, Flight> flights = List.of(
            new Flight("AA1024", "American Airlines", "Dallas", "DFW", "New York", "JFK", "07:00", "11:35", "3h 35m", "Nonstop", 189, 9),
            new Flight("DL455", "Delta", "Dallas", "DFW", "New York", "JFK", "13:20", "17:55", "3h 35m", "Nonstop", 164, 6),
            new Flight("AA1025", "American Airlines", "New York", "JFK", "Dallas", "DFW", "12:30", "15:40", "4h 10m", "Nonstop", 195, 9),
            new Flight("AA2210", "American Airlines", "Dallas", "DFW", "Los Angeles", "LAX", "09:15", "10:40", "3h 25m", "Nonstop", 142, 12),
            new Flight("AA2211", "American Airlines", "Los Angeles", "LAX", "Dallas", "DFW", "14:05", "19:10", "3h 05m", "Nonstop", 151, 12),
            new Flight("BA192", "British Airways", "Dallas", "DFW", "London", "LHR", "17:45", "08:50 +1", "9h 05m", "Nonstop", 812, 7),
            new Flight("BA193", "British Airways", "London", "LHR", "Dallas", "DFW", "10:15", "14:40", "10h 25m", "Nonstop", 845, 7),
            new Flight("BA178", "British Airways", "New York", "JFK", "London", "LHR", "19:30", "07:35 +1", "7h 05m", "Nonstop", 645, 10),
            new Flight("VS4", "Virgin Atlantic", "New York", "JFK", "London", "LHR", "21:00", "09:10 +1", "7h 10m", "Nonstop", 598, 8),
            new Flight("BA177", "British Airways", "London", "LHR", "New York", "JFK", "11:20", "14:15", "7h 55m", "Nonstop", 610, 10),
            new Flight("AF23", "Air France", "New York", "JFK", "Paris", "CDG", "18:15", "07:40 +1", "7h 25m", "Nonstop", 620, 9),
            new Flight("AF22", "Air France", "Paris", "CDG", "New York", "JFK", "10:35", "12:55", "8h 20m", "Nonstop", 655, 9),
            new Flight("BA304", "British Airways", "London", "LHR", "Paris", "CDG", "08:30", "10:50", "1h 20m", "Nonstop", 118, 20),
            new Flight("AF1081", "Air France", "Paris", "CDG", "London", "LHR", "17:10", "17:30", "1h 20m", "Nonstop", 105, 20),
            new Flight("NH105", "ANA", "Los Angeles", "LAX", "Tokyo", "HND", "11:40", "15:30 +1", "11h 50m", "Nonstop", 980, 8),
            new Flight("NH106", "ANA", "Tokyo", "HND", "Los Angeles", "LAX", "17:35", "11:20", "9h 45m", "Nonstop", 940, 8),
            new Flight("AA175", "American Airlines", "Dallas", "DFW", "Tokyo", "HND", "10:50", "14:55 +1", "14h 05m", "Nonstop", 1120, 6),
            new Flight("AA176", "American Airlines", "Tokyo", "HND", "Dallas", "DFW", "17:00", "15:30", "12h 30m", "Nonstop", 1085, 6),
            new Flight("AF274", "Air France", "Paris", "CDG", "Tokyo", "HND", "23:25", "19:05 +1", "12h 40m", "Nonstop", 870, 7),
            new Flight("EK202", "Emirates", "New York", "JFK", "Dubai", "DXB", "23:00", "19:45 +1", "12h 45m", "Nonstop", 890, 10),
            new Flight("EK201", "Emirates", "Dubai", "DXB", "New York", "JFK", "08:30", "14:20", "14h 50m", "Nonstop", 915, 10),
            new Flight("EK2", "Emirates", "London", "LHR", "Dubai", "DXB", "09:40", "20:15", "6h 35m", "Nonstop", 430, 14),
            new Flight("EK510", "Emirates", "Dubai", "DXB", "New Delhi", "DEL", "04:05", "08:55", "3h 20m", "Nonstop", 210, 16),
            new Flight("EK511", "Emirates", "New Delhi", "DEL", "Dubai", "DXB", "10:25", "12:40", "3h 45m", "Nonstop", 225, 16),
            new Flight("EK222", "Emirates", "Dallas", "DFW", "New Delhi", "DEL", "16:15", "08:55 +2", "25h 10m", "1 stop (Dubai)", 1040, 8),
            new Flight("AI162", "Air India", "London", "LHR", "New Delhi", "DEL", "11:30", "00:55 +1", "8h 55m", "Nonstop", 540, 12),
            new Flight("AI161", "Air India", "New Delhi", "DEL", "London", "LHR", "14:00", "18:25", "9h 55m", "Nonstop", 560, 12))
            .stream()
            .collect(Collectors.toMap(Flight::flightNumber, Function.identity()));

    private final Map<String, FlightBooking> bookings = new ConcurrentHashMap<>();
    private final AtomicInteger nextReference = new AtomicInteger(1001);

    @Tool(description = "Search flights between two cities on a travel date, cheapest first. Cities can be names (Dallas) or airport codes (DFW). An empty list means there is no route between them")
    public List<FlightOption> searchFlights(
            @ToolParam(description = "Departure city or airport code") String fromCity,
            @ToolParam(description = "Destination city or airport code") String toCity,
            @ToolParam(description = "Travel date in yyyy-MM-dd format") String travelDate) {
        return flights.values().stream()
                .filter(f -> matchesCity(fromCity, f.fromCity(), f.fromAirport())
                        && matchesCity(toCity, f.toCity(), f.toAirport()))
                .sorted(Comparator.comparingDouble(Flight::priceUsd))
                .map(f -> new FlightOption(f, travelDate, seatsLeft(f, travelDate)))
                .toList();
    }

    @Tool(description = "List every destination with a flight from the given city, useful when the traveller hasn't picked a destination yet")
    public List<String> listDestinationsFrom(@ToolParam(description = "Departure city or airport code") String fromCity) {
        return flights.values().stream()
                .filter(f -> matchesCity(fromCity, f.fromCity(), f.fromAirport()))
                .map(f -> f.toCity() + " (" + f.toAirport() + ")")
                .distinct()
                .sorted()
                .toList();
    }

    @Tool(description = "Get the full details of a flight by its flight number")
    public String getFlightDetails(String flightNumber) {
        Flight flight = flights.get(normalizeFlightNumber(flightNumber));
        if (flight == null) {
            return "Flight not found: " + flightNumber;
        }
        return flight.toString();
    }

    @Tool(description = "Book seats on a flight for a travel date. Returns a booking reference")
    public synchronized String bookFlight(
            String flightNumber,
            @ToolParam(description = "Travel date in yyyy-MM-dd format") String travelDate,
            @ToolParam(description = "Full name of the lead passenger") String passengerName,
            @ToolParam(description = "Number of passengers, must be positive") int passengers) {
        Flight flight = flights.get(normalizeFlightNumber(flightNumber));
        if (flight == null) {
            return "Flight not found: " + flightNumber;
        }
        String dateError = validateTravelDate(travelDate);
        if (dateError != null) {
            return dateError;
        }
        if (passengers <= 0) {
            return "Number of passengers must be greater than zero";
        }
        int seatsLeft = seatsLeft(flight, travelDate);
        if (seatsLeft < passengers) {
            return "Not enough seats on " + flight.flightNumber() + " on " + travelDate + ". Only " + seatsLeft
                    + " left";
        }

        String reference = "FL-" + nextReference.getAndIncrement();
        FlightBooking booking = new FlightBooking(reference, flight.flightNumber(),
                flight.fromCity() + " (" + flight.fromAirport() + ") to " + flight.toCity() + " (" + flight.toAirport() + ")",
                travelDate, flight.departureTime(), passengerName, passengers, flight.priceUsd() * passengers,
                "CONFIRMED");
        bookings.put(reference, booking);
        return "Booking confirmed: " + booking;
    }

    @Tool(description = "Look up a flight booking by its booking reference, e.g. FL-1001")
    public String getFlightBooking(String bookingReference) {
        FlightBooking booking = bookings.get(bookingReference.trim().toUpperCase());
        return booking == null ? "Booking not found: " + bookingReference : booking.toString();
    }

    @Tool(description = "Cancel a flight booking by its booking reference, which frees up the seats")
    public synchronized String cancelFlightBooking(String bookingReference) {
        String key = bookingReference.trim().toUpperCase();
        FlightBooking booking = bookings.get(key);
        if (booking == null) {
            return "Booking not found: " + bookingReference;
        }
        if ("CANCELLED".equals(booking.status())) {
            return "Booking " + key + " is already cancelled";
        }
        bookings.put(key, new FlightBooking(booking.bookingReference(), booking.flightNumber(), booking.route(),
                booking.travelDate(), booking.departureTime(), booking.passengerName(), booking.passengers(),
                booking.totalPriceUsd(), "CANCELLED"));
        return "Booking " + key + " has been cancelled";
    }

    private int seatsLeft(Flight flight, String travelDate) {
        int booked = bookings.values().stream()
                .filter(b -> b.flightNumber().equals(flight.flightNumber())
                        && b.travelDate().equals(travelDate)
                        && "CONFIRMED".equals(b.status()))
                .mapToInt(FlightBooking::passengers)
                .sum();
        return flight.seatsPerDay() - booked;
    }

    // Accepts the airport code, the city name, or text containing it ("Dallas, TX"), plus the aliases below
    private boolean matchesCity(String input, String city, String airportCode) {
        if (input == null || input.isBlank()) {
            return false;
        }
        String value = input.trim().toLowerCase();
        value = CITY_ALIASES.getOrDefault(value, value);
        return value.equals(airportCode.toLowerCase()) || value.contains(city.toLowerCase());
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

    private String normalizeFlightNumber(String flightNumber) {
        return flightNumber == null ? "" : flightNumber.replace(" ", "").toUpperCase();
    }

    private String validateTravelDate(String travelDate) {
        try {
            LocalDate date = LocalDate.parse(travelDate);
            if (date.isBefore(LocalDate.now())) {
                return "Travel date " + travelDate + " is in the past. Today is " + LocalDate.now();
            }
            return null;
        } catch (DateTimeParseException | NullPointerException e) {
            return "Invalid travel date: " + travelDate + ". Use the yyyy-MM-dd format";
        }
    }
}
