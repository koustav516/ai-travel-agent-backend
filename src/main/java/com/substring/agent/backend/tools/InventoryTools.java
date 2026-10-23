package com.substring.agent.backend.tools;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class InventoryTools {

    private static final int LOW_STOCK_THRESHOLD = 10;

    private final Map<String, Integer> inventory = new ConcurrentHashMap<>(Map.of(
            "laptop", 25,
            "smartphone", 40,
            "headphones", 8,
            "keyboard", 60,
            "mouse", 75,
            "monitor", 5,
            "webcam", 0,
            "usb-c cable", 150,
            "power bank", 12,
            "smartwatch", 3));

    @Tool(description = "Get the current stock count of an inventory item by its name")
    public String getStockCount(String itemName) {
        Integer stock = inventory.get(normalize(itemName));
        if (stock == null) {
            return "Item not found: " + itemName;
        }
        return itemName + " has " + stock + " units in stock";
    }

    @Tool(description = "Check whether the requested quantity of an item is available in stock")
    public String checkAvailability(
            String itemName,
            @ToolParam(description = "Number of units the customer wants") int quantity) {
        Integer stock = inventory.get(normalize(itemName));
        if (stock == null) {
            return "Item not found: " + itemName;
        }
        if (stock >= quantity) {
            return "Yes, " + quantity + " units of " + itemName + " are available (" + stock + " in stock)";
        }
        return "Not enough stock. Requested " + quantity + " units of " + itemName + ", but only " + stock
                + " available";
    }

    @Tool(description = "List all inventory items with their stock counts")
    public Map<String, Integer> listAllItems() {
        return Map.copyOf(inventory);
    }

    @Tool(description = "List items that are low on stock or out of stock")
    public Map<String, Integer> getLowStockItems() {
        return inventory.entrySet().stream()
                .filter(entry -> entry.getValue() <= LOW_STOCK_THRESHOLD)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Tool(description = "Add units to an existing item's stock (restock)")
    public synchronized String restockItem(
            String itemName,
            @ToolParam(description = "Number of units to add, must be positive") int quantity) {
        if (quantity <= 0) {
            return "Quantity must be greater than zero";
        }
        Integer updated = inventory.computeIfPresent(normalize(itemName), (key, stock) -> stock + quantity);
        if (updated == null) {
            return "Item not found: " + itemName;
        }
        return "Restocked " + itemName + ". New stock count: " + updated;
    }

    @Tool(description = "Reserve units of an item for a customer, which reduces its stock count")
    public synchronized String reserveItem(
            String itemName,
            @ToolParam(description = "Number of units to reserve, must be positive") int quantity) {
        if (quantity <= 0) {
            return "Quantity must be greater than zero";
        }
        String key = normalize(itemName);
        Integer stock = inventory.get(key);
        if (stock == null) {
            return "Item not found: " + itemName;
        }
        if (stock < quantity) {
            return "Not enough stock to reserve " + quantity + " units of " + itemName + ". Only " + stock
                    + " available";
        }
        inventory.put(key, stock - quantity);
        return "Reserved " + quantity + " units of " + itemName + ". Remaining stock: " + (stock - quantity);
    }

    private String normalize(String itemName) {
        return itemName == null ? "" : itemName.trim().toLowerCase();
    }
}
