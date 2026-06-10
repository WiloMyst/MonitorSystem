package org.example.monitorsystem.modules.system.log.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 操作日志实体
 * 对应数据库表 sys_oper_log，由 LogAspect 异步写入，记录用户操作审计信息。
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {
    @TableId
    private Long id;
    private String title;
    private String businessType;
    private String method;
    private String requestMethod;
    private String operName;
    private String operUrl;
    private String operIp;
    private Integer status;
    private String errorMsg;
    private Long costTime;
    private LocalDateTime operTime;
}