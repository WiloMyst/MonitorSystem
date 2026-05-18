package org.example.monitorsystem.service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.monitorsystem.dto.DeviceQueryDTO;
import org.example.monitorsystem.entity.DeviceInfo;
import org.example.monitorsystem.vo.DeviceVO;

public interface IDeviceInfoService extends IService<DeviceInfo> {
    // 增加分页查询方法
    Page<DeviceVO> getDevicePage(DeviceQueryDTO queryDTO);
}