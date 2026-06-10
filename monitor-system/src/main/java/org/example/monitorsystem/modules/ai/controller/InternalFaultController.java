package org.example.monitorsystem.modules.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.monitorsystem.core.web.Result;
import org.example.monitorsystem.modules.ai.entity.FaultReport;
import org.example.monitorsystem.modules.ai.mapper.FaultReportMapper;
import org.example.monitorsystem.modules.device.entity.DeviceInfo;
import org.example.monitorsystem.modules.device.service.IDeviceInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 内部故障分析控制器
 * 供 AI 微服务进行故障模式分析、相似故障查询和故障报告生成。
 */
@RestController
@RequestMapping("/api/internal/fault")
public class InternalFaultController {

    @Autowired
    private FaultReportMapper faultReportMapper;

    @Autowired
    private IDeviceInfoService deviceInfoService;

    @PostMapping("/analyze")
    public Result<Map<String, Object>> analyzeFault(@RequestBody Map<String, String> payload) {
        String faultDescription = payload.getOrDefault("faultDescription", "");
        String deviceCode = payload.getOrDefault("deviceCode", "");

        if (faultDescription.isEmpty()) {
            return Result.error(400, "faultDescription 不能为空");
        }

        List<Map<String, String>> patterns = new ArrayList<>();
        String descLower = faultDescription.toLowerCase();

        if (descLower.contains("温度") || descLower.contains("过热") || descLower.contains("高温")) {
            patterns.add(Map.of("pattern", "温度异常", "confidence", "high"));
        }
        if (descLower.contains("离线") || descLower.contains("断连") || descLower.contains("无法连接")) {
            patterns.add(Map.of("pattern", "通信故障", "confidence", "high"));
        }
        if (descLower.contains("振动") || descLower.contains("异响") || descLower.contains("噪音")) {
            patterns.add(Map.of("pattern", "机械故障", "confidence", "medium"));
        }
        if (descLower.contains("电源") || descLower.contains("断电") || descLower.contains("ups")) {
            patterns.add(Map.of("pattern", "供电异常", "confidence", "high"));
        }
        if (patterns.isEmpty()) {
            patterns.add(Map.of("pattern", "未知故障模式", "confidence", "low"));
        }

        String severity = "medium";
        if (!deviceCode.isEmpty()) {
            LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DeviceInfo::getDeviceCode, deviceCode);
            DeviceInfo device = deviceInfoService.getOne(wrapper);
            if (device != null && device.getStatus() == 1) {
                severity = "high";
            }
        }

        String rootCause = patterns.stream()
                .map(p -> p.get("pattern"))
                .collect(Collectors.joining("、"));

        Map<String, Object> data = new HashMap<>();
        data.put("patterns", patterns);
        data.put("severity", severity);
        data.put("rootCause", rootCause + "，建议进一步排查确认");
        return Result.success(data);
    }

    @GetMapping("/similar")
    public Result<Map<String, Object>> querySimilarFaults(@RequestParam("description") String description) {
        if (description == null || description.trim().isEmpty()) {
            return Result.error(400, "故障描述不能为空");
        }

        LambdaQueryWrapper<FaultReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(FaultReport::getFaultDescription, description)
                .orderByDesc(FaultReport::getCreatedAt)
                .last("LIMIT 5");
        List<FaultReport> reports = faultReportMapper.selectList(wrapper);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<Map<String, Object>> records = reports.stream().map(r -> {
            Map<String, Object> item = new HashMap<>();
            item.put("reportId", r.getReportId());
            item.put("deviceCode", r.getDeviceCode());
            item.put("faultDescription", r.getFaultDescription());
            item.put("severity", r.getSeverity());
            item.put("createdAt", r.getCreatedAt() != null ? dtf.format(r.getCreatedAt()) : null);
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", records.size());
        return Result.success(data);
    }

    @PostMapping("/report")
    public Result<Map<String, Object>> reportFault(@RequestBody Map<String, String> payload) {
        String deviceCode = payload.getOrDefault("deviceCode", "");
        String faultDescription = payload.getOrDefault("faultDescription", "");
        String severity = payload.getOrDefault("severity", "medium");

        if (deviceCode.isEmpty() || faultDescription.isEmpty()) {
            return Result.error(400, "deviceCode 和 faultDescription 不能为空");
        }

        FaultReport report = new FaultReport();
        report.setReportId("FR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        report.setDeviceCode(deviceCode);
        report.setFaultDescription(faultDescription);
        report.setSeverity(severity);
        report.setCreatedBy("ai_agent");
        report.setCreatedAt(LocalDateTime.now());

        String impactAssessment;
        switch (severity) {
            case "critical":
                impactAssessment = "严重影响：设备需立即停机检修，可能影响产线运行";
                break;
            case "high":
                impactAssessment = "较大影响：设备性能严重下降，需尽快安排维修";
                break;
            case "medium":
                impactAssessment = "一般影响：设备存在异常，建议在24小时内处理";
                break;
            default:
                impactAssessment = "轻微影响：设备存在轻微异常，可纳入定期维护计划";
        }

        String summary = String.format("设备 %s 发生%s级别故障：%s", deviceCode, severity, faultDescription);

        List<String> recommendedActions = new ArrayList<>();
        recommendedActions.add("1. 确认设备当前安全状态，必要时执行紧急停机");
        recommendedActions.add("2. 根据故障描述检索知识库排障方案");
        recommendedActions.add("3. 如无法自主解决，创建维修工单升级处理");

        if ("critical".equals(severity) || "high".equals(severity)) {
            recommendedActions.add("4. 通知相关责任人和管理层");
        }

        report.setSummary(summary);
        report.setImpactAssessment(impactAssessment);
        report.setRecommendedActions(String.join(";;", recommendedActions));
        faultReportMapper.insert(report);

        List<Map<String, String>> timeline = new ArrayList<>();
        Map<String, String> t1 = new HashMap<>();
        t1.put("event", "故障报告生成");
        t1.put("status", "completed");
        timeline.add(t1);
        Map<String, String> t2 = new HashMap<>();
        t2.put("event", "等待人工确认");
        t2.put("status", "pending");
        timeline.add(t2);

        Map<String, Object> data = new HashMap<>();
        data.put("reportId", report.getReportId());
        data.put("status", "submitted");
        data.put("deviceCode", deviceCode);
        data.put("severity", severity);
        data.put("summary", summary);
        data.put("impactAssessment", impactAssessment);
        data.put("recommendedActions", recommendedActions);
        data.put("timeline", timeline);
        return Result.success(data);
    }
}
