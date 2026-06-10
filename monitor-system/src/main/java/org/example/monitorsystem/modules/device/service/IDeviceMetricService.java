package org.example.monitorsystem.modules.device.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.monitorsystem.modules.device.entity.DeviceMetric;

import java.util.List;
import java.util.Map;

/**
 * 设备指标服务接口
 * 提供按设备、指标类型和时间范围查询指标趋势及异常检测。
 */
public interface IDeviceMetricService extends IService<DeviceMetric> {
    Map<String, Object> queryMetrics(String deviceCode, String metricType, String timeRange);
}
