package com.example.auth.app.admin.presentation.v1;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 페이지 응답 공통 형상이다(도메인 {@code Page} 내부 표현을 API 계약으로 노출하지 않는다).
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    public PageResponse {
        content = List.copyOf(content);
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
