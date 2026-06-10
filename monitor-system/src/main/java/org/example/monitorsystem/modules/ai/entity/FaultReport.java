package org.example.monitorsystem.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 故障报告实体
 * 对应数据库表 fault_report，存储 AI 生成的结构化故障分析报告。
 */
@Data
@TableName("fault_report")
public class FaultReport {
    @TableId
    private Long id;
    private String reportId;
    private String deviceCode;
    private String faultDescription;
    private String severity;
    private String summary;
    private String impactAssessment;
    private String recommendedActions;
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
