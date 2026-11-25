package com.example.demo.controller;

import com.example.demo.dto.UserResponseDTO;
import com.example.demo.model.User;
import com.example.demo.model.UserStatus;
import com.example.demo.service.UserService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    // Tous les comptes
    @GetMapping("/users")
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers()
                .stream()
                .map(UserResponseDTO::fromEntity)
                .toList());
    }

    // Consulter les inscriptions en attente
    @GetMapping("/pending-users")
    public ResponseEntity<List<UserResponseDTO>> getPendingUsers() {
        return ResponseEntity.ok(userService.getPendingUsers()
                .stream()
                .map(UserResponseDTO::fromEntity)
                .toList());
    }

    // Accepter une inscription
    @PutMapping("/users/{id}/approve")
    public ResponseEntity<UserResponseDTO> approveUser(@PathVariable("id") Integer id) {
        User user = userService.updateUserStatus(id, UserStatus.APPROVED);
        return ResponseEntity.ok(UserResponseDTO.fromEntity(user));
    }

    // Refuser une inscription
    @PutMapping("/users/{id}/reject")
    public ResponseEntity<UserResponseDTO> rejectUser(@PathVariable("id") Integer id) {
        User user = userService.updateUserStatus(id, UserStatus.REJECTED);
        return ResponseEntity.ok(UserResponseDTO.fromEntity(user));
    }

    // Supprimer un compte (et ses publications / commentaires)
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable("id") Integer id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
