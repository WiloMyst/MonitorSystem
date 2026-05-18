package org.example.monitorsystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.monitorsystem.entity.SysOperLog;

public interface ISysOperLogService extends IService<SysOperLog> {
    // 继承 IService，自动拥有 save、saveBatch 等业务层方法
}