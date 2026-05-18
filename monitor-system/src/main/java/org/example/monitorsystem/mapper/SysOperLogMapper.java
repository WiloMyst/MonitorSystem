package org.example.monitorsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.monitorsystem.entity.SysOperLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysOperLogMapper extends BaseMapper<SysOperLog> {
    // 继承 BaseMapper，自动拥有 insert、update、select 等所有底层方法
}