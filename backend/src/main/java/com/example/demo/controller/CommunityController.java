package com.example.demo.controller;

import com.example.demo.dto.CommunityRequestDTO;
import com.example.demo.dto.CommunityResponseDTO;
import com.example.demo.dto.JoinRequestDTO;
import com.example.demo.model.User;
import com.example.demo.service.CommunityService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/communities")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @GetMapping
    public List<CommunityResponseDTO> list(@AuthenticationPrincipal User current) {
        return communityService.list(current.getId());
    }

    @PostMapping
    public ResponseEntity<CommunityResponseDTO> create(
            @AuthenticationPrincipal User current,
            @RequestBody CommunityRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(communityService.create(current.getId(), request));
    }

    // Rejoindre (clé d'accès requise pour une communauté privée)
    @PostMapping("/{id}/join")
    public CommunityResponseDTO join(
            @AuthenticationPrincipal User current,
            @PathVariable("id") Integer id,
            @RequestBody(required = false) JoinRequestDTO request) {
        String key = request == null ? null : request.key();
        return communityService.join(id, current.getId(), key);
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leave(
            @AuthenticationPrincipal User current,
            @PathVariable("id") Integer id) {
        communityService.leave(id, current.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal User current,
            @PathVariable("id") Integer id) {
        communityService.delete(id, current.getId());
        return ResponseEntity.noContent().build();
    }
}
