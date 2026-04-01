package com.example.demo.controller;

import com.example.demo.dto.request.UserCreateRequest;
import com.example.demo.dto.response.UserDto;
import com.example.demo.mapper.UserMapper;
import com.example.demo.model.User;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @GetMapping
    public List<UserDto> getAll() {
        return userService.findAll().stream()
                .map(userMapper::toDto)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getById(@PathVariable Long id) {
        return userService.findById(id)
                .map(u -> ResponseEntity.ok(userMapper.toDto(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UserDto> create(@RequestBody UserCreateRequest request) {
        Boolean active = request.getActive() != null ? request.getActive() : Boolean.TRUE;
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .active(active)
                .build();

        User saved = userService.save(user);
        return ResponseEntity.ok(userMapper.toDto(saved));
    }
}

