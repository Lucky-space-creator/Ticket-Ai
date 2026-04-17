# 订单管理模块

## 概述
订单管理模块负责处理订单的完整生命周期，包括订单创建、支付、退票、查询和状态管理。该模块采用状态机模式管理订单状态，确保状态转换的合法性和数据一致性。

## 主要功能
1. **订单创建** - 创建购票订单（与车票预订模块集成）
2. **订单支付** - 模拟支付流程，更新订单状态
3. **订单退票** - 处理用户退票申请，恢复库存
4. **订单查询** - 查询用户订单列表和详情
5. **订单状态管理** - 状态机驱动订单状态转换
6. **订单缓存** - 缓存用户订单列表，提高查询性能

## 数据模型
### 订单表（order）
```sql
CREATE TABLE `order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(32) NOT NULL COMMENT '订单号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `train_id` bigint NOT NULL COMMENT '车次ID',
  `departure_date` date NOT NULL COMMENT '出发日期',
  `seat_type` varchar(20) NOT NULL COMMENT '席别类型',
  `total_amount` decimal(10,2) NOT NULL COMMENT '订单总金额',
  `status` varchar(20) NOT NULL COMMENT '订单状态：PENDING-待支付，PAID-已支付，CANCELLED-已取消，REFUNDED-已退票，COMPLETED-已完成',
  `contact_name` varchar(50) NOT NULL COMMENT '联系人姓名',
  `contact_phone` varchar(20) NOT NULL COMMENT '联系人电话',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  `refund_time` datetime DEFAULT NULL COMMENT '退票时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
