package com.qrmenu.orders.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetMenuHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // STATIC INITIALIZATION
    private static final Gson gson = new Gson();
    private static final DynamoDbClient dynamoDb = DynamoDbClient.builder().build();
    private static final String TABLE_NAME = System.getenv("MENUS_TABLE");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // Get shopId from URL (?shopId=nissos)
            Map<String, String> queryParams = request.getQueryStringParameters();
            if (queryParams == null || !queryParams.containsKey("shopId")) {
                return createResponse(400, "{\"error\": \"Missing shopId\"}");
            }
            String shopId = queryParams.get("shopId");

            // Search into DynamoDB
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("shop_id", AttributeValue.builder().s(shopId).build());

            GetItemRequest getItemRequest = GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(key)
                    .build();

            GetItemResponse response = dynamoDb.getItem(getItemRequest);

            // Check if shop exists, if !exists then 404
            if (!response.hasItem()) {
                return createResponse(404, "{\"error\": \"Menu not found for shop: " + shopId + "\"}");
            }

            // Convert DynamoDB Item into Java Map
            Map<String, AttributeValue> dynamoItem = response.item();
            Map<String, Object> finalResponse = new HashMap<>();

            // keep shop_id
            finalResponse.put("shopId", dynamoItem.get("shop_id").s());

            // convert settings
            if (dynamoItem.containsKey("settings")) {
                finalResponse.put("settings", toPlainObject(dynamoItem.get("settings")));
            }

            // convert menu
            if (dynamoItem.containsKey("menu")) {
                finalResponse.put("menu", toPlainObject(dynamoItem.get("menu")));
            }

            // convert features
            if (dynamoItem.containsKey("features")) {
                finalResponse.put("features", toPlainObject(dynamoItem.get("features")));
            }

            // convert theme
            if (dynamoItem.containsKey("theme")) {
                finalResponse.put("theme", toPlainObject(dynamoItem.get("theme")));
            }

            // return as JSON
            return createResponse(200, gson.toJson(finalResponse));

        } catch (Exception e) {
            context.getLogger().log("ERROR: " + e.getMessage());
            return createResponse(500, "{\"error\": \"Internal Server Error\"}");
        }
    }

    // --- HELPER METHODS ---

    private APIGatewayProxyResponseEvent createResponse(int statusCode, String body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(body);
    }

    // retrospective function that converts DynamoDB AttributeValues into Java Objects for Gson
    private Object toPlainObject(AttributeValue attr) {
        if (attr.hasM()) {
            Map<String, Object> map = new HashMap<>();
            attr.m().forEach((k, v) -> map.put(k, toPlainObject(v)));
            return map;
        } else if (attr.hasL()) {
            List<Object> list = new ArrayList<>();
            attr.l().forEach(v -> list.add(toPlainObject(v)));
            return list;
        } else if (attr.s() != null) {
            return attr.s();
        } else if (attr.n() != null) {
            try { return Integer.parseInt(attr.n()); }
            catch (NumberFormatException e) { return Double.parseDouble(attr.n()); }
        } else if (attr.bool() != null) {
            return attr.bool();
        }
        return null;
    }
}