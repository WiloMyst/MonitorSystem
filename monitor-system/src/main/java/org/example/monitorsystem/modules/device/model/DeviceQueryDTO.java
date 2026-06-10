package org.example.monitorsystem.modules.device.model;
import lombok.Data;

/**
 * 设备分页查询参数
 */
@Data
public class DeviceQueryDTO {
    private Integer current = 1;
    private Integer size = 10;
    private String keyword;
    private Integer status;
}