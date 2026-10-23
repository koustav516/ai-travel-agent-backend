package com.substring.agent.backend.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.substring.agent.backend.tools.FlightTools;
import com.substring.agent.backend.tools.HotelTools;
import com.substring.agent.backend.tools.WeatherTools;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class AiConfig {

    private final FlightTools flightTools;
    private final HotelTools hotelTools;
    private final WeatherTools weatherTools;

    // {today} is filled in on every request by ChatService, so relative dates like "next Friday" work
    private static final String SYSTEM_PROMPT = """
            You are a friendly travel agent who helps people plan trips.
            Today is {today}.

            You can search flights, search and book hotels, and check the weather or the best time to visit.
            - Always use your tools for flights, hotels, prices and weather. Never make up flight numbers, hotels, prices or forecasts.
            - Pass dates to tools in yyyy-MM-dd format, working out relative dates such as "next Friday" from today's date.
            - Before booking anything, confirm the details with the traveller and ask for their full name if you don't have it.
            - After every booking, always tell the traveller its booking reference (e.g. FL-1001 or HT-5001).
            - When planning a whole trip, always search for both the outbound and the return flight, suggest a hotel that fits their budget, and mention the expected weather.
            - Keep answers short and well organised, and show prices in USD.
            """;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(flightTools, hotelTools, weatherTools)
                .build();
    }

}
