package com.qrmenu.orders.models;

import java.util.List;

public class OrderUpdateRequest {
    private String shopId;
    private String orderId;
    private String action; // CLAIM, ITEM_DONE, APPEND, CLOSE

    // Extra fields depend on action
    private String employeeName;      // who CLAIMS
    private String productId;         // which product changed into ITEM_DONE
    private String itemTimestamp;     // exact order (if 2 same beers for example)
    private List<OrderItem> newItems; // list with new products for APPEND

    // Getters and Setters
    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getItemTimestamp() { return itemTimestamp; }
    public void setItemTimestamp(String itemTimestamp) { this.itemTimestamp = itemTimestamp; }

    public List<OrderItem> getNewItems() { return newItems; }
    public void setNewItems(List<OrderItem> newItems) { this.newItems = newItems; }
}