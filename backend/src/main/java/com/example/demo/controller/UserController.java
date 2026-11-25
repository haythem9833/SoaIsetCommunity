package com.example.demo.controller;

import com.example.demo.dto.UserResponseDTO;
import com.example.demo.model.User;
import com.example.demo.service.UserService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

//indique que cette classe expose des services REST.
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Récupérer tous les utilisateurs
    @GetMapping
    //traite une requête HTTP GET.
    public List<UserResponseDTO> getAllUsers() {

        return userService.getAllUsers()
                .stream()
                .map(UserResponseDTO::fromEntity)
                .toList();
    }

    // Récupérer un utilisateur par son ID
    @GetMapping("/{id}")
    public UserResponseDTO getUserById(@PathVariable Integer id) {

        User user = userService.getUserById(id);

        return UserResponseDTO.fromEntity(user);
    }
}