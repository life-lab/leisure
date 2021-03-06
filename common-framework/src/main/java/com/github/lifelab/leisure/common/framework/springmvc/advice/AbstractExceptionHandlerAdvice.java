package com.github.lifelab.leisure.common.framework.springmvc.advice;

import brave.Tracer;
import com.github.lifelab.leisure.common.exception.ExtensionException;
import com.github.lifelab.leisure.common.framework.exception.EnumExceptionMessageFramework;
import com.github.lifelab.leisure.common.framework.logger.LoggerConst;
import com.github.lifelab.leisure.common.framework.springmvc.advice.enhance.event.ErrorEvent;
import com.github.lifelab.leisure.common.framework.warning.WarningService;
import com.github.lifelab.leisure.common.model.response.ErrorResponse;
import com.github.lifelab.leisure.common.utils.JsonUtils;
import com.github.lifelab.leisure.common.utils.Warning;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 异常处理器
 *
 * @author weichao.li (liweichao0102@gmail.com)
 * @date 2018/7/5
 */

@Slf4j
public abstract class AbstractExceptionHandlerAdvice implements ApplicationEventPublisherAware {

    private ApplicationEventPublisher publisher;

    @Autowired
    private Tracer tracer;

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.publisher = applicationEventPublisher;
    }

    @ExceptionHandler(value = Exception.class)
    public ErrorResponse errorAttributes(Exception exception, HttpServletRequest request, HttpServletResponse response) {

        // 异常错误返回时 添加 trace_id 到 response header 中
        response.setHeader("trace-id", tracer.currentSpan().context().traceIdString());

        //特定异常处理所需要的参数
        Object data;
        Map paramMap = getParam(request);
        String url = request.getRequestURL().toString();
        String uri = request.getRequestURI();

        //拼装返回结果
        ErrorResponse errorResponse = new ErrorResponse(new Date(), uri);

        if (exception instanceof ExtensionException) {
            log.warn(MessageFormat.format("当前程序进入到异常捕获器，出错的 url 为：[ {0} ]，出错的参数为：[ {1} ]", url, JsonUtils.serialize(paramMap)), exception);
            ExtensionException expectException = (ExtensionException) exception;
            errorResponse.setCode(expectException.getCode());
            errorResponse.setMessage(expectException.getMessage());
            errorResponse.setStatus(expectException.getStatus());
            data = expectException.getData();
            if (Objects.nonNull(expectException.getCause())) {
                errorResponse.setException(expectException.getCause().getMessage());
            }
        } else {
            log.error(MessageFormat.format("当前程序进入到异常捕获器，出错的 url 为：[ {0} ]，出错的参数为：[ {1} ]", url, JsonUtils.serialize(paramMap)), exception);
            errorResponse.setCode(EnumExceptionMessageFramework.UNEXPECTED_ERROR.getCode());
            errorResponse.setMessage(EnumExceptionMessageFramework.UNEXPECTED_ERROR.getMessage());
            errorResponse.setException(exception.getMessage());
            errorResponse.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            /**
             * 参数校验异常特殊处理
             */
            if (exception instanceof BindException) {
                errorResponse.setCode(EnumExceptionMessageFramework.PARAM_VALIDATED_UN_PASS.getCode());
                errorResponse.setMessage(EnumExceptionMessageFramework.PARAM_VALIDATED_UN_PASS.getMessage());
                for (FieldError fieldError : ((BindException) exception).getBindingResult().getFieldErrors()) {
                    errorResponse.addError(fieldError.getField(), fieldError.getDefaultMessage());
                }
                errorResponse.setStatus(HttpStatus.BAD_REQUEST.value());
            } else if (exception instanceof MethodArgumentNotValidException) {
                errorResponse.setCode(EnumExceptionMessageFramework.PARAM_VALIDATED_UN_PASS.getCode());
                errorResponse.setMessage(EnumExceptionMessageFramework.PARAM_VALIDATED_UN_PASS.getMessage());
                for (FieldError fieldError : ((MethodArgumentNotValidException) exception).getBindingResult().getFieldErrors()) {
                    errorResponse.addError(fieldError.getField(), fieldError.getDefaultMessage());
                }
                errorResponse.setStatus(HttpStatus.BAD_REQUEST.value());
            } else if (exception instanceof ConstraintViolationException) {
                errorResponse.setCode(EnumExceptionMessageFramework.PARAM_VALIDATED_UN_PASS.getCode());
                errorResponse.setMessage(EnumExceptionMessageFramework.PARAM_VALIDATED_UN_PASS.getMessage());
                for (ConstraintViolation cv : ((ConstraintViolationException) exception).getConstraintViolations()) {
                    errorResponse.addError(cv.getPropertyPath().toString(), cv.getMessage());
                }
                errorResponse.setStatus(HttpStatus.BAD_REQUEST.value());
            } else if (exception instanceof NoHandlerFoundException) {
                errorResponse.setStatus(HttpStatus.NOT_FOUND.value());
            } else if (exception instanceof HttpRequestMethodNotSupportedException) {
                errorResponse.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
            } else if (exception instanceof HttpMediaTypeNotSupportedException) {
                errorResponse.setStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
            }
            data = new Warning(null,
                    "服务发生非预期异常",
                    tracer.currentSpan().context().traceIdString(),
                    uri,
                    request.getMethod(),
                    JsonUtils.serialize(paramMap),
                    null,
                    new Date(),
                    exception.getMessage());
        }
        response.setStatus(errorResponse.getStatus());
        publisher.publishEvent(new ErrorEvent(errorResponse, data));
        return errorResponse;
    }


    @SuppressWarnings("unchecked")
    private Map getParam(HttpServletRequest request) {
        Map<String, Object> params = new HashMap<>(2);
        if (request instanceof ContentCachingRequestWrapper) {
            ContentCachingRequestWrapper requestWrapper = (ContentCachingRequestWrapper) request;
            String requestBody = "";
            try {
                requestBody = new String(requestWrapper.getContentAsByteArray(), requestWrapper.getCharacterEncoding());
                if (StringUtils.isNotBlank(requestBody)) {
                    requestBody = org.springframework.util.StringUtils.trimAllWhitespace(requestBody);
                }
            } catch (IOException ignored) {
            }
            params.put(LoggerConst.REQUEST_KEY_FORM_PARAM, JsonUtils.serialize(request.getParameterMap()));
            params.put(LoggerConst.REQUEST_KEY_BODY_PARAM, requestBody);
        }
        return params;
    }

}