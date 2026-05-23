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
 * 系统用户服务实现类
 * 企业级特性：防爆破逻辑、SHA-256加盐存储、统一异常抛出
 */
@Service
public class SysUserServiceImpl implements ISysUserService {

    @Autowired
    private SysUserMapper sysUserMapper;

    /**
     * 执行登录逻辑
     */
    @Override
    public String doLogin(LoginDTO loginDTO) {
        // 1. 根据用户名去数据库查询用户信息
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, loginDTO.getUsername());
        SysUser user = sysUserMapper.selectOne(wrapper);

        // 2. 账号存在性及状态校验
        // 安全策略：即便账号不存在，也不要在报错中明确提示“账号不存在”，防止黑客嗅探账号名
        if (user == null || user.getStatus() == 0) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }

        // 3. 密码比对（SHA-256 + 动态盐）
        // 逻辑：将前端传来的明文密码 + 数据库里该用户专属的 salt，进行 SHA-256 杂凑计算
        String inputPassword = SaSecureUtil.sha256(loginDTO.getPassword() + user.getSalt());

        if (!inputPassword.equals(user.getPassword())) {
            // 抛出密码错误异常，GlobalExceptionHandler 会将其捕获并转为 Result.error
            throw new BusinessException(ErrorCodeEnum.PASSWORD_ERROR);
        }

        // 4. 登录鉴权：告诉 Sa-Token 框架，当前用户已经通过验证
        StpUtil.login(user.getId());

        // 5. 返回 Token 给前端存储
        return StpUtil.getTokenValue();
    }
}