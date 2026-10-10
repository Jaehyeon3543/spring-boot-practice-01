package com.example.ksl.wsd2025.config;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class LoggingInterceptor implements HandlerInterceptor {

    // API 키 (POST / PUT / DELETE 요청 시 X-API-KEY 헤더에 이 값이 있어야 함)
    private static final String API_KEY = "wsd2025";

    // 점검 모드 스위치 (기본값 false)
    // 켜는 법: ./gradlew bootRun --args='--app.maintenance=true'
    @Value("${app.maintenance:false}")
    private boolean maintenance;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws IOException {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();

        System.out.println("[LoggingInterceptor] 요청 시작: " + method + " " + uri +
                (query != null ? "?" + query : ""));


        //---------------------------------------------------------------------------------------------
        // 아래 두 기능은 v4 API 에만 적용
        if (!uri.startsWith("/api/v4")) {
            return true;
        }

        // 1) 점검 모드: 켜져 있으면 모든 v4 요청을 503 으로 차단
        if (maintenance) {
            return reject(response, 503, "SERVICE_UNDER_MAINTENANCE", "서버 점검 중입니다. 잠시 후 다시 시도해주세요.", null);
        }

        // 2) API 키 인증: 데이터를 바꾸는 요청(POST/PUT/DELETE)은 API 키가 있어야 통과
        boolean isWriteRequest = method.equals("POST") || method.equals("PUT") || method.equals("DELETE");
        String apiKey = request.getHeader("X-API-KEY");
        if (isWriteRequest && !API_KEY.equals(apiKey)) {
            String detail = (apiKey == null) ? "X-API-KEY 헤더가 없습니다." : "잘못된 X-API-KEY 값입니다.";
            return reject(response, 401, "INVALID_API_KEY", "유효한 API 키가 필요합니다.", detail);
        }

        return true; // false로 하면 컨트롤러로 안 넘어감
    }
        //---------------------------------------------------------------------------------------------

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        System.out.println("[LoggingInterceptor] 요청 완료, status=" + response.getStatus());
    }

    // 컨트롤러로 보내지 않고 여기서 바로 응답
    // 컨트롤러와 같은 포맷: { "success": false, "error": { "code": "...", "message": "...", "detail": "..." } }
    private boolean reject(HttpServletResponse response, int status, String code, String message, String detail)
            throws IOException {
        System.out.println("[LoggingInterceptor] 요청 거절: status=" + status + ", code=" + code);

        String json = "{\"success\":false,\"error\":{"
                + "\"code\":\"" + code + "\","
                + "\"message\":\"" + message + "\""
                + (detail != null ? ",\"detail\":\"" + detail + "\"" : "")
                + "}}";

        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json);
        return false;
    }
}
