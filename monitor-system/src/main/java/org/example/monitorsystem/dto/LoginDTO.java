package org.example.monitorsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginDTO {

    @NotBlank(message = "账号绝对不能为空")
    @Size(min = 4, max = 20, message = "账号长度必须在 4 到 20 个字符之间")
    private String username;

    @NotBlank(message = "密码绝对不能为空")
    @Size(min = 6, max = 32, message = "密码安全性不足，长度必须在 6 到 32 位之间")
    private String password;
}