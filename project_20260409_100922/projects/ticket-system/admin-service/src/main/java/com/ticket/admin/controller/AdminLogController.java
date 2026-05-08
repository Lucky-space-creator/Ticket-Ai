package com.ticket.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticket.admin.mapper.OperationLogMapper;
import com.ticket.entity.OperationLog;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 操作日志管理控制器
 * 提供后台管理端操作日志的查询（分页+条件）和删除功能。
 * 注意：仅支持查和删，不提供新增/修改接口。
 */
@RestController
@RequestMapping("/api/admin/logs")
@CrossOrigin(origins = "*")
public class AdminLogController {

    @Resource
    private OperationLogMapper operationLogMapper;

    /**
     * 分页查询操作日志列表
     * 支持按模块、操作类型、用户名、状态、时间范围筛选
     */
    @GetMapping
    public ResponseUtil.Result<Page<OperationLog>> list(
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "operation", required = false) String operation,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "startTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        Page<OperationLog> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<OperationLog> query = new LambdaQueryWrapper<>();

        // 筛选模块、操作类型、用户名、状态、时间范围
        if (StringUtils.hasText(module)) {
            query.eq(OperationLog::getModule, module);
        }
        if (StringUtils.hasText(operation)) {
            query.like(OperationLog::getOperation, operation);
        }
        if (StringUtils.hasText(username)) {
            query.like(OperationLog::getUsername, username);
        }
        if (status != null) {
            query.eq(OperationLog::getStatus, status);
        }
        if (startTime != null) {
            query.ge(OperationLog::getCreatedAt, startTime);
        }
        if (endTime != null) {
            query.le(OperationLog::getCreatedAt, endTime);
        }

        // 按创建时间倒序，最新的日志在前
        query.orderByDesc(OperationLog::getCreatedAt);

        Page<OperationLog> result = operationLogMapper.selectPage(pageParam, query);
        return ResponseUtil.success(result);
    }

    /**
     * 删除单条操作日志
     */
    @DeleteMapping("/{id}")
    public ResponseUtil.Result<?> delete(@PathVariable("id") Long id) {
        if (id == null) {
            return ResponseUtil.error("日志ID不能为空");
        }
        int rows = operationLogMapper.deleteById(id);
        if (rows > 0) {
            return ResponseUtil.success("删除成功");
        }
        return ResponseUtil.error("日志不存在或已删除");
    }
}
