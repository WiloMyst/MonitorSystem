package org.example.monitorsystem.modules.device.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.monitorsystem.modules.device.entity.DeviceAlert;
import org.example.monitorsystem.modules.device.mapper.DeviceAlertMapper;
import org.example.monitorsystem.modules.device.service.IDeviceAlertService;
import org.springframework.stereotype.Service;

@Service
public class DeviceAlertServiceImpl extends ServiceImpl<DeviceAlertMapper, DeviceAlert> implements IDeviceAlertService {
}
