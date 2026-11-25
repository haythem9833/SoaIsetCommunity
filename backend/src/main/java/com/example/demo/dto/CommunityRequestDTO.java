package com.example.demo.dto;

// type : PUBLIC ou PRIVATE. key et nbMax ne servent que pour une communauté privée.
public record CommunityRequestDTO(String name, String type, String key, Integer nbMax) {
}
