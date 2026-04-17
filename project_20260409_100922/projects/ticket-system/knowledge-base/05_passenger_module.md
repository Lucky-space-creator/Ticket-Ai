# 乘客管理模块

## 概述
乘客管理模块负责管理用户的常用联系人（乘客）信息，支持乘客信息的增删改查，并确保敏感信息（如身份证号）的安全存储。乘客信息在购票时自动填充，提高用户体验。

## 主要功能
1. **添加乘客** - 添加新的常用联系人
2. **修改乘客** - 更新乘客信息
3. **删除乘客** - 删除不再使用的联系人
4. **查询乘客** - 查询当前用户的所有乘客
5. **身份证加密** - 使用AES加密存储身份证号
6. **信息验证** - 验证身份证号、手机号格式

## 数据模型
### 乘客表（passenger）
```sql
CREATE TABLE `passenger` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `name` varchar(50) NOT NULL COMMENT '乘客姓名',
  `id_card` varchar(100) NOT NULL COMMENT '身份证号（加密存储）',
  `id_card_type` varchar(10) DEFAULT 'ID_CARD' COMMENT '证件类型：ID_CARD-身份证，PASSPORT-护照，OTHER-其他',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `passenger_type` varchar(10) DEFAULT 'ADULT' COMMENT '乘客类型：ADULT-成人，CHILD-儿童，STUDENT-学生，SOLDIER-军人',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-禁用，1-正常',
  `is_default` tinyint DEFAULT '0' COMMENT '是否默认乘客：0-否，1-是',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='乘客表';
```

## 证件类型定义
```java
public enum IdCardType {
    ID_CARD("身份证"),
    PASSPORT("护照"),
    OTHER("其他证件");
    
    private final String description;
    
    IdCardType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
```

## 乘客类型定义
```java
public enum PassengerType {
    ADULT("成人"),      // 全价票
    CHILD("儿童"),      // 半价票（1.2-1.5米）
    STUDENT("学生"),    // 学生票（凭证购买）
    SOLDIER("军人");    // 军人票（凭证购买）
    
    private final String description;
    
    PassengerType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
```

## 核心接口
### 1. 添加乘客
**请求地址**: `POST /api/passengers`

**请求参数**:
```json
{
  "name": "张三",
  "idCard": "110101199001011234",
  "idCardType": "ID_CARD",
  "phone": "13800138000",
  "passengerType": "ADULT",
  "isDefault": true
}
```

**处理流程**:
1. 验证当前用户身份
2. 验证身份证号格式（15位或18位）
3. 验证手机号格式
4. 使用AES加密身份证号
5. 检查是否已存在相同身份证号的乘客（同一用户下）
6. 如果设置为默认乘客，先取消其他乘客的默认状态
7. 创建乘客记录
8. 返回创建结果

### 2. 修改乘客
**请求地址**: `PUT /api/passengers/{id}`

**请求参数**: 同添加接口

**处理流程**:
1. 验证乘客是否存在且属于当前用户
2. 验证身份证号格式（如果修改）
3. 加密身份证号（如果修改）
4. 如果修改为默认乘客，先取消其他乘客的默认状态
5. 更新乘客信息
6. 返回更新结果

### 3. 删除乘客
**请求地址**: `DELETE /api/passengers/{id}`

**处理流程**:
1. 验证乘客是否存在且属于当前用户
2. 检查乘客是否有未完成的订单（预留）
3. 逻辑删除（更新状态为0）或物理删除
4. 返回删除结果

### 4. 查询乘客列表
**请求地址**: `GET /api/passengers`

**查询参数**:
- `status` - 状态（可选，默认只查询正常状态）
- `passengerType` - 乘客类型（可选）
- `isDefault` - 是否默认乘客（可选）

**响应示例**:
```json
{
  "success": true,
  "code": "200",
  "message": "查询成功",
  "data": [
    {
      "id": 1,
      "name": "张三",
      "idCardMask": "110101********1234",  // 脱敏显示
      "idCardType": "ID_CARD",
      "phone": "138****8000",  // 脱敏显示
      "passengerType": "ADULT",
      "isDefault": true,
      "createTime": "2026-04-10 10:30:00"
    }
  ]
}
```

### 5. 查询乘客详情
**请求地址**: `GET /api/passengers/{id}`

**处理流程**:
1. 验证乘客是否存在且属于当前用户
2. 查询乘客信息
3. 返回脱敏后的乘客详情（不返回完整身份证号）

## 核心业务逻辑
### 1. 身份证号加密存储
所有身份证号在存储前都进行AES加密：

```java
@Component
public class CryptoUtil {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private final SecretKey secretKey;
    private final IvParameterSpec iv;
    
    public CryptoUtil(@Value("${app.encryption.key}") String key) {
        // 从配置读取加密密钥
        this.secretKey = new SecretKeySpec(key.getBytes(), ALGORITHM);
        this.iv = new IvParameterSpec(key.substring(0, 16).getBytes());
    }
    
    public String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }
    
    public String decrypt(String encryptedText) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }
}
```

### 2. 身份证号脱敏显示
在返回给前端的数据中，身份证号进行脱敏处理：

```java
public String maskIdCard(String idCard) {
    if (idCard == null || idCard.length() < 8) {
        return idCard;
    }
    
    if (idCard.length() == 15) {
        // 15位身份证：前6位 + 6个* + 后3位
        return idCard.substring(0, 6) + "******" + idCard.substring(12);
    } else if (idCard.length() == 18) {
        // 18位身份证：前6位 + 8个* + 后4位
        return idCard.substring(0, 6) + "********" + idCard.substring(14);
    }
    
    return idCard;
}
```

