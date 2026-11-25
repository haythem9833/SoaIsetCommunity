package com.example.demo.controller;

import com.example.demo.dto.LoginRequestDTO;
import com.example.demo.dto.RegisterRequestDTO;
import com.example.demo.dto.AuthResponseDTO;
import com.example.demo.dto.UserResponseDTO;

import com.example.demo.model.User;
import com.example.demo.service.AuthService;
import com.example.demo.service.UserService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(
            AuthService authService,
            UserService userService) {

        this.authService = authService;
        this.userService = userService;
    }

    // Inscription
    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> register(
            @RequestBody RegisterRequestDTO request) {

        // Créer un objet User à partir du DTO
        User userRequest = new User();

        userRequest.setFullName(request.getFullName());
        userRequest.setDepartement(request.getDepartement());
        userRequest.setEmail(request.getEmail());
        userRequest.setPassword(request.getPassword());

        // Appeler le service avec les deux paramètres attendus
        User user = userService.registerUser(
                userRequest,
                request.getUserType()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(UserResponseDTO.fromEntity(user));
    }

    // Connexion
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(
            @RequestBody LoginRequestDTO request) {

        AuthResponseDTO response = authService.login(request);

        return ResponseEntity.ok(response);
    }

    // Profil de l'utilisateur connecté (utilisé par le frontend après le login)
    @GetMapping("/me")
    public UserResponseDTO me(@AuthenticationPrincipal User current) {
        return UserResponseDTO.fromEntity(current);
    }
}