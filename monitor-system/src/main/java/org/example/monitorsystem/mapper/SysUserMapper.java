package org.example.monitorsystem.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.monitorsystem.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}