# qrmenu-order-service — Java Lambda Microservice

Serverless Java microservice για το σύστημα παραγγελιών του QRMenu. Τρέχει σε AWS Lambda, εκτίθεται μέσω API Gateway, αποθηκεύει στο DynamoDB.

## Tech Stack

- **Java 21**
- **AWS Lambda** (API Gateway trigger)
- **AWS DynamoDB** (SDK v2)
- **Gson** για JSON serialization
- **Maven** + `maven-shade-plugin` για fat JAR

## Αρχιτεκτονική

```
API Gateway
    ├── GET  /menu?shopId=       → GetMenuHandler
    ├── GET  /orders?shopId=     → GetOrdersHandler
    ├── POST /orders             → CreateOrderHandler
    └── PATCH /orders/status     → UpdateOrderStatusHandler
```

## Handlers

### `GetMenuHandler`
Φέρνει το πλήρες μενού ενός καταστήματος από το `MENUS_TABLE`.

- **Input**: `?shopId=nissos` (query param)
- **Output**: `{ shopId, settings, menu, features, theme }`
- Χρησιμοποιεί recursive `toPlainObject()` για μετατροπή `AttributeValue` → Java objects → Gson JSON
- Επιστρέφει 404 αν δεν βρεθεί το κατάστημα

### `CreateOrderHandler`
Δημιουργεί νέα παραγγελία.

- **Input**: `Order` object (shopId, tableNumber, orderItem[], totalAmount)
- **Output**: `{ message, orderId }`
- Παράγει composite Sort Key: `ORDER#{ISO_timestamp}#{UUID}`
- Enriches κάθε `OrderItem` με `itemStatus: "PENDING"` και `timestamp`
- Fail-safe για null `station` → default `"KITCHEN"`
- Fail-safe για null `productId` → χρησιμοποιεί το `name`
- Αποθηκεύει `orderItem[]` ως JSON string στο DynamoDB (πεδίο `items`)

### `GetOrdersHandler`
Επιστρέφει όλες τις παραγγελίες ενός καταστήματος.

- **Input**: `?shopId=nissos`
- **Output**: Array παραγγελιών
- Query με `begins_with(orderId, "ORDER#")` για φιλτράρισμα
- Parse του stored JSON string `items` πίσω σε array

### `UpdateOrderStatusHandler`
Ενημερώνει κατάσταση παραγγελίας. Υποστηρίζει 4 actions:

| Action | Περιγραφή | Status transition |
|--------|-----------|-------------------|
| `CLAIM` | Σερβιτόρος αναλαμβάνει το τραπέζι | → `CLAIMED` |
| `CLOSE` | Πληρωμή & κλείσιμο | → `CLOSED`, paymentStatus: `PAID` |
| `APPEND` | Προσθήκη νέων items σε υπάρχουσα παραγγελία | → `CLAIMED` |
| `ITEM_DONE` | Ένα item ετοιμάστηκε (Kitchen/Bar) | → `PARTIAL` ή `READY` |

**Business logic για `ITEM_DONE`**: Αν **όλα** τα items έχουν `itemStatus: "DONE"` → status `READY`, αλλιώς `PARTIAL`.

**APPEND matching**: Γίνεται με `productId` + `timestamp` για ακριβή ταύτιση (αντιμετωπίζει το case 2× ίδιο προϊόν στην ίδια παραγγελία).

## Data Models

### Order
```java
String shopId          // PK στο DynamoDB
String orderId         // SK: "ORDER#{timestamp}#{uuid}"
String tableNumber
List<OrderItem> orderItem
Double totalAmount
String status          // NEW | CLAIMED | PARTIAL | READY | CLOSED
String source          // CUSTOMER_QR | WAITER_APP
String createdAt       // ISO 8601
String claimedBy
String paymentStatus   // UNPAID | PAID
```

### OrderItem
```java
String productId
String name
Integer quantity
Double unitPrice
String station         // BAR | KITCHEN
String itemStatus      // PENDING | DONE
String timestamp       // για unique identification
List<String> modifiers
```

## DynamoDB Schema

### `MENUS_TABLE`
| Attribute | Type | Role |
|-----------|------|------|
| `shop_id` | String | Partition Key |
| `menu` | Map/List | Κατηγορίες & προϊόντα |
| `settings` | Map | Ρυθμίσεις καταστήματος |
| `features` | Map | Ενεργά features ανά πλάνο |
| `theme` | Map | Theme configuration |

### `ORDERS_TABLE`
| Attribute | Type | Role |
|-----------|------|------|
| `shopId` | String | Partition Key |
| `orderId` | String | Sort Key (`ORDER#...`) |
| `status` | String | |
| `items` | String | JSON-serialized OrderItem[] |
| `claimedBy` | String | |
| `tableNumber` | String | |
| `totalAmount` | Number | |
| `createdAt` | String | ISO timestamp |

## Build

```bash
mvn clean package
# Παράγει: target/qrmenu-order-service-1.0-SNAPSHOT.jar
```

Το `maven-shade-plugin` παράγει fat JAR που περιλαμβάνει όλες τις dependencies (AWS SDK, Gson, κ.α.) για upload στο Lambda.

## Lambda Configuration

Κάθε handler ορίζεται ξεχωριστά στο Lambda console:

```
Handler: com.qrmenu.orders.handlers.CreateOrderHandler::handleRequest
Runtime: Java 21
Timeout: 30s (recommended)
Memory: 512 MB (recommended)
```

**Environment Variables:**
- `ORDERS_TABLE` — Όνομα του DynamoDB table για παραγγελίες
- `MENUS_TABLE` — Όνομα του DynamoDB table για μενού

## CORS

Όλοι οι handlers επιστρέφουν:
```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: OPTIONS,POST,GET,PATCH,PUT
```
