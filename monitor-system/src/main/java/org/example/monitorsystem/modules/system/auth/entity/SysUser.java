package org.example.monitorsystem.modules.system.auth.entity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 系统用户实体
 * 对应数据库表 sys_user，密码采用 SHA-256 + 动态盐值存储。
 */
@Data
@TableName("sys_user")
public class SysUser {
    @TableId
    private Long id;
    private String username;
    private String password;
    private String salt;
    private String realName;
    private String roleCode;
    private Integer status;
}