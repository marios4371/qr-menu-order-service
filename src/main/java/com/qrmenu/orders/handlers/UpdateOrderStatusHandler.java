package com.qrmenu.orders.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
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
            Map<String, String> body = gson.fromJson(request.getBody(), Map.class);
            String shopId = body.get("shopId");
            String orderId = body.get("orderId");
            String newStatus = body.get("status");

            // 2. VALIDATE: Ensure all required fields for the update are present
            if (shopId == null || orderId == null || newStatus == null) {
                return createResponse(400, "{\"error\": \"Missing required fields: shopId, orderId, or status\"}");
            }

            // 3. DEFINE KEYS: Which exact item are we updating?
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("shopId", AttributeValue.builder().s(shopId).build());
            key.put("orderId", AttributeValue.builder().s(orderId).build());

            // 4. PREPARE UPDATE EXPRESSION
            // 'status' is a reserved keyword in DynamoDB sometimes, so we use #s as an alias
            Map<String, String> expressionAttributeNames = new HashMap<>();
            expressionAttributeNames.put("#s", "status");

            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":newStatusValue", AttributeValue.builder().s(newStatus).build());

            // 5. EXECUTE UPDATE: Set the new status
            UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(key)
                    .updateExpression("SET #s = :newStatusValue")
                    .expressionAttributeNames(expressionAttributeNames)
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();

            dynamoDb.updateItem(updateRequest);

            // 6. RETURN SUCCESS
            return createResponse(200, "{\"message\": \"Order status updated successfully\", \"newStatus\": \"" + newStatus + "\"}");

        } catch (Exception e) {
            context.getLogger().log("ERROR updating order: " + e.getMessage());
            return createResponse(500, "{\"error\": \"Internal Server Error\"}");
        }
    }

    // HELPER METHOD
    private APIGatewayProxyResponseEvent createResponse(int statusCode, String body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(body);
    }
}