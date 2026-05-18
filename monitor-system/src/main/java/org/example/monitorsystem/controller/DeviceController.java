package org.example.monitorsystem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.monitorsystem.common.Result;
import org.example.monitorsystem.common.annotation.Log;
import org.example.monitorsystem.dto.DeviceQueryDTO;
import org.example.monitorsystem.service.IDeviceInfoService;
import org.example.monitorsystem.vo.DeviceVO;
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