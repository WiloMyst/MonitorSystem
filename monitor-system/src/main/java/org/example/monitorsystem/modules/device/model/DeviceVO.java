package org.example.monitorsystem.modules.device.model;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class DeviceVO {
    // VO 里只放前端页面真正需要的字段，屏蔽掉内部的隐秘信息（比如设备内网IP、逻辑删除标记等，假设表里有的话）
    private String deviceCode;
    private String deviceType;
    private Integer status;
    private BigDecimal temperature;
    // 注意：如果是给前端展示的时间，企业里通常会转成 String 格式，这里为了简便先保持原样或转为 String
    private String lastUpdateTime;
}