```

### 订单明细表（order_item）
```sql
CREATE TABLE `order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `passenger_id` bigint NOT NULL COMMENT '乘客ID',
  `ticket_no` varchar(32) DEFAULT NULL COMMENT '票号（支付后生成）',
  `seat_no` varchar(10) DEFAULT NULL COMMENT '座位号',
  `price` decimal(10,2) NOT NULL COMMENT '票价',
  `status` varchar(20) NOT NULL COMMENT '票状态：UNPAID-待支付，PAID-已支付，REFUNDED-已退票',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_passenger_id` (`passenger_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';
```

## 订单状态机
### 状态定义
```java
public enum OrderStatus {
    PENDING("待支付"),      // 订单创建后，等待支付
    PAID("已支付"),        // 支付成功
    CANCELLED("已取消"),    // 用户取消订单（支付前）
    REFUNDED("已退票"),     // 支付后退票
    COMPLETED("已完成");    // 车次已出发，订单完成
    
    private final String description;
    
    OrderStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
```

### 状态转换规则
```
PENDING → PAID        : 用户支付订单
PENDING → CANCELLED   : 用户取消订单（支付前）
PAID → REFUNDED       : 用户退票
PAID → COMPLETED      : 车次出发后自动标记完成
```

### 状态转换约束
1. **PENDING → PAID**: 必须在订单创建后30分钟内完成支付，超时自动取消
2. **PAID → REFUNDED**: 发车前2小时之前可退票，扣除手续费
3. **自动完成**: 车次出发时间后，系统自动将PAID状态订单标记为COMPLETED

## 核心接口
### 1. 订单支付
**请求地址**: `POST /api/orders/{orderNo}/pay`

**处理流程**:
1. 验证订单是否存在且属于当前用户
2. 检查订单状态是否为PENDING
3. 检查是否超过支付截止时间（30分钟）
4. 模拟支付流程（实际项目应接入支付网关）
5. 更新订单状态为PAID，记录支付时间
6. 生成票号和座位号
7. 清理订单缓存
8. 返回支付成功结果

**响应示例**:
```json
{
  "success": true,
  "code": "200",
  "message": "支付成功",
  "data": {
    "orderNo": "20260420123456789",
    "payAmount": 1106.00,
    "payTime": "2026-04-20 14:30:25",
    "ticketNos": ["T2026042012345678901", "T2026042012345678902"]
  }
}
```

### 2. 订单退票
**请求地址**: `POST /api/orders/{orderNo}/refund`

**请求参数**:
```json
{
  "reason": "行程变更"
}
```

**处理流程**:
1. 验证订单是否存在且属于当前用户
2. 检查订单状态是否为PAID
3. 检查是否在可退票时间内（发车前2小时之前）
4. 计算退票金额（扣除手续费）
5. 开启事务处理：
   - 更新订单状态为REFUNDED，记录退票时间
   - 更新订单明细状态为REFUNDED
   - 恢复车票库存
   - 记录退票流水（预留）
6. 清理相关缓存
7. 返回退票成功结果

### 3. 订单查询
#### 3.1 查询订单列表
**请求地址**: `GET /api/orders`

**查询参数**:
- `status` - 订单状态（可选）
- `page` - 页码（默认1）
- `size` - 每页数量（默认10）
- `startDate` - 开始日期（可选）
- `endDate` - 结束日期（可选）

**缓存策略**:
```java
// 缓存键格式：user_orders:{userId}:{status}:{page}:{size}
String cacheKey = String.format("user_orders:%d:%s:%d:%d", 
    userId, status, page, size);

// 缓存有效期：2分钟
List<OrderVO> cachedOrders = cacheService.get(cacheKey);
if (cachedOrders != null) {
    return cachedOrders;
}

// 数据库查询并缓存
List<OrderVO> orders = orderMapper.selectByUser(userId, status, page, size);
cacheService.set(cacheKey, orders, 2, TimeUnit.MINUTES);
```

#### 3.2 查询订单详情
**请求地址**: `GET /api/orders/{orderNo}`

**处理流程**:
1. 验证订单是否属于当前用户
2. 查询订单基本信息
3. 查询订单明细（车票信息）
4. 查询关联的乘客信息
5. 查询车次信息
6. 组装完整订单详情返回

### 4. 订单取消（支付前）
**请求地址**: `POST /api/orders/{orderNo}/cancel`

**处理流程**:
1. 验证订单是否存在且属于当前用户
2. 检查订单状态是否为PENDING
3. 开启事务处理：
   - 更新订单状态为CANCELLED，记录取消时间
   - 恢复车票库存
4. 清理相关缓存
5. 返回取消成功结果

## 核心业务逻辑
### 1. 订单号生成规则
订单号采用时间戳+随机数+用户ID哈希的方式生成，确保唯一性：
```java
public String generateOrderNo() {
    // 格式：yyyyMMdd + 8位随机数 + 用户ID后3位
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
    String dateStr = sdf.format(new Date());
    String randomStr = String.format("%08d", new Random().nextInt(100000000));
    String userIdSuffix = String.format("%03d", userId % 1000);
    return dateStr + randomStr + userIdSuffix;
}
```

### 2. 支付超时处理
系统通过定时任务处理支付超时的订单：
```java
@Component
public class OrderTimeoutTask {
    
    @Scheduled(fixedDelay = 60000) // 每分钟执行一次
    public void cancelTimeoutOrders() {
        // 查询创建时间超过30分钟且状态为PENDING的订单
        List<Order> timeoutOrders = orderMapper.selectTimeoutOrders(30);
        
        for (Order order : timeoutOrders) {
            try {
                orderService.cancelOrder(order.getOrderNo(), "支付超时自动取消");
            } catch (Exception e) {
                log.error("取消超时订单失败：{}", order.getOrderNo(), e);
            }
        }
    }
}
```

### 3. 退票手续费计算
退票手续费根据退票时间与发车时间的距离计算：
```java
public BigDecimal calculateRefundAmount(BigDecimal totalAmount, LocalDateTime departureTime) {
    LocalDateTime now = LocalDateTime.now();
    long hoursBeforeDeparture = Duration.between(now, departureTime).toHours();
    
    if (hoursBeforeDeparture > 48) {
        // 发车前48小时以上，扣除5%手续费
        return totalAmount.multiply(new BigDecimal("0.95"));
    } else if (hoursBeforeDeparture > 24) {
        // 发车前24-48小时，扣除10%手续费
        return totalAmount.multiply(new BigDecimal("0.90"));
    } else if (hoursBeforeDeparture > 2) {
        // 发车前2-24小时，扣除20%手续费
        return totalAmount.multiply(new BigDecimal("0.80"));
    } else {
        // 发车前2小时内，不可退票
        throw new BusinessException("ORDER_005", "发车前2小时内不可退票");
    }
}
```

### 4. 订单完成自动化
车次出发后，系统自动将相关订单标记为已完成：
```java
@Component
public class OrderCompletionTask {
    
    @Scheduled(cron = "0 0/30 * * * ?") // 每30分钟执行一次
    public void completeDepartedOrders() {
        // 查询已出发车次的PAID状态订单
        List<Order> ordersToComplete = orderMapper.selectOrdersForCompletion();
        
        for (Order order : ordersToComplete) {
            try {
                orderService.completeOrder(order.getOrderNo());
            } catch (Exception e) {
                log.error("标记订单完成失败：{}", order.getOrderNo(), e);
            }
        }
    }
}
```

## 缓存设计
### 1. 用户订单列表缓存
- **缓存键**: `user_orders:{userId}:{status}:{page}:{size}`
- **缓存值**: 序列化的订单列表
- **过期时间**: 2分钟
- **清除时机**: 订单状态变更时清除相关用户的订单缓存

### 2. 订单详情缓存
- **缓存键**: `order:{orderNo}`
- **缓存值**: 完整的订单详情
- **过期时间**: 5分钟
- **清除时机**: 订单状态变更时清除

### 3. 缓存清除策略
```java
public void clearOrderCache(Long userId, String orderNo) {
    // 清除用户所有状态的分页缓存（使用通配符）
    cacheService.deleteByPattern("user_orders:" + userId + ":*");
    
    // 清除订单详情缓存
    cacheService.delete("order:" + orderNo);
    
    // 清除车次相关缓存
    // ...
}
```

## 错误处理
### 常见错误码
| 错误码 | 说明 | HTTP状态码 |
|--------|------|------------|
| ORDER_001 | 订单不存在 | 404 |
| ORDER_002 | 订单不属于当前用户 | 403 |
| ORDER_003 | 订单状态不正确 | 400 |
| ORDER_004 | 支付超时，订单已自动取消 | 400 |
| ORDER_005 | 不在可退票时间内 | 400 |
| ORDER_006 | 订单已完成，不可操作 | 400 |
| ORDER_007 | 订单已取消，不可操作 | 400 |
| ORDER_008 | 订单已退票，不可操作 | 400 |

## AI工具集成
智能客服可以通过AI工具调用订单相关功能：

```java
@Tool("查询我的订单")
public List<OrderVO> getMyOrders(
    @P("订单状态，可选值：PENDING-待支付，PAID-已支付，CANCELLED-已取消，REFUNDED-已退票，COMPLETED-已完成") 
    String status
) {
    return orderService.getUserOrders(getCurrentUserId(), status);
}

@Tool("退票")
public RefundResult refundOrder(
    @P("订单号") String orderNo,
    @P("退票原因") String reason
) {
    return orderService.refundOrder(orderNo, reason);
}
```

## 数据一致性保障
1. **事务管理**: 所有状态变更操作都在事务中执行
2. **幂等性设计**: 支付、退票等操作支持幂等调用
3. **补偿机制**: 通过定时任务处理异常状态订单
4. **审计日志**: 记录所有状态变更操作（预留功能）