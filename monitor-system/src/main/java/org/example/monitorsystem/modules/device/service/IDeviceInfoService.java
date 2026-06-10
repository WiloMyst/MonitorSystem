package org.example.monitorsystem.modules.device.service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.monitorsystem.modules.device.model.DeviceQueryDTO;
import org.example.monitorsystem.modules.device.entity.DeviceInfo;
import org.example.monitorsystem.modules.device.model.DeviceVO;

/**
 * 设备信息服务接口
 * 继承 MyBatis-Plus IService，提供设备分页查询能力。
 */
public interface IDeviceInfoService extends IService<DeviceInfo> {
    Page<DeviceVO> getDevicePage(DeviceQueryDTO queryDTO);
}