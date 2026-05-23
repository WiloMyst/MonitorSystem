package org.example.monitorsystem.modules.device.model;
import lombok.Data;

@Data
public class DeviceQueryDTO {
    // 分页参数
    private Integer current = 1; // 默认第一页
    private Integer size = 10;   // 默认每页10条

    // 搜索条件（比如前端可以根据设备编号或状态搜索）
    private String keyword;
    private Integer status;
}