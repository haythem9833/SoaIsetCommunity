package com.example.demo.dto;

import com.example.demo.model.*;
import org.hibernate.Hibernate;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserResponseDTO {

    private Integer id;
    private String fullName;
    private String departement;
    private String email;
    private UserStatus status;
    private String userType;

    public static UserResponseDTO fromEntity(User entity) {

        // un proxy Hibernate n'est pas "instanceof Student/Prof/Admin"
        User user = (User) Hibernate.unproxy(entity);

        String type;

        if (user instanceof Admin) {
            type = "ADMIN";
        } else if (user instanceof Prof) {
            type = "PROF";
        } else if (user instanceof Student) {
            type = "STUDENT";
        } else {
            type = "USER";
        }

        return new UserResponseDTO(
                user.getId(),
                user.getFullName(),
                user.getDepartement(),
                user.getEmail(),
                user.getStatus(),
                type
        );
    }
}