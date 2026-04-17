# 用户认证模块

## 概述
用户认证模块负责处理用户注册、登录、令牌管理和安全验证。采用JWT（JSON Web Token）作为无状态认证机制，结合BCrypt密码加密，确保用户账户安全。

## 主要功能
1. **用户注册** - 创建新账户，验证用户名唯一性
2. **用户登录** - 验证凭证，生成JWT令牌
3. **令牌刷新** - 使用刷新令牌获取新的访问令牌
4. **密码重置** - （预留功能）通过邮箱验证重置密码
5. **用户信息获取** - 获取当前登录用户信息

## 数据库表设计
### user 表结构
```sql
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '加密后的密码',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `id_card` varchar(50) DEFAULT NULL COMMENT '身份证号（加密存储）',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-禁用，1-正常',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

## 核心接口
### 1. 用户注册
**请求地址**: `POST /api/auth/register`

**请求参数**:
```json
{
  "username": "testuser",
  "password": "Test123456",
  "email": "test@example.com",
  "phone": "13800138000",
  "realName": "张三",
  "idCard": "110101199001011234"
}
```

**处理流程**:
1. 验证用户名是否已存在
2. 使用BCrypt加密密码
3. 加密身份证号（如果提供）
4. 创建用户记录
5. 返回注册成功信息

### 2. 用户登录
**请求地址**: `POST /api/auth/login`

**请求参数**:
```json
{
  "username": "testuser",
  "password": "Test123456"
}
```

**处理流程**:
1. 根据用户名查询用户
2. 验证用户状态是否正常
3. 使用BCrypt验证密码
4. 生成JWT访问令牌和刷新令牌
5. 返回令牌信息

**响应示例**:
```json
{
  "success": true,
  "code": "200",
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 7200,
    "tokenType": "Bearer",
    "userInfo": {
      "id": 1,
      "username": "testuser",
      "email": "test@example.com",
      "realName": "张三"
    }
  }
}
```

### 3. 令牌刷新
**请求地址**: `POST /api/auth/refresh-token`

**请求头**:
```
Authorization: Bearer {refreshToken}
```

**处理流程**:
1. 验证刷新令牌有效性
2. 解析用户信息
3. 生成新的访问令牌
4. 返回新的令牌信息

## JWT配置
### 令牌结构
- **访问令牌**: 有效期2小时，用于API调用授权
- **刷新令牌**: 有效期7天，用于获取新的访问令牌

### 配置参数（application.yml）
```yaml
jwt:
  secret-key: "your-jwt-secret-key-change-this-in-production"
  access-token-expiration: 7200  # 2小时（秒）
  refresh-token-expiration: 604800  # 7天（秒）
```

### 令牌验证流程
1. 客户端在请求头中携带访问令牌：`Authorization: Bearer {token}`
2. 服务端拦截器验证令牌签名和有效期
3. 解析用户ID并存入SecurityContext
4. 业务层通过`@CurrentUser`注解获取当前用户

## 安全机制
### 1. 密码加密
使用BCrypt算法，自动加盐存储：
```java
// 密码加密
String encryptedPassword = BCrypt.hashpw(rawPassword, BCrypt.gensalt());

// 密码验证
boolean isValid = BCrypt.checkpw(rawPassword, encryptedPassword);
```

### 2. 敏感信息加密
身份证号使用AES加密存储：
```java
// 加密
String encryptedIdCard = CryptoUtil.encrypt(idCard, encryptionKey);

// 解密（仅限授权接口）
String decryptedIdCard = CryptoUtil.decrypt(encryptedIdCard, encryptionKey);
```

### 3. 输入验证
- 用户名：4-20位字母数字组合
- 密码：8-20位，必须包含字母和数字
- 手机号：中国手机号格式验证
- 邮箱：标准邮箱格式验证
- 身份证号：15位或18位格式验证

### 4. 防暴力破解
- 登录失败次数限制（预留功能）
- 验证码机制（预留功能）

## 测试账号
系统预置测试账号，方便开发测试：
- 用户名: `testuser`
- 密码: `Test123456`
- 权限: 普通用户

## 错误码说明
| 错误码 | 说明 | HTTP状态码 |
|--------|------|------------|
| AUTH_001 | 用户名已存在 | 400 |
| AUTH_002 | 用户名或密码错误 | 401 |
| AUTH_003 | 用户已被禁用 | 403 |
| AUTH_004 | 令牌已过期 | 401 |
| AUTH_005 | 令牌无效 | 401 |
| AUTH_006 | 刷新令牌无效 | 401 |

## 前端集成
### 令牌存储
前端将令牌存储在localStorage中：
```javascript
// 登录成功后保存令牌
localStorage.setItem('accessToken', response.data.accessToken);
localStorage.setItem('refreshToken', response.data.refreshToken);
```

### 请求拦截器
Axios拦截器自动添加Authorization头：
```javascript
axios.interceptors.request.use(config => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
```

### 令牌刷新机制
当访问令牌过期时，自动使用刷新令牌获取新令牌：
```javascript
axios.interceptors.response.use(
  response => response,
  async error => {
    if (error.response?.status === 401) {
      // 尝试刷新令牌
      const newToken = await refreshToken();
      if (newToken) {
        // 重试原始请求
        return axios(error.config);
      }
    }
    return Promise.reject(error);
  }
);
```

## 最佳实践
1. **前端安全**: 令牌存储在localStorage，生产环境可考虑HttpOnly Cookie
2. **密码策略**: 强制用户使用强密码，定期提示修改
3. **会话管理**: 支持多设备登录，提供设备管理功能（预留）
4. **审计日志**: 记录登录登出、密码修改等敏感操作（预留）