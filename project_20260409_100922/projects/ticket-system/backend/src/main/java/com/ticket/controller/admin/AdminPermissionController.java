package com.ticket.controller.admin;

import com.ticket.entity.Permission;
import com.ticket.service.PermissionService;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端权限控制器
 */
@RestController
@RequestMapping("/api/admin/permissions")
@CrossOrigin(origins = "*")
public class AdminPermissionController {

    @Resource
    private PermissionService permissionService;

    /**
     * 获取权限列表
     */
    @GetMapping
    public ResponseUtil.Result<List<Permission>> list() {
        List<Permission> permissions = permissionService.list();
        return ResponseUtil.success(permissions);
    }

    /**
     * 获取权限树
     */
    @GetMapping("/tree")
    public ResponseUtil.Result<List<Permission>> tree() {
        List<Permission> tree = permissionService.getPermissionTree();
        return ResponseUtil.success(tree);
    }

    /**
     * 获取菜单权限列表
     */
    @GetMapping("/menus")
    public ResponseUtil.Result<List<Permission>> menus() {
        List<Permission> menus = permissionService.getMenuPermissions();
        return ResponseUtil.success(menus);
    }

    /**
     * 获取API权限列表
     */
    @GetMapping("/apis")
    public ResponseUtil.Result<List<Permission>> apis() {
        List<Permission> apis = permissionService.getApiPermissions();
        return ResponseUtil.success(apis);
    }

    /**
     * 获取权限详情
     */
    @GetMapping("/{id}")
    public ResponseUtil.Result<Permission> detail(@PathVariable Long id) {
        Permission permission = permissionService.getById(id);
        if (permission == null) {
            return ResponseUtil.error("权限不存在");
        }
        return ResponseUtil.success(permission);
    }

    /**
     * 创建权限
     */
    @PostMapping
    public ResponseUtil.Result<Permission> create(@RequestBody CreatePermissionRequest request) {
        Permission permission = new Permission();
        permission.setPermissionName(request.getPermissionName());
        permission.setPermissionDisplayName(request.getPermissionDisplayName());
        permission.setPermissionType(request.getPermissionType());
        permission.setParentId(request.getParentId());
        permission.setPath(request.getPath());
        permission.setComponent(request.getComponent());
        permission.setIcon(request.getIcon());
        permission.setSort(request.getSort());
        permission.setApiMethod(request.getApiMethod());
        permission.setApiPath(request.getApiPath());
        permission.setDescription(request.getDescription());
        permission.setStatus(1);
        permissionService.save(permission);
        return ResponseUtil.success("创建成功", permission);
    }

    /**
     * 更新权限
     */
    @PutMapping("/{id}")
    public ResponseUtil.Result<Permission> update(@PathVariable Long id, @RequestBody UpdatePermissionRequest request) {
        Permission permission = permissionService.getById(id);
        if (permission == null) {
            return ResponseUtil.error("权限不存在");
        }
        permission.setPermissionDisplayName(request.getPermissionDisplayName());
        permission.setPermissionType(request.getPermissionType());
        permission.setParentId(request.getParentId());
        permission.setPath(request.getPath());
        permission.setComponent(request.getComponent());
        permission.setIcon(request.getIcon());
        permission.setSort(request.getSort());
        permission.setApiMethod(request.getApiMethod());
        permission.setApiPath(request.getApiPath());
        permission.setDescription(request.getDescription());
        permissionService.updateById(permission);
        return ResponseUtil.success("更新成功", permission);
    }

    /**
     * 更新权限状态
     */
    @PutMapping("/{id}/status")
    public ResponseUtil.Result<?> updateStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest request) {
        Permission permission = permissionService.getById(id);
        if (permission == null) {
            return ResponseUtil.error("权限不存在");
        }
        permission.setStatus(request.getStatus());
        permissionService.updateById(permission);
        return ResponseUtil.success("状态更新成功");
    }

    /**
     * 删除权限
     */
    @DeleteMapping("/{id}")
    public ResponseUtil.Result<?> delete(@PathVariable Long id) {
        Permission permission = permissionService.getById(id);
        if (permission == null) {
            return ResponseUtil.error("权限不存在");
        }
        permissionService.removeById(id);
        return ResponseUtil.success("删除成功");
    }

    @lombok.Data
    public static class CreatePermissionRequest {
        private String permissionName;
        private String permissionDisplayName;
        private Integer permissionType;
        private Long parentId;
        private String path;
        private String component;
        private String icon;
        private Integer sort;
        private String apiMethod;
        private String apiPath;
        private String description;
    }

    @lombok.Data
    public static class UpdatePermissionRequest {
        private String permissionDisplayName;
        private Integer permissionType;
        private Long parentId;
        private String path;
        private String component;
        private String icon;
        private Integer sort;
        private String apiMethod;
        private String apiPath;
        private String description;
    }

    @lombok.Data
    public static class UpdateStatusRequest {
        private Integer status;
    }
}