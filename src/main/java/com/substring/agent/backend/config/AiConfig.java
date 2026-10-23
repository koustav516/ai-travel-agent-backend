package com.substring.agent.backend.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Configuration;

import com.substring.agent.backend.tools.OrderTools;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class AiConfig {

    private final OrderTools tools;

    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(
                        "You are a helpful assistant who hels customers to get the status of their order and cancel their orders")
                .defaultTools(tools)
                .build();
    }

}
