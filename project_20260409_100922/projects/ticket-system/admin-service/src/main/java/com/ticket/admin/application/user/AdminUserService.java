package com.ticket.admin.application.user;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.entity.User;

/**
 * 管理端「用户目录」应用服务端口：用户聚合由 user-service 拥有，此处仅做后台维度的查询与属性更新（共享库表场景）。
 */
public interface AdminUserService extends IService<User> {

    boolean updateUserRole(Long userId, Long roleId);
}
