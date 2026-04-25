-- 修复权限API路径不匹配问题
-- 执行此脚本以更新已存在的权限记录，使其与实际Controller路径一致

-- 1. 更新请求人工客服接口路径
UPDATE `permission` 
SET `api_path` = '/api/customer-service/request-human'
WHERE `permission_name` = 'user:customer_service:request' 
  AND `api_path` = '/api/customer-service/request';

-- 2. 检查其他可能不匹配的权限路径（暂无）

-- 3. 验证用户角色分配（可选）
-- 如果特定用户缺少user角色，可以执行以下更新（将用户ID替换为实际值）
-- UPDATE `user` SET `role_id` = (SELECT `id` FROM `role` WHERE `role_name` = 'user') WHERE `id` = 2046884899892834306;

-- 4. 检查用户当前角色和权限
SELECT 
    u.id AS user_id,
    u.phone,
    r.role_name,
    r.role_display_name,
    p.permission_name,
    p.api_path,
    p.api_method
FROM `user` u
LEFT JOIN `role` r ON u.role_id = r.id
LEFT JOIN `role_permission` rp ON r.id = rp.role_id
LEFT JOIN `permission` p ON rp.permission_id = p.id
WHERE u.id = 2046884899892834306
   OR u.phone = '用户手机号';  -- 替换为实际手机号

-- 5. 确认权限已更新
SELECT 
    permission_name,
    permission_display_name,
    api_method,
    api_path,
    description
FROM `permission`
WHERE permission_name LIKE '%customer_service%'
   OR permission_name LIKE '%chat%'
   OR permission_name LIKE '%user:%';
