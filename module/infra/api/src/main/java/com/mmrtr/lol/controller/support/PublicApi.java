package com.mmrtr.lol.controller.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ingress 로 공개되는 컨트롤러 표시. 붙은 컨트롤러는
 * <ul>
 *   <li>{@link PublicApiPaths#OWNED_PREFIXES} 아래에만 매핑해야 하고 (PublicApiMappingTest 가 강제)</li>
 *   <li>예외가 {@link PublicApiExceptionAdvice} 로 lol-server 와 같은 {@code ApiResponse} 에러로 응답된다.</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicApi {
}
