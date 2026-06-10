package org.example.monitorsystem.modules.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.monitorsystem.core.web.Result;
import org.example.monitorsystem.modules.device.entity.DeviceAlert;
import org.example.monitorsystem.modules.device.entity.DeviceInfo;
import org.example.monitorsystem.modules.device.service.IDeviceAlertService;
import org.example.monitorsystem.modules.device.service.IDeviceInfoService;
import org.example.monitorsystem.modules.device.service.IDeviceMetricService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 内部设备数据控制器
 * 供 AI 微服务查询设备状态、告警历史和指标趋势，路径受 InternalApiInterceptor 保护。
 */
@RestController
@RequestMapping("/api/internal/device")
public class InternalDeviceController {

    @Autowired
    private IDeviceInfoService deviceInfoService;

    @Autowired
    private IDeviceAlertService deviceAlertService;

    @Autowired
    private IDeviceMetricService deviceMetricService;

    @GetMapping("/status")
    public Result<Map<String, Object>> queryDeviceStatus(@RequestParam("code") String deviceCode) {
        if (deviceCode == null || deviceCode.trim().isEmpty()) {
            return Result.error(400, "设备编号不能为空");
        }

        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceInfo::getDeviceCode, deviceCode);
        DeviceInfo device = deviceInfoService.getOne(wrapper);

        if (device == null) {
            return Result.error(404, "设备不存在");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("device_code", device.getDeviceCode());
        data.put("device_type", device.getDeviceType());
        data.put("status", device.getStatus());
        data.put("temperature", device.getTemperature() != null ? device.getTemperature().toString() : null);
        data.put("update_time", device.getUpdateTime() != null ? device.getUpdateTime().toString() : null);

        return Result.success(data);
    }

    @GetMapping("/batch-status")
    public Result<Map<String, Object>> batchQueryStatus(@RequestParam("codes") List<String> deviceCodes) {
        if (deviceCodes == null || deviceCodes.isEmpty()) {
            return Result.error(400, "设备编号列表不能为空");
        }

        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DeviceInfo::getDeviceCode, deviceCodes);
        List<DeviceInfo> devices = deviceInfoService.list(wrapper);

        int normalCount = 0;
        int abnormalCount = 0;
        List<Map<String, Object>> results = new ArrayList<>();
        for (DeviceInfo device : devices) {
            Map<String, Object> item = new HashMap<>();
            item.put("device_code", device.getDeviceCode());
            item.put("device_type", device.getDeviceType());
            item.put("status", device.getStatus());
            item.put("temperature", device.getTemperature() != null ? device.getTemperature().toString() : null);
            item.put("update_time", device.getUpdateTime() != null ? device.getUpdateTime().toString() : null);
            results.add(item);

            if (device.getStatus() != null && device.getStatus() == 1) {
                abnormalCount++;
            } else {
                normalCount++;
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("devices", results);
        data.put("normalCount", normalCount);
        data.put("abnormalCount", abnormalCount);
        return Result.success(data);
    }

    @GetMapping("/alerts")
    public Result<Map<String, Object>> queryDeviceAlerts(
            @RequestParam("deviceCode") String deviceCode,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit) {

        if (deviceCode == null || deviceCode.trim().isEmpty()) {
            return Result.error(400, "设备编号不能为空");
        }
        if (limit != null && (limit < 1 || limit > 100)) {
            return Result.error(400, "limit 范围应在 1~100 之间");
        }

        LambdaQueryWrapper<DeviceAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceAlert::getDeviceCode, deviceCode);
        if (severity != null && !severity.isEmpty()) {
            wrapper.eq(DeviceAlert::getSeverity, severity);
        }
        wrapper.orderByDesc(DeviceAlert::getCreatedAt);
        wrapper.last("LIMIT " + limit);

        List<DeviceAlert> alertList = deviceAlertService.list(wrapper);

        int criticalCount = 0;
        int warningCount = 0;

        List<Map<String, Object>> alerts = new ArrayList<>();
        for (DeviceAlert alert : alertList) {
            Map<String, Object> item = new HashMap<>();
            item.put("alertId", alert.getAlertId());
            item.put("deviceCode", alert.getDeviceCode());
            item.put("type", alert.getAlertType());
            item.put("severity", alert.getSeverity());
            item.put("message", alert.getMessage());
            item.put("acknowledged", alert.getAcknowledged());
            item.put("timestamp", alert.getCreatedAt() != null ? alert.getCreatedAt().toString() : null);
            alerts.add(item);

            if ("critical".equals(alert.getSeverity())) {
                criticalCount++;
            } else if ("warning".equals(alert.getSeverity())) {
                warningCount++;
            }
        }

        LambdaQueryWrapper<DeviceAlert> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(DeviceAlert::getDeviceCode, deviceCode);
        long total = deviceAlertService.count(countWrapper);

        Map<String, Object> data = new HashMap<>();
        data.put("alerts", alerts);
        data.put("total", total);
        data.put("criticalCount", criticalCount);
        data.put("warningCount", warningCount);
        return Result.success(data);
    }

    @GetMapping("/metrics")
    public Result<Map<String, Object>> queryDeviceMetrics(
            @RequestParam("deviceCode") String deviceCode,
            @RequestParam(value = "metricType", defaultValue = "temperature") String metricType,
            @RequestParam(value = "timeRange", defaultValue = "24h") String timeRange) {

        if (deviceCode == null || deviceCode.trim().isEmpty()) {
            return Result.error(400, "设备编号不能为空");
        }

        Map<String, Object> data = deviceMetricService.queryMetrics(deviceCode, metricType, timeRange);
        return Result.success(data);
    }
}
