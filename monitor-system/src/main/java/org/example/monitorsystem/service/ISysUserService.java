package org.example.monitorsystem.service;

import org.example.monitorsystem.dto.LoginDTO;

public interface ISysUserService {
    String doLogin(LoginDTO loginDTO);
}
