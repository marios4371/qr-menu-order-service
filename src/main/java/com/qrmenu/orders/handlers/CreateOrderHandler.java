package com.qrmenu.orders.handlers;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.qrmenu.orders.models.Order;
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
            String responseBody = String.format("{\"message\": \"Order created\", \"orderId\": \"%s\"}", generatedOrderId);
            return createResponse(201, responseBody);

        } catch (Exception e) {
            context.getLogger().log("ERROR: " + e.getMessage());
            return createResponse(500, "{\"error\": \"Internal Server Error\"}");
        }
    }

    // HELPER METHOD: Standardize API responses
    private APIGatewayProxyResponseEvent createResponse(int statusCode, String body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        // CORS headers - crucial for web/mobile apps
        headers.put("Access-Control-Allow-Origin", "*");

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(body);
    }
}