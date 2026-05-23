package org.example.monitorsystem.modules.system.log.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_oper_log")
public class SysOperLog {
    @TableId
    private Long id;
    private String title;         // 模块标题（如：设备管理）
    private String businessType;  // 业务类型（如：新增、修改、删除）
    private String method;        // 调用的 Java 方法名
    private String requestMethod; // HTTP 请求方式（GET、POST）
    private String operName;      // 操作人员账号
    private String operUrl;       // 请求 URL
    private String operIp;        // 主机地址 (IP)
    private Integer status;       // 操作状态（1正常 0异常）
    private String errorMsg;      // 错误消息
    private Long costTime;        // 执行耗时（毫秒）
    private LocalDateTime operTime; // 操作时间
}