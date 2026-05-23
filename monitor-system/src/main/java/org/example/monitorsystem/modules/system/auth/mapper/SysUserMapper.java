package org.example.monitorsystem.modules.system.auth.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.monitorsystem.modules.system.auth.entity.SysUser;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}