package com.qrmenu.orders.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetOrdersHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // STATIC INITIALIZATION
    private static final Gson gson = new Gson();
    private static final DynamoDbClient dynamoDb = DynamoDbClient.builder().build();
    private static final String TABLE_NAME = System.getenv("ORDERS_TABLE");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // 1. EXTRACT PARAMETERS: Get the shopId from the URL (?shopId=SHOP_001)
            Map<String, String> queryParams = request.getQueryStringParameters();
            if (queryParams == null || !queryParams.containsKey("shopId")) {
                return createResponse(400, "{\"error\": \"Missing shopId query parameter\"}");
            }
            String shopId = queryParams.get("shopId");

            // 2. BUILD QUERY: Fetch orders efficiently using the Partition Key (shopId)
            Map<String, String> expressionAttributesNames = new HashMap<>();
            expressionAttributesNames.put("#pk", "shopId");
            expressionAttributesNames.put("#sk", "orderId");

            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":shopIdValue", AttributeValue.builder().s(shopId).build());
            // We only want to fetch items that are actually orders (in case we add other entities later)
            expressionAttributeValues.put(":orderPrefix", AttributeValue.builder().s("ORDER#").build());

            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .keyConditionExpression("#pk = :shopIdValue AND begins_with(#sk, :orderPrefix)")
                    .expressionAttributeNames(expressionAttributesNames)
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();

            // 3. EXECUTE QUERY
            QueryResponse queryResponse = dynamoDb.query(queryRequest);

            // 4. FORMAT RESPONSE: Map DynamoDB objects back to standard JSON
            List<Map<String, Object>> ordersList = new ArrayList<>();
            for (Map<String, AttributeValue> item : queryResponse.items()) {
                Map<String, Object> orderMap = new HashMap<>();
                orderMap.put("shopId", item.get("shopId").s());
                orderMap.put("orderId", item.get("orderId").s());

                if (item.containsKey("status")) orderMap.put("status", item.get("status").s());
                if (item.containsKey("totalAmount")) orderMap.put("totalAmount", item.get("totalAmount").n());
                if (item.containsKey("createdAt")) orderMap.put("createdAt", item.get("createdAt").s());
                if (item.containsKey("tableNumber")) orderMap.put("tableNumber", item.get("tableNumber").s());

                // Parse the stored JSON string back to an array for the response
                if (item.containsKey("items")) {
                    Object itemsObj = gson.fromJson(item.get("items").s(), Object.class);
                    orderMap.put("orderItem", itemsObj);
                }

                ordersList.add(orderMap);
            }

            // 5. RETURN SUCCESS
            return createResponse(200, gson.toJson(ordersList));

        } catch (Exception e) {
            context.getLogger().log("ERROR: " + e.getMessage());
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