package com.readshelf.utils;

public interface EntityMapper<RequestDTO, ResponseDTO, Entity > {
    Entity toEntity(RequestDTO requestDTO);
    ResponseDTO toResponseDTO(Entity e);


}
