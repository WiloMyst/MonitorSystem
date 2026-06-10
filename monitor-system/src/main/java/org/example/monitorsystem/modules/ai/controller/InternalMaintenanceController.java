package org.example.monitorsystem.modules.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.monitorsystem.core.web.Result;
import org.example.monitorsystem.modules.ai.entity.MaintenanceOrder;
import org.example.monitorsystem.modules.ai.mapper.MaintenanceOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 内部维修调度控制器
 * 供 AI 微服务创建维修工单、查询维修历史和跟踪维修进度。
 */
@RestController
@RequestMapping("/api/internal/maintenance")
public class InternalMaintenanceController {

    @Autowired
    private MaintenanceOrderMapper maintenanceOrderMapper;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @PostMapping("/create")
    public Result<Map<String, Object>> createOrder(@RequestBody Map<String, String> payload) {
        String deviceCode = payload.getOrDefault("deviceCode", "");
        String faultDescription = payload.getOrDefault("faultDescription", "");
        String priority = payload.getOrDefault("priority", "medium");
        String assignedTeam = payload.getOrDefault("assignedTeam", "");

        if (deviceCode.isEmpty() || faultDescription.isEmpty()) {
            return Result.error(400, "deviceCode 和 faultDescription 不能为空");
        }

        MaintenanceOrder order = new MaintenanceOrder();
        order.setOrderId("MO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        order.setDeviceCode(deviceCode);
        order.setFaultDescription(faultDescription);
        order.setPriority(priority);
        order.setStatus("pending");
        order.setAssignedTeam(assignedTeam);
        order.setCreatedBy("ai_agent");
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        maintenanceOrderMapper.insert(order);

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getOrderId());
        data.put("assignedTeam", assignedTeam);
        data.put("estimatedTime", "2-4小时");
        return Result.success(data);
    }

