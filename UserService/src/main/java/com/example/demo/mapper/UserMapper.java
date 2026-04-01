package com.example.demo.mapper;

import com.example.demo.dto.response.UserDto;
import com.example.demo.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto toDto(User user);
}
