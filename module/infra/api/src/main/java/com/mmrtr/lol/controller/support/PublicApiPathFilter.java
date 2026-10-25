package com.mmrtr.lol.controller.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.controller.support.response.ApiResponse;
import com.mmrtr.lol.support.error.ErrorType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * 들어온 포트에 따라 받을 수 있는 경로를 제한한다. 허용되지 않으면 핸들러까지 가지 않고 {@code ApiResponse} 404.
 * <ul>
 *   <li>내부 포트: {@code /internal/**} 만</li>
 *   <li>공개 포트: 소유 prefix + 이행용 허용 prefix. {@code /internal/**} 은 항상 거부</li>
 * </ul>
 * 경로는 컨테이너가 정규화한 servletPath 와 원본 URI 양쪽으로 판정해 둘 다 허용될 때만 통과시킨다
 * ({@code ..}·인코딩으로 판정을 우회하지 못하도록).
 */
public class PublicApiPathFilter extends OncePerRequestFilter {

    private static final UrlPathHelper RAW_PATH_HELPER = new UrlPathHelper();

    static {
        RAW_PATH_HELPER.setUrlDecode(false);
    }

    private final WebPortProperties properties;
    private final ObjectMapper objectMapper;

    public PublicApiPathFilter(WebPortProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean internalPort = properties.internalPortEnabled()
                && request.getLocalPort() == properties.internalPort();

        String normalizedPath = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
        String rawPath = RAW_PATH_HELPER.getPathWithinApplication(request);

        if (isAllowed(normalizedPath, internalPort) && isAllowed(rawPath, internalPort)) {
            chain.doFilter(request, response);
            return;
        }
        writeNotFound(response);
    }

    private boolean isAllowed(String path, boolean internalPort) {
        if (internalPort) {
            return PublicApiPaths.isInternal(path);
        }
        if (PublicApiPaths.isInternal(path)) {
            return false;
        }
        return PublicApiPaths.isOwned(path)
                || properties.transitionalPublicPrefixes().stream()
                        .anyMatch(prefix -> PublicApiPaths.matches(path, prefix));
    }

    private void writeNotFound(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorType.NOT_FOUND_API.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(ErrorType.NOT_FOUND_API));
    }
}
