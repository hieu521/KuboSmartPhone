package com.example.common.pagination;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PageBasedRequest {

    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(100)
    private int size = 20;

    private String sortBy = "id";

    private Sort.Direction sortDir = Sort.Direction.ASC;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public Sort.Direction getSortDir() {
        return sortDir;
    }

    public void setSortDir(Sort.Direction sortDir) {
        this.sortDir = sortDir;
    }

    /**
     * Page-based input, Spring Data JPA will translate to DB offset/limit under the hood.
     */
    public Pageable toPageable() {
        Sort sort = Sort.by(sortDir, sortBy);
        return PageRequest.of(page, size, sort);
    }
}

