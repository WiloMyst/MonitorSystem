package org.example.monitorsystem.modules.device.service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.monitorsystem.modules.device.model.DeviceQueryDTO;
import org.example.monitorsystem.modules.device.entity.DeviceInfo;
import org.example.monitorsystem.modules.device.model.DeviceVO;

public interface IDeviceInfoService extends IService<DeviceInfo> {
    // 增加分页查询方法
    Page<DeviceVO> getDevicePage(DeviceQueryDTO queryDTO);
}