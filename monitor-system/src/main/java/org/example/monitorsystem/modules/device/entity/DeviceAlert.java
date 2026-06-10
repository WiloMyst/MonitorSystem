package org.example.monitorsystem.modules.device.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 设备告警实体
 * 对应数据库表 device_alert，存储设备触发的告警记录。
 */
@Data
@TableName("device_alert")
public class DeviceAlert {
    @TableId
    private Long id;
    private String alertId;
    private String deviceCode;
    private String alertType;
    private String severity;
    private String message;
    private Integer acknowledged;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
