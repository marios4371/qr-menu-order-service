package com.qrmenu.orders.models;

import java.util.List;

public class Order {

    // Matches the existing shop_id from the QR Menu table to link data
    private String shopId;
    // Unique identifier for the transaction (for example ORDER#20260227#12345)
    private String orderId;
    private String tableNumber;
    private List<OrderItem> orderItem;
    private Double totalAmount;

    // STATE MANAGEMENT
    // For example, PENDING_PAYMENT, NEW, PREPARING, COMPLETED
    private String status;
    // For example, CUSTOMER_QR, WAITER_PDA
    private String source;

    // ISO 8601 string or Epoch timestamp
    private String createdAt;

    private String claimedBy;

    private String paymentStatus;

    // DEFAULT CONSTRUCTOR (Required for JSON serialization/deserialization)
    public Order() {
    }

    // GETTERS AND SETTERS

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(String tableNumber) {
        this.tableNumber = tableNumber;
    }

    public void setOrderItem(List<OrderItem> orderItem) {
        this.orderItem = orderItem;
    }

    public List<OrderItem> getOrderItem() {
        return orderItem;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public void setClaimedBy(String claimedBy) {
        this.claimedBy = claimedBy;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
}