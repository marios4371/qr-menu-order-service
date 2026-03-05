package com.qrmenu.orders.models;


import java.util.List;

public class OrderItem {

    // Unique ID referencing the product in your existing QR Menu JSON
    private String productId;
    private String name;
    private Integer quantity;
    private Double unitPrice;
    // Can be a list of Strings or a list of specific Modifier objects later
    private List<String> modifiers;

    // station for bar or kitchen
    private String station;
    // claim, pending etc.
    private String itemStatus;
    // timestamp string in order to see when each state of order happened
    private String timestamp;

    public OrderItem() {
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Double getUnitPrice() {
        return unitPrice;
    }

    public String getStation() {
        return station;
    }

    public void setStation(String station) {
        this.station = station;
    }

    public String getItemStatus() {
        return itemStatus;
    }

    public void setItemStatus(String itemStatus) {
        this.itemStatus = itemStatus;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public void setUnitPrice(Double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public List<String> getModifiers() {
        return modifiers;
    }

    public void setModifiers(List<String> modifiers) {
        this.modifiers = modifiers;
    }
}