    @GetMapping("/history")
    public Result<Map<String, Object>> queryHistory(
            @RequestParam(value = "deviceCode", required = false) String deviceCode) {

        LambdaQueryWrapper<MaintenanceOrder> wrapper = new LambdaQueryWrapper<>();
        if (deviceCode != null && !deviceCode.isEmpty()) {
            wrapper.eq(MaintenanceOrder::getDeviceCode, deviceCode);
        }
        wrapper.orderByDesc(MaintenanceOrder::getCreatedAt).last("LIMIT 20");
        List<MaintenanceOrder> orders = maintenanceOrderMapper.selectList(wrapper);

        long pendingCount = orders.stream().filter(o -> "pending".equals(o.getStatus())).count();

        List<Map<String, Object>> records = orders.stream().map(o -> {
            Map<String, Object> item = new HashMap<>();
            item.put("orderId", o.getOrderId());
            item.put("deviceCode", o.getDeviceCode());
            item.put("faultDescription", o.getFaultDescription());
            item.put("priority", o.getPriority());
            item.put("status", o.getStatus());
            item.put("assignedTeam", o.getAssignedTeam());
            item.put("createdAt", o.getCreatedAt() != null ? DTF.format(o.getCreatedAt()) : null);
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", records.size());
        data.put("pendingCount", pendingCount);
        return Result.success(data);
    }

    @PostMapping("/schedule")
    public Result<Map<String, Object>> scheduleMaintenance(@RequestBody Map<String, String> payload) {
        String deviceCode = payload.getOrDefault("deviceCode", "");
        String maintenanceType = payload.getOrDefault("maintenanceType", "routine");
        String description = payload.getOrDefault("description", "");

        if (deviceCode.isEmpty()) {
            return Result.error(400, "deviceCode 不能为空");
        }

        String scheduledTime = LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        Map<String, Object> data = new HashMap<>();
        data.put("scheduled", true);
        data.put("scheduleId", "SCH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        data.put("plannedDate", scheduledTime);
        data.put("assignedTeam", "默认维修班组");
        data.put("deviceCode", deviceCode);
        data.put("maintenanceType", maintenanceType);
        return Result.success(data);
    }

    @GetMapping("/progress")
    public Result<Map<String, Object>> queryProgress(
            @RequestParam(value = "orderId", required = false) String orderId,
            @RequestParam(value = "deviceCode", required = false) String deviceCode) {

        if ((orderId == null || orderId.isEmpty()) && (deviceCode == null || deviceCode.isEmpty())) {
            return Result.error(400, "orderId 和 deviceCode 至少提供一个");
        }

        MaintenanceOrder order = null;

        if (orderId != null && !orderId.isEmpty()) {
            LambdaQueryWrapper<MaintenanceOrder> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MaintenanceOrder::getOrderId, orderId);
            order = maintenanceOrderMapper.selectOne(wrapper);
        }

        if (order == null && deviceCode != null && !deviceCode.isEmpty()) {
            LambdaQueryWrapper<MaintenanceOrder> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MaintenanceOrder::getDeviceCode, deviceCode)
                    .ne(MaintenanceOrder::getStatus, "completed")
                    .ne(MaintenanceOrder::getStatus, "cancelled")
                    .orderByDesc(MaintenanceOrder::getCreatedAt)
                    .last("LIMIT 1");
            order = maintenanceOrderMapper.selectOne(wrapper);
        }

        if (order == null) {
            Map<String, Object> data = new HashMap<>();
            data.put("orderId", orderId != null ? orderId : "");
            data.put("status", "not_found");
            data.put("progressPercent", 0);
            return Result.success(data);
        }

        int progressPercent = 0;
        String currentStage = "未知";
        switch (order.getStatus()) {
            case "pending":
                progressPercent = 10;
                currentStage = "待处理";
                break;
            case "processing":
                progressPercent = 50;
                currentStage = "处理中";
                break;
            case "completed":
                progressPercent = 100;
                currentStage = "已完成";
                break;
            case "cancelled":
                progressPercent = 0;
                currentStage = "已取消";
                break;
            default:
                progressPercent = 0;
        }

        List<Map<String, Object>> updates = new ArrayList<>();
        Map<String, Object> updateItem = new HashMap<>();
        updateItem.put("stage", currentStage);
        updateItem.put("timestamp", order.getUpdatedAt() != null ? DTF.format(order.getUpdatedAt()) : null);
        updates.add(updateItem);

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getOrderId());
        data.put("deviceCode", order.getDeviceCode());
        data.put("status", order.getStatus());
        data.put("currentStage", currentStage);
        data.put("progressPercent", progressPercent);
        data.put("assignedPerson", order.getAssignedTeam());
        data.put("estimatedCompletion", "completed".equals(order.getStatus()) ? "" : "预计2-4小时");
        data.put("updates", updates);
        data.put("result", order.getResult());
        data.put("createdAt", order.getCreatedAt() != null ? DTF.format(order.getCreatedAt()) : null);
        data.put("completedAt", order.getCompletedAt() != null ? DTF.format(order.getCompletedAt()) : null);
        return Result.success(data);
    }

    @PostMapping("/update-status")
    public Result<Map<String, Object>> updateOrderStatus(@RequestBody Map<String, String> payload) {
        String orderId = payload.getOrDefault("orderId", "");
        String status = payload.getOrDefault("status", "");

        if (orderId.isEmpty() || status.isEmpty()) {
            return Result.error(400, "orderId 和 status 不能为空");
        }

        LambdaQueryWrapper<MaintenanceOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MaintenanceOrder::getOrderId, orderId);
        MaintenanceOrder order = maintenanceOrderMapper.selectOne(wrapper);

        if (order == null) {
            return Result.error(404, "工单不存在: " + orderId);
        }

        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now());
        if ("completed".equals(status) || "cancelled".equals(status)) {
            order.setCompletedAt(LocalDateTime.now());
        }
        maintenanceOrderMapper.updateById(order);

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("status", status);
        return Result.success(data);
    }
}
