package org.example.monitorsystem.modules.device.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备指标实体
 * 对应数据库表 device_metric，存储设备运行指标（温度/振动/压力/电压）的时序数据。
 */
@Data
@TableName("device_metric")
public class DeviceMetric {
    @TableId
    private Long id;
    private String deviceCode;
    private String metricType;
    private BigDecimal metricValue;
    private LocalDateTime recordedAt;
}
