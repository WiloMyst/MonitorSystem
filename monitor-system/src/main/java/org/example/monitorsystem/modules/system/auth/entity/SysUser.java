package org.example.monitorsystem.modules.system.auth.entity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId
    private Long id;
    private String username;
    private String password;
    private String salt; // 该账号的专属密码盐
    private String realName;
    private String roleCode;
    private Integer status;
}