package org.example.monitorsystem.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.monitorsystem.modules.ai.entity.MaintenanceOrder;

@Mapper
public interface MaintenanceOrderMapper extends BaseMapper<MaintenanceOrder> {
}
