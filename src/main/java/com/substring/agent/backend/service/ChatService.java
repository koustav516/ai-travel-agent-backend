package com.substring.agent.backend.service;

import java.time.LocalDate;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;

    public String chat(String query) {
        return chatClient.prompt()
                // Weekday included so the model can work out dates like "next Friday"
                .system(s -> s.param("today", LocalDate.now().getDayOfWeek() + ", " + LocalDate.now()))
                .user(query)
                .call()
                .content();
    }

}
