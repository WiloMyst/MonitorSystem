package org.example.monitorsystem.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.example.monitorsystem.common.component.AgentObservation;
import org.example.monitorsystem.common.component.ToolUseAdapter;
import org.example.monitorsystem.entity.DeviceInfo;
import org.example.monitorsystem.service.IDeviceInfoService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class AiFunctionConfig {

    // 1. 定义大模型传给 Java 的参数格式
    // 用 Jackson 注解给大模型下达“提取指令”
    public record DeviceStatusRequest(
            @NotBlank(message = "设备编号绝对不能为空")
            @Pattern(regexp = "^[A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+$", message = "设备编号格式必须符合企业规范(如: ATM-SN-001)")
            @JsonProperty(required = true, value = "deviceCode")
            @JsonPropertyDescription("需要查询的设备唯一编号。请务必从用户的提问中精准提取此编号。")
            String deviceCode
    ) {}

    // 2. 定义 Java 返回给大模型的数据格式
    public record DeviceStatusResponse(String deviceCode, String status, String temperature) {}

    /**
     * 这个 @Description 注解极其重要！
     * Spring AI 会把这段中文发送给智谱大模型，大模型就是靠这句话来判断“什么时候该调用这个函数”。
     */
    @Bean
    @Description("这是一个查询设备状态的工具。当你需要回答某个具体设备的实时状态、运行温度等动态信息时，必须调用此工具。")
    public Function<DeviceStatusRequest, AgentObservation> queryDeviceStatus(
            IDeviceInfoService deviceInfoService,
            ToolUseAdapter toolUseAdapter) { // 注入通用适配层

        // 定义真正的业务逻辑
        Function<DeviceStatusRequest, DeviceStatusResponse> businessLogic = request -> {
            // 1. 查询 MySQL 数据库
            LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DeviceInfo::getDeviceCode, request.deviceCode());
            DeviceInfo device = deviceInfoService.getOne(wrapper);

            // 2. 组装结果返回给大模型
            if (device == null) {
                // 直接抛出异常，外层的适配器会自动捕获并告诉大模型
                throw new RuntimeException("数据库中未查找到该设备编号");
            }

            String statusStr = device.getStatus() == 1 ? "异常/高温报警" : "正常";
            String tempStr = device.getTemperature() != null ? device.getTemperature().toString() + "℃" : "暂无数据";

            return new DeviceStatusResponse(request.deviceCode(), statusStr, tempStr);
        };

        // 用通用适配层将纯净的业务逻辑包起来
        return toolUseAdapter.wrap("设备状态查询工具", businessLogic);
    }
}