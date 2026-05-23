package org.example.monitorsystem.modules.system.auth.service;

import org.example.monitorsystem.modules.system.auth.model.LoginDTO;

public interface ISysUserService {
    String doLogin(LoginDTO loginDTO);
}
