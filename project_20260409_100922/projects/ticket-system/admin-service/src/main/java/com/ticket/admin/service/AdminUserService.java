package com.ticket.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.entity.User;

/**
 * 管理端「用户目录」应用服务端口：用户聚合由 user-service 拥有，此处仅做后台维度的查询与属性更新（共享库表场景）。
 */
public interface AdminUserService extends IService<User> {

    /**
     * 更新用户角色。
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     * @return 是否更新成功
     */
    boolean updateUserRole(Long userId, Long roleId);
}
