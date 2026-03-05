package com.qrmenu.orders.handlers;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.qrmenu.orders.models.Order;
import com.qrmenu.orders.models.OrderItem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreateOrderHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // STATIC INITIALIZATION: Optimize for cold starts
    private static final Gson gson = new Gson();
    private static final DynamoDbClient dynamoDb = DynamoDbClient.builder().build();
    private static final String TABLE_NAME = System.getenv("ORDERS_TABLE");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        context.getLogger().log("Incoming Request: " + request.getBody());

        try {
            // DESERIALIZE: Convert the JSON body from the client into our Order object
            Order newOrder = gson.fromJson(request.getBody(), Order.class);

            if (newOrder.getOrderItem() != null && !newOrder.getOrderItem().isEmpty()) {
                OrderItem firstItem = newOrder.getOrderItem().get(0);
                if (firstItem.getStation() == null || firstItem.getStation().isEmpty()) {
                    String rawBody = request.getBody().replace("\"", "'"); // Αλλαγή σε μονά αυτάκια για να μην σπάσει το JSON
                    return createResponse(400, "{\"error\": \"DEBUG_STATION_NULL | Η Java έλαβε αυτό: " + rawBody + "\"}");
                }
            }

            // when new order is initialized takes some standard states
            newOrder.setStatus("NEW");
            newOrder.setPaymentStatus("UNPAID");
            newOrder.setClaimedBy("NONE");
            if (newOrder.getSource() == null) {
                newOrder.setSource("CUSTOMER_QR");
            }

            String currentTimestamp = Instant.now().toString();
            if (newOrder.getOrderItem() != null) {
                for (OrderItem item : newOrder.getOrderItem()) {
                    item.setItemStatus("PENDING");
                    item.setTimestamp(currentTimestamp);

                    // LOGGING: Print exactly what Java received from the frontend
                    context.getLogger().log("Received Item: " + item.getName() + " | Station: " + item.getStation() + " | ProductId: " + item.getProductId());

                    // FAIL-SAFE: If station is null, force it, so it doesn't get dropped by Gson
                    if (item.getStation() == null || item.getStation().trim().isEmpty()) {
                        context.getLogger().log("WARNING: Station was null for item " + item.getName() + ". Defaulting to KITCHEN.");
                        item.setStation("KITCHEN");
                    }

                    // FAIL-SAFE: If productId is null, use the name as ID
                    if (item.getProductId() == null || item.getProductId().trim().isEmpty()) {
                        item.setProductId(item.getName());
                    }
                }
            }

            // VALIDATE: Ensure critical fields exist
            if (newOrder.getShopId() == null || newOrder.getShopId().isEmpty()) {
                return createResponse(400, "{\"error\": \"Missing shopId\"}");
            }

            // ENRICH: Assign UUID, Timestamp, and default state if missing
            String uniqueId = UUID.randomUUID().toString();
            String timestamp = Instant.now().toString();
            // Create the composite Sort Key (e.g., ORDER#2026-02-27T10:15:30Z#123e4567...)
            String generatedOrderId = "ORDER#" + timestamp + "#" + uniqueId;

            newOrder.setOrderId(generatedOrderId);
            newOrder.setCreatedAt(timestamp);
            if (newOrder.getStatus() == null) {
                newOrder.setStatus("NEW");
            }

            // MAP TO DYNAMODB ITEM
            Map<String, AttributeValue> item = new HashMap<>();
            // Primary Keys
            item.put("shopId", AttributeValue.builder().s(newOrder.getShopId()).build());
            item.put("orderId", AttributeValue.builder().s(newOrder.getOrderId()).build());

            // Other Attributes
            item.put("status", AttributeValue.builder().s(newOrder.getStatus()).build());
            item.put("createdAt", AttributeValue.builder().s(newOrder.getCreatedAt()).build());

            if (newOrder.getPaymentStatus() != null) {
                item.put("paymentStatus", AttributeValue.builder().s(newOrder.getPaymentStatus()).build());
            }
            if (newOrder.getClaimedBy() != null) {
                item.put("claimedBy", AttributeValue.builder().s(newOrder.getClaimedBy()).build());
            }

            if (newOrder.getTableNumber() != null) {
                item.put("tableNumber", AttributeValue.builder().s(newOrder.getTableNumber()).build());
            }
            if (newOrder.getTotalAmount() != null) {
                item.put("totalAmount", AttributeValue.builder().n(String.valueOf(newOrder.getTotalAmount())).build());
            }

            // Convert the items list back to JSON string to store in DB
            // (DynamoDB handles simple strings faster than nested complex maps)
            if (newOrder.getOrderItem() != null && !newOrder.getOrderItem().isEmpty()) {
                String itemsJson = gson.toJson(newOrder.getOrderItem());
                item.put("items", AttributeValue.builder().s(itemsJson).build());
            }

            // PERSIST: Save the item to the database
            PutItemRequest putRequest = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(item)
                    .build();
            dynamoDb.putItem(putRequest);

            // RESPOND: Return the generated IDs to the client
            String responseBody = String.format("{\"message\": \"Order V2\", \"orderId\": \"%s\"}", generatedOrderId);
            return createResponse(201, responseBody);

        } catch (Exception e) {
            context.getLogger().log("ERROR: " + e.getMessage());
            return createResponse(500, "{\"error\": \"Internal Server Error\"}");
        }
    }

    // HELPER METHOD Standardize API responses
    private APIGatewayProxyResponseEvent createResponse(int statusCode, String body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "OPTIONS,POST,GET");
        headers.put("Access-Control-Allow-Headers", "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token");

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(body);
    }
}