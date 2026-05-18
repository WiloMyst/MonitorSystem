package org.example.monitorsystem.common.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD}) // 这个注解只能贴在方法上
@Retention(RetentionPolicy.RUNTIME) // 在运行时生效
@Documented
public @interface Log {

    // 模块名称
    String title() default "";

    // 操作类型（比如：INSERT, UPDATE, DELETE, EXPORT）
    String businessType() default "OTHER";
}