package org.example.monitorsystem.modules.system.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.monitorsystem.modules.system.log.entity.SysOperLog;

@Mapper
public interface SysOperLogMapper extends BaseMapper<SysOperLog> {
    // 继承 BaseMapper，自动拥有 insert、update、select 等所有底层方法
}