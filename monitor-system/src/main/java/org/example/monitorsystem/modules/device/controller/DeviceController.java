package org.example.monitorsystem.modules.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.monitorsystem.core.web.Result;
import org.example.monitorsystem.modules.system.log.aop.annotation.Log;
import org.example.monitorsystem.modules.device.model.DeviceQueryDTO;
import org.example.monitorsystem.modules.device.service.IDeviceInfoService;
import org.example.monitorsystem.modules.device.model.DeviceVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequestMapping("/api/device")
public class DeviceController {

    @Autowired
    private IDeviceInfoService deviceInfoService;

    @Log(title = "设备实时监控", businessType = "QUERY")
    @PostMapping("/page") // 注意：搜索通常涉及很多参数，企业里规范用 POST 传 JSON (DTO)
    public Result<Page<DeviceVO>> getDevicePage(@Validated @RequestBody DeviceQueryDTO queryDTO) {
        // 将具体的搜索和转换逻辑下推到 Service 层，保持 Controller 干净
        Page<DeviceVO> pageResult = deviceInfoService.getDevicePage(queryDTO);
        return Result.success(pageResult);
    }
}