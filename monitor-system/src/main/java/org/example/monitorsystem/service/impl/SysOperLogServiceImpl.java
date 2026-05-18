package org.example.monitorsystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.monitorsystem.entity.SysOperLog;
import org.example.monitorsystem.mapper.SysOperLogMapper;
import org.example.monitorsystem.service.ISysOperLogService;
import org.springframework.stereotype.Service;

@Service
public class SysOperLogServiceImpl extends ServiceImpl<SysOperLogMapper, SysOperLog> implements ISysOperLogService {
    // 继承 ServiceImpl 并实现接口，这里什么都不用写，MyBatis-Plus 已经帮你把 CRUD 全包了
}