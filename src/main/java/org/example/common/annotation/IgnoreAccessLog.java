package org.example.common.annotation;

import java.lang.annotation.*;

/**
 * @author liangzhirong
 * @date 2021/6/5
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IgnoreAccessLog {
    /**
     * 是否忽略记录日志 (true 为不使用切面记录和处理异常)
     */
    boolean ignore() default true;

    /**
     * 忽略修剪长度 (true 为使用切割)
     */
    boolean trim() default true;
}
