package com.substring.agent.backend.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.substring.agent.backend.tools.InventoryTools;
import com.substring.agent.backend.tools.OrderTools;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class AiConfig {

    private final OrderTools tools;
    private final InventoryTools inventoryTools;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(
                        "You are a helpful assistant who helps customers get the status of their order, cancel their orders, and check or manage inventory stock")
                .defaultTools(tools, inventoryTools)
                .build();
    }

}
