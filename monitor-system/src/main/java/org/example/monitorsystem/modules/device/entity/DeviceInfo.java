package org.example.monitorsystem.modules.device.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备信息实体
 * 对应数据库表 device_info，存储设备基础信息和实时状态。
 */
@Data
@TableName("device_info")
public class DeviceInfo {
    @TableId
    private Long id;
    private String deviceCode;
    private String deviceType;
    private Integer status;
    private BigDecimal temperature;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}