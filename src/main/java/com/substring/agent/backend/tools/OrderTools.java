package com.substring.agent.backend.tools;

import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class OrderTools {

    // Fake data
    private final Map<String, String> orders = Map.of(
            "1041", "Order shipped",
            "1042", "Order received",
            "1043", "Order Delivered");

    @Tool(description = "Get the status of the order with its order id")
    public String getOrderStatus(String orderId) {
        return orders.getOrDefault(orderId, "Order not found");
    }

    @Tool(description = "Cancel a customer order by its id")
    public String cancelOrder(String orderId) {
        if (orders.containsKey(orderId)) {
            return "Order with ID: " + orderId + " has been cancelled";
        } else {
            return "Order not found";
        }
    }
}
