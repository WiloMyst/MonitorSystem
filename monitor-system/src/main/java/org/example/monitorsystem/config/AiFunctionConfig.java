package org.example.monitorsystem.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.example.monitorsystem.entity.DeviceInfo;
import org.example.monitorsystem.service.IDeviceInfoService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class AiFunctionConfig {

    // 1. 定义大模型传给 Java 的参数格式（这里必须用 Java 的 Record 或 Class）
    // 用 Jackson 注解给大模型下达“提取指令”
    public record DeviceStatusRequest(
            @JsonProperty(required = true, value = "deviceCode")
            @JsonPropertyDescription("需要查询的设备唯一编号。请务必从用户的提问中精准提取此编号，例如：'ATM-SN-001'、'5G-BS-SB-045'等。如果用户提问中包含此类格式的字符串，即为设备编号。")
            String deviceCode
    ) {}

    // 2. 定义 Java 返回给大模型的数据格式
    public record DeviceStatusResponse(String deviceCode, String status, String temperature, String message) {}

    /**
     * 这个 @Description 注解极其重要！
     * Spring AI 会把这段中文发送给智谱大模型，大模型就是靠这句话来判断“什么时候该调用这个函数”。
     */
    @Bean
    @Description("这是一个查询设备状态的工具。当你需要回答某个具体设备的实时状态、运行温度等动态信息时，必须调用此工具。")
    public Function<DeviceStatusRequest, DeviceStatusResponse> queryDeviceStatus(IDeviceInfoService deviceInfoService) {

        return request -> {
            System.out.println("====== 🤖 触发 Function Calling！大模型要求查询设备: " + request.deviceCode() + " ======");

            // 1. 查询 MySQL 数据库
            LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DeviceInfo::getDeviceCode, request.deviceCode());
            DeviceInfo device = deviceInfoService.getOne(wrapper);

            // 2. 组装结果返回给大模型
            if (device == null) {
                return new DeviceStatusResponse(request.deviceCode(), "未知", "未知", "数据库中未找到该设备");
            }

            String statusStr = device.getStatus() == 1 ? "异常/高温报警" : "正常";
            String tempStr = device.getTemperature() != null ? device.getTemperature().toString() + "℃" : "暂无数据";

            System.out.println("====== 🤖 查询完毕，将数据喂给大模型重新组织语言 ======");
            return new DeviceStatusResponse(request.deviceCode(), statusStr, tempStr, "查询成功");
        };
    }
}