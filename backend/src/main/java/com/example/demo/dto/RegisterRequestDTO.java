package com.example.demo.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequestDTO {

    private String fullName;
    private String departement;
    private String email;
    private String password;

    // STUDENT ou PROF
    private String userType;
}