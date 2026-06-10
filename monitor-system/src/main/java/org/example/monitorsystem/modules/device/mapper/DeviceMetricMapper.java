package org.example.monitorsystem.modules.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.monitorsystem.modules.device.entity.DeviceMetric;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceMetricMapper extends BaseMapper<DeviceMetric> {
}
