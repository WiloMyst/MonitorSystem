package org.example.monitorsystem.modules.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.monitorsystem.modules.device.entity.DeviceMetric;
import org.example.monitorsystem.modules.device.mapper.DeviceMetricMapper;
import org.example.monitorsystem.modules.device.service.IDeviceMetricService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备指标服务实现
 *
 * 查询指定设备的指标趋势数据，并提供:
 *   - 统计摘要: 当前值、平均值、最大值、最小值
 *   - 趋势判断: rising / falling / stable
 *   - 异常检测: 温度超 80°C、电压超 [200V, 240V]、振动超 0.50
 */
@Service
public class DeviceMetricServiceImpl extends ServiceImpl<DeviceMetricMapper, DeviceMetric> implements IDeviceMetricService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Map<String, Object> queryMetrics(String deviceCode, String metricType, String timeRange) {
        LocalDateTime since = parseTimeRange(timeRange);

        LambdaQueryWrapper<DeviceMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceMetric::getDeviceCode, deviceCode)
                .eq(DeviceMetric::getMetricType, metricType)
                .ge(DeviceMetric::getRecordedAt, since)
                .orderByAsc(DeviceMetric::getRecordedAt)
                .last("LIMIT 500");
        List<DeviceMetric> metrics = list(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("deviceCode", deviceCode);
        result.put("metricType", metricType);
        result.put("timeRange", timeRange);

        if (metrics.isEmpty()) {
            result.put("dataPoints", Collections.emptyList());
            result.put("currentValue", null);
            result.put("averageValue", null);
            result.put("maxValue", null);
            result.put("minValue", null);
            result.put("trend", "no_data");
            result.put("anomalyDetected", false);
            result.put("anomalyDetails", Collections.emptyList());
            return result;
        }

        List<Map<String, Object>> dataPoints = metrics.stream().map(m -> {
            Map<String, Object> point = new HashMap<>();
            point.put("timestamp", m.getRecordedAt() != null ? DTF.format(m.getRecordedAt()) : null);
            point.put("value", m.getMetricValue());
            return point;
        }).collect(Collectors.toList());

        List<BigDecimal> values = metrics.stream()
                .map(DeviceMetric::getMetricValue)
                .collect(Collectors.toList());

        BigDecimal current = values.get(values.size() - 1);
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
        BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(current);
        BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(current);

        String trend = calculateTrend(values);
        List<Map<String, Object>> anomalyDetails = detectAnomalies(deviceCode, metricType, values, current);

        result.put("dataPoints", dataPoints);
        result.put("currentValue", current);
        result.put("averageValue", avg);
        result.put("maxValue", max);
        result.put("minValue", min);
        result.put("trend", trend);
        result.put("anomalyDetected", !anomalyDetails.isEmpty());
        result.put("anomalyDetails", anomalyDetails);
        return result;
    }

    private LocalDateTime parseTimeRange(String timeRange) {
        LocalDateTime now = LocalDateTime.now();
        if (timeRange == null) return now.minusHours(24);
        return switch (timeRange) {
            case "1h" -> now.minusHours(1);
            case "6h" -> now.minusHours(6);
            case "12h" -> now.minusHours(12);
            case "7d" -> now.minusDays(7);
            case "30d" -> now.minusDays(30);
            default -> now.minusHours(24);
        };
    }

    private String calculateTrend(List<BigDecimal> values) {
        if (values.size() < 3) return "stable";
        int n = values.size();
        BigDecimal first = values.get(0);
        BigDecimal last = values.get(n - 1);
        BigDecimal range = max(values).subtract(min(values));
        BigDecimal threshold = max(values).multiply(BigDecimal.valueOf(0.05));

        if (range.compareTo(threshold) <= 0) return "stable";

        BigDecimal diff = last.subtract(first);
        if (diff.compareTo(BigDecimal.ZERO) > 0) return "rising";
        if (diff.compareTo(BigDecimal.ZERO) < 0) return "falling";
        return "stable";
    }

    private BigDecimal max(List<BigDecimal> vals) {
        return vals.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    private BigDecimal min(List<BigDecimal> vals) {
        return vals.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    private List<Map<String, Object>> detectAnomalies(String deviceCode, String metricType, List<BigDecimal> values, BigDecimal current) {
        List<Map<String, Object>> anomalies = new ArrayList<>();

        if ("temperature".equals(metricType) && current.compareTo(BigDecimal.valueOf(80)) > 0) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("type", "high_temperature");
            detail.put("value", current);
            detail.put("threshold", 80);
            detail.put("message", String.format("设备 %s 温度 %.1f°C 超过阈值 80°C", deviceCode, current.doubleValue()));
            anomalies.add(detail);
        }

        if ("voltage".equals(metricType)) {
            if (current.compareTo(BigDecimal.valueOf(240)) > 0 || current.compareTo(BigDecimal.valueOf(200)) < 0) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("type", "voltage_abnormal");
                detail.put("value", current);
                detail.put("message", String.format("设备 %s 电压 %.1fV 超出安全范围 [200V, 240V]", deviceCode, current.doubleValue()));
                anomalies.add(detail);
            }
        }

        if ("vibration".equals(metricType) && current.compareTo(BigDecimal.valueOf(0.5)) > 0) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("type", "high_vibration");
            detail.put("value", current);
            detail.put("threshold", 0.5);
            detail.put("message", String.format("设备 %s 振动值 %.2f 超过阈值 0.50", deviceCode, current.doubleValue()));
            anomalies.add(detail);
        }

        return anomalies;
    }
}
