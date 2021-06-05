package org.example.common.aspect;

import com.google.gson.Gson;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.ThreadContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.common.annotation.IgnoreAccessLog;
import org.example.common.bean.ResultBean;
import org.example.common.exception.CheckException;
import org.example.common.exception.ParamsException;
import org.example.common.exception.ProjectException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author liangzhirong
 * @date 2021/6/5
 */
@Aspect
@Component
@Order(1)
@Log4j2
public class AccessLogAspect {

    private static final String TRACE_ID = "TRACE_ID";

    private static final String USER_ID = "USER_ID";

    /**
     * 所有 返回值为 ResponseEntity 模块为 controller 的 命名为 *Controller 的方法
     */
    @Pointcut("execution(public org.springframework.http.ResponseEntity org.example.controller.*Controller.*(..))")
    public void controllerPointCut() {
        // 空注释，避免sonar警告
    }

    @Around("controllerPointCut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        IgnoreAccessLog ignoreAccessLog = method.getAnnotation(IgnoreAccessLog.class);
        if (ignoreAccessLog != null
                && ignoreAccessLog.ignore()) {
            return joinPoint.proceed();
        }
        String traceId = UUID.randomUUID().toString();
        HttpServletResponse response =
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getResponse();
        response.setHeader(AccessLogAspect.TRACE_ID, traceId);
        ThreadContext.put(AccessLogAspect.TRACE_ID, traceId);
//        if (ShiroUtils.getUserEntity() != null) {
//            MDC.put(AccessLogAspect.USER_ID, String.valueOf(ShiroUtils.getUserId()));
//        }
        long start = System.currentTimeMillis();
        // 获取方法参数
        List<Object> httpReqArgs = new ArrayList<>();
        Object[] args = joinPoint.getArgs();
        for (Object object : args) {
            if (!(object instanceof HttpServletRequest)
                    && !(object instanceof HttpServletResponse)
                    && !(object instanceof MultipartFile)) {
                httpReqArgs.add(object);
            }
        }
        String url = request.getRequestURI();
        String params = new Gson().toJson(httpReqArgs);
        log.info("==> [{}] [{}] REQ_DATA:{}", String.format("%5s", request.getMethod()), url, params);
        ResponseEntity<?> result = null;
        try {
            // 调用 Controller 层的方法
            result = (ResponseEntity<?>) joinPoint.proceed();
        } catch (Exception e) {
            result = handlerException(request.getMethod(), url, params, e);
        } finally {
            // 获取应答报文及接口处理耗时
            String respData = result == null ? null : new Gson().toJson(result);
            String littleTail;
            if (ignoreAccessLog != null
                    && !ignoreAccessLog.trim()) {
                // 不修剪长度
                littleTail = "";
            } else {
                // 修剪响应长度
                int len;
                if (respData != null) {
                    len = Math.min(respData.length(), 100);
                    respData = respData.substring(0, len);
                }
                littleTail = "...";
            }
            long useTime = (System.currentTimeMillis() - start);
            log.info("<== [{}] [{}] [{}ms] BODY: {}{}{}",
                    String.format("%5s", request.getMethod()), url, String.format("%6s", useTime), respData, littleTail, System.lineSeparator());
            ThreadContext.remove(AccessLogAspect.TRACE_ID);
            ThreadContext.remove(AccessLogAspect.USER_ID);
        }
        return result;
    }

    /**
     * 封装异常信息，注意区分已知异常（自己抛出的）和未知异常
     */
    private ResponseEntity<?> handlerException(String method, String url, String params, Throwable e) {
        // 已知异常
        if (e instanceof ParamsException) {
            // 参数异常
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ResultBean.fail(e.getMessage()));
        } else if (e instanceof CheckException) {
            // 已知异常
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ResultBean.fail(e.getMessage()));
        } else if (e instanceof ProjectException) {
            // 逻辑不合理触发的异常, 需要通知管理员
            //TODO liangzhirong 未知的异常，应该格外注意，可以发送邮件通知等
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ResultBean.fail("服务器繁忙，请稍后再试！"));
        } else {
            log.error("!!!请求报错 [{}] [{}], REQ_DATA:{} ERROR:", method, url, params, e);
            //TODO liangzhirong 未知的异常，应该格外注意，可以发送邮件通知等
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ResultBean.fail("服务器繁忙，请稍后再试！"));
        }
    }

}
