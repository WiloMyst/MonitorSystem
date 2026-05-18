package org.example.monitorsystem.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data // 自动生成 Getter/Setter
@TableName("device_info") // 对齐数据库表名
public class DeviceInfo {
    @TableId
    private Long id;
    private String deviceCode;
    private String deviceType;
    private Integer status;
    private BigDecimal temperature;

    // 标记为插入和更新时自动填充
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}