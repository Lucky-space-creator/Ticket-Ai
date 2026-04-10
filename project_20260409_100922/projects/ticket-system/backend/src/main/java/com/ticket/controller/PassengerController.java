package com.ticket.controller;

import com.ticket.entity.Passenger;
import com.ticket.entity.User;
import com.ticket.enums.ResponseCode;
import com.ticket.service.PassengerService;
import com.ticket.service.UserService;
import com.ticket.util.CryptoUtil;
import com.ticket.util.JwtUtil;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 常用联系人控制器
 */
@RestController
@RequestMapping("/api/passengers")
@CrossOrigin(origins = "*")
public class PassengerController {

    @Resource
    private UserService userService;

    @Resource
    private PassengerService passengerService;

    @Resource
    private JwtUtil jwtUtil;

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            return jwtUtil.getUserIdFromToken(token);
        }
        return null;
    }

    /**
     * 获取常用联系人列表
     */
    @GetMapping
    public ResponseUtil.Result<?> getPassengers(HttpServletRequest request) {
        try {
            Long userId = getCurrentUserId(request);
            if (userId == null) {
                return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
            }

            User user = userService.getById(userId);

            List<Passenger> passengers = passengerService.getByUserId(user);

            return ResponseUtil.success(passengers);
        } catch (Exception e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    /**
     * 添加常用联系人
     */
    @PostMapping
    public ResponseUtil.Result<?> addPassenger(
            @RequestBody AddPassengerRequest addRequest,
            HttpServletRequest httpRequest
    ) {
        try {
            Long userId = getCurrentUserId(httpRequest);
            if (userId == null) {
                return ResponseUtil.error(com.ticket.enums.ResponseCode.UNAUTHORIZED);
            }

            Passenger passenger = passengerService.addPassenger(
                    userId,
                    addRequest.getName(),
                    addRequest.getIdCard(),
                    addRequest.getPhone()
            );

            return ResponseUtil.success("添加成功", passenger);
        } catch (Exception e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    /**
     * 删除常用联系人
     */
    @DeleteMapping("/{id}")
    public ResponseUtil.Result<?> deletePassenger(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        try {
            Long userId = getCurrentUserId(request);
            if (userId == null) {
                return ResponseUtil.error(com.ticket.enums.ResponseCode.UNAUTHORIZED);
            }

            boolean result = passengerService.deletePassenger(id, userId);

            if (result) {
                return ResponseUtil.success("删除成功");
            } else {
                return ResponseUtil.error("删除失败");
            }
        } catch (Exception e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    /**
     * 添加联系人请求DTO
     */
    @lombok.Data
    public static class AddPassengerRequest {
        private String name;
        private String idCard;
        private String phone;
    }
}
