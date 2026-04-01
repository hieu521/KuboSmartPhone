package com.example.department.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/departments")
public class DepartmentController {

    @GetMapping
    public List<String> getAll() {
        // Demo endpoint (chưa có entity/DB cho Department trong dự án hiện tại)
        return List.of("Sales", "Support", "Engineering");
    }
}

