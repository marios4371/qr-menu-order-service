package com.qrmenu.orders.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.qrmenu.orders.models.OrderItem;
import com.qrmenu.orders.models.OrderUpdateRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdateOrderStatusHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // STATIC INITIALIZATION
    private static final Gson gson = new Gson();
    private static final DynamoDbClient dynamoDb = DynamoDbClient.builder().build();
    private static final String TABLE_NAME = System.getenv("ORDERS_TABLE");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // 1. EXTRACT DATA: Parse the incoming JSON body
            OrderUpdateRequest updateReq = gson.fromJson(request.getBody(), OrderUpdateRequest.class);
            String shopId = updateReq.getShopId();
            String orderId = updateReq.getOrderId();
            String action = updateReq.getAction();

            // 2. VALIDATE: Ensure all required fields for the update are present
            if (shopId == null || orderId == null || action == null) {
                return createResponse(400, "{\"error\": \"Missing required fields: shopId, orderId, or status\"}");
            }

            // 3. DEFINE KEYS: Which exact item are we updating?
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("shopId", AttributeValue.builder().s(shopId).build());
            key.put("orderId", AttributeValue.builder().s(orderId).build());

            // fetch current state, required to evaluate array elements for ITEM_DONE and APPEND
            GetItemRequest getItemRequest = GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(key)
                    .build();
            GetItemResponse getResponse = dynamoDb.getItem(getItemRequest);

            if (!getResponse.hasItem()) {
                return createResponse(404, "{\"error\": \"Order not found\"}");
            }

            Map<String, AttributeValue> item = new HashMap<>(getResponse.item());

            // 5. EXTRACT CURRENT ITEMS AND STATUS
            String currentStatus = item.containsKey("status") ? item.get("status").s() : "NEW";
            String currentItemsJson = item.containsKey("items") ? item.get("items").s() : "[]";

            Type listType = new TypeToken<List<OrderItem>>(){}.getType();
            List<OrderItem> currentItems = gson.fromJson(currentItemsJson, listType);

            // 6. EXECUTE BUSINESS LOGIC BASED ON ACTION
            switch (action.toUpperCase()) {
                case "CLAIM":
                    item.put("status", AttributeValue.builder().s("CLAIMED").build());
                    if (updateReq.getEmployeeName() != null) {
                        item.put("claimedBy", AttributeValue.builder().s(updateReq.getEmployeeName()).build());
                    }
                    currentStatus = "CLAIMED";
                    break;

                case "CLOSE":
                    item.put("status", AttributeValue.builder().s("CLOSED").build());
                    item.put("paymentStatus", AttributeValue.builder().s("PAID").build());
                    currentStatus = "CLOSED";
                    break;

                case "APPEND":
                    if (updateReq.getNewItems() != null && !updateReq.getNewItems().isEmpty()) {
                        currentItems.addAll(updateReq.getNewItems());
                        item.put("status", AttributeValue.builder().s("CLAIMED").build());
                        currentStatus = "CLAIMED";
                    }
                    break;

                case "ITEM_DONE":
                    boolean allDone = true;
                    for (OrderItem orderItem : currentItems) {
                        if (orderItem.getProductId() != null && orderItem.getProductId().equals(updateReq.getProductId()) &&
                                orderItem.getTimestamp() != null && orderItem.getTimestamp().equals(updateReq.getItemTimestamp())) {
                            orderItem.setItemStatus("DONE");
                        }
                        if (!"DONE".equals(orderItem.getItemStatus())) {
                            allDone = false;
                        }
                    }
                    currentStatus = allDone ? "READY" : "PARTIAL";
                    item.put("status", AttributeValue.builder().s(currentStatus).build());
                    break;

                default:
                    return createResponse(400, "{\"error\": \"Unknown action\"}");
            }

            // Update items array in the map
            item.put("items", AttributeValue.builder().s(gson.toJson(currentItems)).build());

            // 7. PERSIST: Save the updated item back to DynamoDB
            PutItemRequest putRequest = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(item)
                    .build();

            dynamoDb.putItem(putRequest);

            // 8. RETURN SUCCESS
            return createResponse(200, "{\"message\": \"Order updated successfully\", \"newStatus\": \"" + currentStatus + "\"}");

        } catch (Exception e) {
            context.getLogger().log("ERROR updating order: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, "{\"error\": \"Internal Server Error\"}");
        }
    }

    // HELPER METHOD
    private APIGatewayProxyResponseEvent createResponse(int statusCode, String body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "OPTIONS,POST,GET,PATCH,PUT");
        headers.put("Access-Control-Allow-Headers", "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token");

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(body);
    }
}