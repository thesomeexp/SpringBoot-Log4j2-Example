package org.example.controller;

import lombok.extern.log4j.Log4j2;
import org.example.common.exception.CheckException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @author liangzhirong
 * @date 2021/6/5
 */
@Controller
@Log4j2
public class HelloController {

    @GetMapping("/hello")
    public ResponseEntity<?> hello() {
        log.debug("debug-test: {}",
                () -> {
                    log.debug("延迟计算开始");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interruptedException) {
                        log.error("休眠异常", interruptedException);
                        throw new CheckException("休眠异常");
                    } finally {
                        log.debug("延迟计算结束");
                    }
                    return "延迟计算测试完成";
                });
        log.info("info-test");
        return ResponseEntity.ok("hello");
    }

    @GetMapping("/exception")
    public ResponseEntity<?> exception(ExceptionQuery exceptionQuery) {
        if (exceptionQuery.getId() == 0) {
            int i = 1 / 0;
        }
        return ResponseEntity.ok("exception");
    }

}
