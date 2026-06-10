package org.example.monitorsystem.modules.device.model;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 设备信息视图对象
 * 仅暴露前端展示所需字段，屏蔽内部敏感信息。
 */
@Data
public class DeviceVO {
    private String deviceCode;
    private String deviceType;
    private Integer status;
    private BigDecimal temperature;
    private String lastUpdateTime;
}