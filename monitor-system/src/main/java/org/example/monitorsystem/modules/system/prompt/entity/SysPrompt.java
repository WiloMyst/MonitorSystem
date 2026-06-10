package org.example.monitorsystem.modules.system.prompt.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统提示词实体
 * 对应数据库表 sys_prompt，存储 AI Agent 的动态提示词配置。
 */
@Data
@TableName("sys_prompt")
public class SysPrompt {
    @TableId
    private Long id;
    private String promptCode;
    private String content;
    private String description;
    private LocalDateTime updateTime;
}