### 3. 手机号脱敏显示
```java
public String maskPhone(String phone) {
    if (phone == null || phone.length() < 7) {
        return phone;
    }
    
    // 保留前3位和后4位，中间用*代替
    return phone.substring(0, 3) + "****" + phone.substring(7);
}
```

### 4. 默认乘客管理
每个用户只能有一个默认乘客，设置新默认乘客时需要取消旧的：

```java
public void setDefaultPassenger(Long userId, Long passengerId) {
    // 1. 取消当前用户的所有默认乘客
    passengerMapper.clearDefaultPassengers(userId);
    
    // 2. 设置新的默认乘客
    passengerMapper.setAsDefault(passengerId);
}
```

### 5. 身份证号格式验证
```java
public boolean validateIdCard(String idCard) {
    if (idCard == null || idCard.isEmpty()) {
        return false;
    }
    
    // 15位或18位
    if (!idCard.matches("^\\d{15}|\\d{17}[0-9Xx]$")) {
        return false;
    }
    
    // 18位身份证校验码验证
    if (idCard.length() == 18) {
        return validateIdCard18(idCard);
    }
    
    return true;
}

private boolean validateIdCard18(String idCard) {
    // 权重因子
    int[] weight = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    // 校验码对应值
    char[] validate = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
    
    int sum = 0;
    for (int i = 0; i < 17; i++) {
        sum += (idCard.charAt(i) - '0') * weight[i];
    }
    
    char expected = validate[sum % 11];
    char actual = Character.toUpperCase(idCard.charAt(17));
    
    return expected == actual;
}
```

## 缓存设计
### 1. 用户乘客列表缓存
- **缓存键**: `user_passengers:{userId}`
- **缓存值**: 序列化的乘客列表（脱敏后）
- **过期时间**: 10分钟
- **清除时机**: 乘客增删改时清除

### 2. 乘客详情缓存
- **缓存键**: `passenger:{passengerId}`
- **缓存值**: 乘客详情（脱敏后）
- **过期时间**: 30分钟
- **清除时机**: 乘客信息修改时清除

## 错误处理
### 常见错误码
| 错误码 | 说明 | HTTP状态码 |
|--------|------|------------|
| PASSENGER_001 | 乘客不存在 | 404 |
| PASSENGER_002 | 乘客不属于当前用户 | 403 |
| PASSENGER_003 | 身份证号格式不正确 | 400 |
| PASSENGER_004 | 手机号格式不正确 | 400 |
| PASSENGER_005 | 已存在相同身份证号的乘客 | 400 |
| PASSENGER_006 | 乘客有关联订单，无法删除 | 400 |

## 安全考虑
### 1. 数据权限控制
- 乘客信息严格按用户ID隔离
- 查询接口必须验证当前用户权限
- 防止越权访问其他用户的乘客信息

### 2. 敏感信息保护
- 身份证号加密存储
- 接口返回脱敏数据
- 只有授权接口（如购票）才解密身份证号
- 操作日志不记录完整身份证号

### 3. 输入验证
- 身份证号格式验证
- 手机号格式验证
- 姓名长度限制（2-50字符）
- 防止SQL注入和XSS攻击

## 与购票流程的集成
### 1. 购票时选择乘客
用户购票时从乘客列表选择，避免重复输入：

```java
public List<PassengerVO> getAvailablePassengers(Long userId) {
    // 查询用户所有正常状态的乘客
    return passengerMapper.selectByUserAndStatus(userId, 1);
}
```

### 2. 自动填充乘客信息
创建订单时自动使用乘客信息：

```java
public OrderCreateRequest prepareOrderRequest(Long userId, List<Long> passengerIds) {
    OrderCreateRequest request = new OrderCreateRequest();
    request.setUserId(userId);
    request.setPassengerIds(passengerIds);
    
    // 获取乘客信息并填充
    List<Passenger> passengers = passengerMapper.selectByIds(passengerIds);
    
    // 验证所有乘客都属于该用户
    for (Passenger passenger : passengers) {
        if (!passenger.getUserId().equals(userId)) {
            throw new BusinessException("PASSENGER_002", "乘客不属于当前用户");
        }
    }
    
    // 使用默认乘客的联系方式，或第一个乘客的信息
    Passenger contact = findContactPassenger(passengers);
    request.setContactName(contact.getName());
    request.setContactPhone(contact.getPhone());
    
    return request;
}
```

## AI工具集成
智能客服可以通过AI工具管理乘客：

```java
@Tool("添加常用乘客")
public PassengerVO addPassenger(
    @P("乘客姓名") String name,
    @P("身份证号") String idCard,
    @P("手机号") String phone
) {
    PassengerCreateRequest request = new PassengerCreateRequest();
    request.setName(name);
    request.setIdCard(idCard);
    request.setPhone(phone);
    request.setPassengerType("ADULT");
    
    return passengerService.addPassenger(getCurrentUserId(), request);
}

@Tool("查询我的常用乘客")
public List<PassengerVO> getMyPassengers() {
    return passengerService.getPassengers(getCurrentUserId());
}
```

## 最佳实践
### 1. 数据清理
- 定期清理长时间未使用的乘客记录（预留）
- 用户注销时删除所有关联乘客（预留）

### 2. 性能优化
- 乘客列表分页查询，避免数据量过大
- 使用缓存减少数据库查询
- 建立合适的索引（user_id, status）

### 3. 用户体验
- 支持设置默认乘客，简化购票流程
- 提供乘客信息批量导入功能（预留）
- 支持乘客分组管理（如家人、同事）（预留）