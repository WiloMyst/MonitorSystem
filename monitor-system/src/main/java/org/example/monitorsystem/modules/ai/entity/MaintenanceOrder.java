package org.example.monitorsystem.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 维修工单实体
 * 对应数据库表 maintenance_order，存储 AI 创建的维修工单及执行状态。
 */
@Data
@TableName("maintenance_order")
public class MaintenanceOrder {
    @TableId
    private Long id;
    private String orderId;
    private String deviceCode;
    private String faultDescription;
    private String priority;
    private String status;
    private String assignedTeam;
    private String result;
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;
}
