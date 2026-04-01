package com.example.auth.mapper;

import com.example.auth.dto.response.InternalUserDto;
import com.example.auth.model.AuthUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InternalUserMapper {

    @Mapping(source = "id", target = "userId")
    InternalUserDto toDto(AuthUser user);
}
