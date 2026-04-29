package com.ticket.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.common.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限Mapper接口
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}