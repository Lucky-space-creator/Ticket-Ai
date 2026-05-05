package com.ticket.user.controller;

import com.ticket.entity.Passenger;
import com.ticket.entity.User;
import com.ticket.enums.ResponseCode;
import com.ticket.user.service.PassengerService;
import com.ticket.user.service.UserService;
import com.ticket.util.CryptoUtil;
import com.ticket.util.ResponseUtil;
import com.ticket.util.UserContext;
import jakarta.annotation.Resource;
import org.slf4j.LoggerFactory;
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
    private PassengerService passengerService;

    /**
     * 获取常用联系人列表
     */
    @GetMapping
    public ResponseUtil.Result<?> getPassengers() {
        try {
            Long userId = UserContext.getCurrentUserId();
            if (userId == null) {
                return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
            }

            List<Passenger> passengers = passengerService.getByUserId(userId);

            // 解密身份证后返回（增加解密失败保护）
            List<Passenger> decryptedPassengers = passengers.stream()
                    .map(p -> {
                        Passenger copy = new Passenger();
                        copy.setId(p.getId());
                        copy.setUserId(p.getUserId());
                        copy.setName(p.getName());
                        if (p.getIdCard() != null && !p.getIdCard().isEmpty()) {
                            String decrypted = CryptoUtil.decrypt(p.getIdCard());
                            if (decrypted == null) {
                                LoggerFactory.getLogger(PassengerController.class)
                                        .warn("联系人身份证解密失败: passengerId={}, idCard(前10位)={}",
                                                p.getId(), p.getIdCard().length() > 10
                                                        ? p.getIdCard().substring(0, 10) : p.getIdCard());
                            }
                            copy.setIdCard(decrypted);
                        } else {
                            copy.setIdCard(null);
                        }
                        copy.setPhone(p.getPhone());
                        copy.setCreatedAt(p.getCreatedAt());
                        return copy;
                    })
                    .collect(Collectors.toList());

            return ResponseUtil.success(decryptedPassengers);
        } catch (Exception e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    /**
     * 添加常用联系人
     */
    @PostMapping
    public ResponseUtil.Result<?> addPassenger(@RequestBody AddPassengerRequest addRequest) {
        try {
            Long userId = UserContext.getCurrentUserId();
            if (userId == null) {
                return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
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
    public ResponseUtil.Result<?> deletePassenger(@PathVariable Long id) {
        try {
            Long userId = UserContext.getCurrentUserId();
            if (userId == null) {
                return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
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
    public static class AddPassengerRequest {
        private String name;
        private String idCard;
        private String phone;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getIdCard() {
            return idCard;
        }
        
        public void setIdCard(String idCard) {
            this.idCard = idCard;
        }
        
        public String getPhone() {
            return phone;
        }
        
        public void setPhone(String phone) {
            this.phone = phone;
        }
    }
}