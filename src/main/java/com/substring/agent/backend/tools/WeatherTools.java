package com.substring.agent.backend.tools;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class WeatherTools {

    private static final int MAX_FORECAST_DAYS = 14;

    // Monthly averages (January first) in Celsius, plus the months that are usually wet
    private record Climate(int[] avgHighC, int[] avgLowC, Set<Integer> wetMonths, String bestTimeToVisit) {
    }

    public record DailyWeather(String date, String condition, int highC, int lowC, int highF, int lowF,
            int chanceOfRainPercent) {
    }

    public record MonthlyClimate(String city, String month, int avgHighC, int avgLowC, int avgHighF, int avgLowF,
            String typicalWeather) {
    }

    // Fake data: rough real-world averages for the cities FlightTools and HotelTools cover
    private final Map<String, Climate> climates = Map.of(
            "dallas", new Climate(
                    new int[] { 14, 16, 21, 25, 29, 34, 36, 36, 32, 26, 19, 14 },
                    new int[] { 3, 5, 9, 13, 18, 22, 24, 24, 20, 14, 8, 3 },
                    Set.of(5, 6, 10),
                    "March to May and September to November: warm and pleasant, before and after the summer heat"),
            "new york", new Climate(
                    new int[] { 4, 6, 10, 17, 22, 27, 29, 28, 24, 18, 12, 6 },
                    new int[] { -3, -2, 2, 7, 13, 18, 21, 21, 17, 10, 5, 0 },
                    Set.of(3, 4, 7),
                    "April to June and September to early November: mild weather, fall colours in October"),
            "los angeles", new Climate(
                    new int[] { 20, 20, 21, 22, 23, 25, 28, 29, 28, 26, 23, 20 },
                    new int[] { 9, 10, 11, 12, 14, 16, 18, 18, 17, 15, 11, 9 },
                    Set.of(1, 2, 3),
                    "March to May and September to November: sunny and warm with fewer crowds"),
            "london", new Climate(
                    new int[] { 8, 9, 12, 15, 18, 21, 24, 23, 20, 16, 11, 8 },
                    new int[] { 2, 2, 4, 6, 9, 12, 14, 14, 12, 9, 5, 3 },
                    Set.of(1, 10, 11, 12),
                    "May to September: longest days and mildest weather, though rain is possible any time"),
            "paris", new Climate(
                    new int[] { 7, 9, 13, 16, 20, 23, 25, 25, 21, 16, 11, 8 },
                    new int[] { 3, 3, 5, 7, 11, 14, 16, 16, 13, 10, 6, 3 },
                    Set.of(5, 10, 11, 12),
                    "April to June and September to October: mild, with fewer crowds than in summer"),
            "tokyo", new Climate(
                    new int[] { 10, 10, 14, 19, 23, 26, 30, 31, 27, 22, 17, 12 },
                    new int[] { 1, 2, 5, 10, 15, 19, 23, 24, 21, 15, 9, 4 },
                    Set.of(6, 7, 9, 10),
                    "Late March to May for cherry blossoms, or October to November for autumn leaves. June is rainy season"),
            "dubai", new Climate(
                    new int[] { 24, 25, 29, 33, 38, 40, 41, 41, 39, 35, 30, 26 },
                    new int[] { 14, 15, 18, 21, 25, 28, 30, 30, 28, 24, 19, 16 },
                    Set.of(),
                    "November to March: warm and sunny. June to September is extremely hot"),
            "new delhi", new Climate(
                    new int[] { 20, 24, 29, 36, 40, 39, 35, 34, 34, 33, 28, 23 },
                    new int[] { 7, 10, 15, 21, 26, 28, 27, 27, 25, 19, 13, 8 },
                    Set.of(7, 8, 9),
                    "October to March: dry and comfortable. April to June is very hot, July to September is monsoon"));

    @Tool(description = "Get today's weather in a city")
    public Object getCurrentWeather(String city) {
        String key = findCity(city);
        if (key == null) {
            return unknownCity(city);
        }
        return dailyWeather(key, LocalDate.now());
    }

    @Tool(description = "Get a day-by-day weather forecast for a city for up to 14 days. For dates further ahead it returns the typical weather for that month instead")
    public Object getWeatherForecast(
            String city,
            @ToolParam(description = "First day of the forecast in yyyy-MM-dd format. Defaults to today", required = false) String startDate,
            @ToolParam(description = "Number of days to forecast, from 1 to 14") int days) {
        String key = findCity(city);
        if (key == null) {
            return unknownCity(city);
        }
        LocalDate today = LocalDate.now();
        LocalDate start;
        try {
            start = (startDate == null || startDate.isBlank()) ? today : LocalDate.parse(startDate);
        } catch (DateTimeParseException e) {
            return "Invalid start date: " + startDate + ". Use the yyyy-MM-dd format";
        }
        if (start.isBefore(today)) {
            return "Start date " + startDate + " is in the past. Today is " + today;
        }
        LocalDate lastForecastDay = today.plusDays(MAX_FORECAST_DAYS - 1);
        if (start.isAfter(lastForecastDay)) {
            // Too far ahead for a forecast, so answer with the typical weather for that month instead
            return Map.of(
                    "note", "No day-by-day forecast this far ahead (forecasts end " + lastForecastDay
                            + "). This is the typical weather for the month",
                    "typicalWeather", getClimateForMonth(city, String.valueOf(start.getMonthValue())));
        }

        int count = Math.max(1, Math.min(days, MAX_FORECAST_DAYS));
        List<DailyWeather> forecast = new ArrayList<>();
        for (LocalDate date = start; forecast.size() < count && !date.isAfter(lastForecastDay); date = date.plusDays(1)) {
            forecast.add(dailyWeather(key, date));
        }
        return forecast;
    }

    @Tool(description = "Get the typical weather in a city for a month. Use this for trips more than 14 days away")
    public Object getClimateForMonth(
            String city,
            @ToolParam(description = "Month name or number, e.g. April or 4") String month) {
        String key = findCity(city);
        if (key == null) {
            return unknownCity(city);
        }
        Month parsed = parseMonth(month);
        if (parsed == null) {
            return "Invalid month: " + month;
        }
        Climate climate = climates.get(key);
        int index = parsed.getValue() - 1;
        int high = climate.avgHighC()[index];
        int low = climate.avgLowC()[index];
        return new MonthlyClimate(titleCase(key), parsed.getDisplayName(TextStyle.FULL, Locale.ENGLISH), high, low,
                toFahrenheit(high), toFahrenheit(low), describeMonth(climate, parsed.getValue()));
    }

    @Tool(description = "Get the best time of year to visit a city, based on its weather")
    public String getBestTimeToVisit(String city) {
        String key = findCity(city);
        if (key == null) {
            return unknownCity(city);
        }
        return titleCase(key) + ": " + climates.get(key).bestTimeToVisit();
    }

    // Seeded by city and date, so asking twice about the same day gives the same answer
    private DailyWeather dailyWeather(String key, LocalDate date) {
        Climate climate = climates.get(key);
        int month = date.getMonthValue();
        Random random = new Random(Objects.hash(key, date));

        int high = climate.avgHighC()[month - 1] + random.nextInt(7) - 3;
        int low = Math.min(high - 3, climate.avgLowC()[month - 1] + random.nextInt(5) - 2);
        int baseRain = climate.wetMonths().contains(month) ? 55 : key.equals("dubai") ? 3 : 18;
        int rain = Math.max(0, Math.min(95, baseRain + random.nextInt(31) - 15));

        String condition;
        if (rain >= 60) {
            condition = high <= 2 ? "Snow" : "Rain";
        } else if (rain >= 40) {
            condition = high <= 2 ? "Light snow" : "Showers";
        } else {
            condition = List.of("Sunny", "Sunny", "Partly cloudy", "Cloudy").get(random.nextInt(4));
        }
        return new DailyWeather(date.toString(), condition, high, low, toFahrenheit(high), toFahrenheit(low), rain);
    }

    private String describeMonth(Climate climate, int month) {
        int high = climate.avgHighC()[month - 1];
        String temperature = high >= 35 ? "Very hot" : high >= 27 ? "Hot" : high >= 20 ? "Warm"
                : high >= 12 ? "Mild" : high >= 5 ? "Cold" : "Very cold";
        return climate.wetMonths().contains(month) ? temperature + " and often rainy" : temperature + " and mostly dry";
    }

    private String findCity(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String value = input.trim().toLowerCase();
        String alias = CITY_ALIASES.getOrDefault(value, value);
        return climates.keySet().stream().filter(alias::contains).findFirst().orElse(null);
    }

    private String unknownCity(String city) {
        return "No weather data for " + city + ". Available cities: Dallas, New York, Los Angeles, London, Paris, "
                + "Tokyo, Dubai, New Delhi";
    }

    private Month parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        String value = month.trim();
        try {
            return Month.of(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            for (Month m : Month.values()) {
                if (m.name().startsWith(value.toUpperCase()) && value.length() >= 3) {
                    return m;
                }
            }
            return null;
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }

    private int toFahrenheit(int celsius) {
        return Math.round(celsius * 9 / 5f + 32);
    }

    private String titleCase(String key) {
        StringBuilder result = new StringBuilder();
        for (String word : key.split(" ")) {
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return result.toString().trim();
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
