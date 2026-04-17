# 车票预订模块

## 概述
车票预订模块是系统的核心业务模块，负责车次查询、余票管理、席别选择、订单创建等完整购票流程。该模块采用缓存优化、并发控制和事务管理，确保数据一致性和系统性能。

## 主要功能
1. **车次查询** - 根据出发地、目的地、日期搜索车次
2. **余票查询** - 查询特定车次、席别的实时余票
3. **席别管理** - 支持多种席别类型（一等座、二等座、商务座等）
4. **订单创建** - 创建购票订单，包含并发库存控制
5. **车次缓存** - 缓存热门车次信息，减少数据库压力

## 数据模型
### 车次表（train）
```sql
CREATE TABLE `train` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `train_no` varchar(20) NOT NULL COMMENT '车次号，如G123',
  `train_type` varchar(10) NOT NULL COMMENT '列车类型：G-高铁，D-动车，Z-直达，T-特快，K-快速',
  `departure_station` varchar(50) NOT NULL COMMENT '始发站',
  `arrival_station` varchar(50) NOT NULL COMMENT '终点站',
  `departure_time` time NOT NULL COMMENT '出发时间',
  `arrival_time` time NOT NULL COMMENT '到达时间',
  `duration_minutes` int NOT NULL COMMENT '运行时长（分钟）',
  `total_seats` int NOT NULL COMMENT '总座位数',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-停运，1-正常',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_train_no` (`train_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车次表';
```

### 车次余票库存表（train_seat_inventory）
```sql
CREATE TABLE `train_seat_inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `train_id` bigint NOT NULL COMMENT '车次ID',
  `departure_date` date NOT NULL COMMENT '出发日期',
  `seat_type` varchar(20) NOT NULL COMMENT '席别类型：FIRST_CLASS-一等座，SECOND_CLASS-二等座，BUSINESS-商务座，HARD_SLEEPER-硬卧，SOFT_SLEEPER-软卧',
  `total_quantity` int NOT NULL COMMENT '总票数',
  `available_quantity` int NOT NULL COMMENT '可售票数',
  `price` decimal(10,2) NOT NULL COMMENT '票价（元）',
  `version` int DEFAULT '0' COMMENT '版本号（用于乐观锁）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_train_date_seat` (`train_id`,`departure_date`,`seat_type`),
  KEY `idx_train_id` (`train_id`),
  KEY `idx_departure_date` (`departure_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车次余票库存表';
```

## 席别类型定义
系统支持以下席别类型：
- **FIRST_CLASS** - 一等座
- **SECOND_CLASS** - 二等座
- **BUSINESS** - 商务座
- **HARD_SEAT** - 硬座
- **SOFT_SEAT** - 软座
- **HARD_SLEEPER** - 硬卧
- **SOFT_SLEEPER** - 软卧
- **NO_SEAT** - 无座

## 核心接口
### 1. 车次搜索
**请求地址**: `GET /api/trains/search`

**请求参数**:
- `departureStation` - 出发站（必填）
- `arrivalStation` - 到达站（必填）
- `departureDate` - 出发日期（格式：yyyy-MM-dd）（必填）
- `trainType` - 列车类型（可选，如：G,D,Z,T,K）
- `page` - 页码（默认1）
- `size` - 每页数量（默认10）

**处理流程**:
1. 检查参数有效性
2. 查询缓存中是否有该搜索条件的结果
3. 如果缓存未命中，查询数据库
4. 对每个车次查询余票信息
5. 将结果存入缓存（有效期5分钟）
6. 返回分页结果

**响应示例**:
```json
{
  "success": true,
  "code": "200",
  "message": "查询成功",
  "data": {
    "list": [
      {
        "trainId": 1,
        "trainNo": "G123",
        "trainType": "G",
        "departureStation": "北京南",
        "arrivalStation": "上海虹桥",
        "departureTime": "08:00",
        "arrivalTime": "12:30",
        "durationMinutes": 270,
        "seatInventories": [
          {
            "seatType": "FIRST_CLASS",
            "availableQuantity": 120,
            "price": 553.0
          },
          {
            "seatType": "SECOND_CLASS",
            "availableQuantity": 450,
            "price": 553.0
          }
        ]
      }
    ],
    "total": 15,
    "page": 1,
    "size": 10
  }
}
```

### 2. 余票查询
**请求地址**: `GET /api/trains/{trainId}/inventory`

**请求参数**:
- `departureDate` - 出发日期（格式：yyyy-MM-dd）（必填）
- `seatType` - 席别类型（可选，不传则返回所有席别）

**处理流程**:
1. 验证车次是否存在且正常运营
2. 查询指定日期的余票库存
3. 返回余票数量和价格信息

### 3. 创建订单（车票预订）
**请求地址**: `POST /api/orders/create`

**请求参数**:
```json
{
  "trainId": 1,
  "departureDate": "2026-04-20",
  "seatType": "SECOND_CLASS",
  "passengerIds": [1, 2],
  "contactName": "张三",
  "contactPhone": "13800138000"
}
```

## 核心业务逻辑
### 1. 车次缓存策略
```java
// 缓存键格式：train_search:{departureStation}:{arrivalStation}:{departureDate}
String cacheKey = String.format("train_search:%s:%s:%s", 
    departureStation, arrivalStation, departureDate);

// 缓存有效期：5分钟
List<TrainVO> cachedResult = cacheService.get(cacheKey);
if (cachedResult != null) {
    return cachedResult;
}

// 数据库查询并缓存结果
List<TrainVO> result = trainMapper.searchTrains(params);
cacheService.set(cacheKey, result, 5, TimeUnit.MINUTES);
```

### 2. 余票查询与实时性
- 余票数量从`train_seat_inventory`表实时查询
- 为避免频繁查询数据库，对热门车次进行短期缓存（1分钟）
- 缓存键格式：`train_inventory:{trainId}:{departureDate}`

### 3. 创建订单的并发控制
创建订单是系统最复杂的业务流程，需要确保在高并发下不会超卖：

```java
@Transactional(rollbackFor = Exception.class)
public OrderCreateResult createOrder(OrderCreateRequest request) {
    // 1. 验证用户和乘客信息
    validateUserAndPassengers(request);
    
    // 2. 查询并锁定余票记录（使用SELECT FOR UPDATE）
    TrainSeatInventory inventory = trainSeatInventoryMapper.selectForUpdate(
        request.getTrainId(), 
        request.getDepartureDate(), 
        request.getSeatType()
    );
    
    // 3. 检查余票是否充足
    if (inventory.getAvailableQuantity() < request.getPassengerIds().size()) {
        throw new BusinessException("TICKET_001", "余票不足");
    }
    
    // 4. 扣减库存（使用乐观锁）
    int rows = trainSeatInventoryMapper.decreaseInventory(
        inventory.getId(), 
        request.getPassengerIds().size(),
        inventory.getVersion()
    );
    
    if (rows == 0) {
        // 乐观锁冲突，重试或抛异常
        throw new BusinessException("TICKET_002", "库存已变更，请重试");
    }
    
    // 5. 生成订单号
    String orderNo = generateOrderNo();
    
    // 6. 创建订单主记录
    Order order = new Order();
    order.setOrderNo(orderNo);
    order.setUserId(currentUser.getId());
    // ... 设置其他字段
    orderMapper.insert(order);
    
    // 7. 创建订单明细（每张票）
    for (Long passengerId : request.getPassengerIds()) {
        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setPassengerId(passengerId);
        // ... 设置其他字段
        orderItemMapper.insert(item);
    }
    
    // 8. 清理相关缓存
    clearOrderCache(currentUser.getId());
    clearTrainCache(request.getTrainId(), request.getDepartureDate());
    
    // 9. 返回创建结果
    return new OrderCreateResult(orderNo, order.getId());
}
```

### 4. 库存扣减的SQL（乐观锁实现）
```sql
UPDATE train_seat_inventory 
SET available_quantity = available_quantity - #{quantity},
    version = version + 1,
    update_time = NOW()
WHERE id = #{id} 
  AND available_quantity >= #{quantity}
  AND version = #{version}
```

## 缓存设计
### 1. 车次搜索缓存
- **缓存键**: `train_search:{departureStation}:{arrivalStation}:{departureDate}`
- **缓存值**: 序列化的车次列表（包含余票信息）
- **过期时间**: 5分钟
- **刷新策略**: 缓存失效后重新查询数据库

### 2. 车次详情缓存
- **缓存键**: `train:{trainId}`
- **缓存值**: 车次基本信息
- **过期时间**: 1小时
- **刷新策略**: 车次信息变更时清除缓存

### 3. 余票缓存
- **缓存键**: `train_inventory:{trainId}:{departureDate}`
- **缓存值**: 各席别余票数量
- **过期时间**: 1分钟（短时间缓存，保证一定实时性）
- **刷新策略**: 订单创建成功后清除相关缓存

## 错误处理
### 常见错误码
| 错误码 | 说明 | HTTP状态码 |
|--------|------|------------|
| TRAIN_001 | 车次不存在或已停运 | 404 |
| TRAIN_002 | 出发日期不能早于今天 | 400 |
| TRAIN_003 | 出发站和到达站不能相同 | 400 |
| INVENTORY_001 | 余票不足 | 400 |
| INVENTORY_002 | 席别类型不支持 | 400 |
| ORDER_001 | 乘客信息不完整 | 400 |
| ORDER_002 | 乘客不属于当前用户 | 403 |

### 事务回滚场景
1. 库存扣减失败
2. 订单创建失败
3. 订单明细创建失败
4. 任何数据库操作异常

## 性能优化
### 1. 查询优化
- 为`train_seat_inventory`表建立复合索引：`(train_id, departure_date, seat_type)`
- 分页查询使用覆盖索引减少回表
- 避免N+1查询，使用JOIN一次性获取关联数据

### 2. 缓存优化
- 使用多级缓存：本地缓存 + Redis（如果部署）
- 缓存预热：系统启动时加载热门车次到缓存
- 缓存穿透防护：对不存在的车次ID缓存空值（短时间）

### 3. 并发优化
- 使用数据库行锁（SELECT FOR UPDATE）保证库存一致性
- 乐观锁减少锁竞争
- 订单创建接口限流，防止恶意刷票

## AI工具集成
智能客服可以通过AI工具调用车票查询功能：

```java
@Tool("查询车次信息")
public List<TrainVO> searchTrains(
    @P("出发站") String departureStation,
    @P("到达站") String arrivalStation,
    @P("出发日期，格式：yyyy-MM-dd") String departureDate
) {
    // 调用内部服务方法
    return trainService.searchTrains(departureStation, arrivalStation, departureDate);
}
```

这样用户可以通过自然语言查询车次，如："帮我查一下4月20号从北京到上海的高铁"。