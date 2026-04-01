package com.example.common.pagination;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public class PaginationMapper {

    private PaginationMapper() {
    }

    public static <T, R> PageBasedResponse<R> toResponse(Page<T> page, Function<T, R> mapper) {
        List<R> content = page.getContent().stream().map(mapper).toList();
        return new PageBasedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}

