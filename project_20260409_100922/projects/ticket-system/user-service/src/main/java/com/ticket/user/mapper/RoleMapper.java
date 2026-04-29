package com.ticket.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.common.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色Mapper接口
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}