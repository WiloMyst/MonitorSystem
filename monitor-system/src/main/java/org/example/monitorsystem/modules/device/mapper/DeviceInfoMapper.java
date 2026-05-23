package org.example.monitorsystem.modules.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.monitorsystem.modules.device.entity.DeviceInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper // 告诉 Spring 这是一个数据库访问组件
public interface DeviceInfoMapper extends BaseMapper<DeviceInfo> {
    // 继承了 BaseMapper，你就自动拥有了增删改查的所有方法，一行代码都不用写！
}