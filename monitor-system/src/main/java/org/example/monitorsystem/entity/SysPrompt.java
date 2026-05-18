package org.example.monitorsystem.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

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