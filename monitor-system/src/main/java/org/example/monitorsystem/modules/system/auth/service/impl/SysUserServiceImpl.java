package org.example.monitorsystem.modules.system.auth.service.impl;

import cn.dev33.satoken.secure.SaSecureUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.monitorsystem.core.exception.BusinessException;
import org.example.monitorsystem.core.exception.ErrorCodeEnum;
import org.example.monitorsystem.modules.system.auth.model.LoginDTO;
import org.example.monitorsystem.modules.system.auth.entity.SysUser;
import org.example.monitorsystem.modules.system.auth.mapper.SysUserMapper;
import org.example.monitorsystem.modules.system.auth.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 系统用户服务实现
 * 登录流程: 查库 → 账号状态校验 → SHA-256+盐值密码比对 → Sa-Token 颁发令牌
 */
@Service
public class SysUserServiceImpl implements ISysUserService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Override
    public String doLogin(LoginDTO loginDTO) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, loginDTO.getUsername());
        SysUser user = sysUserMapper.selectOne(wrapper);

        if (user == null || user.getStatus() == 0) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }

        String inputPassword = SaSecureUtil.sha256(loginDTO.getPassword() + user.getSalt());

        if (!inputPassword.equals(user.getPassword())) {
            throw new BusinessException(ErrorCodeEnum.PASSWORD_ERROR);
        }

        StpUtil.login(user.getId());

        return StpUtil.getTokenValue();
    }
}