package com.example.demo.controller;

import com.example.demo.dto.PublicationRequestDTO;
import com.example.demo.dto.PublicationResponseDTO;
import com.example.demo.model.User;
import com.example.demo.service.PublicationService;
import com.example.demo.service.PublicationService.FileDownload;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/publications")
public class PublicationController {

    private final PublicationService publicationService;

    public PublicationController(PublicationService publicationService) {
        this.publicationService = publicationService;
    }

    // Fil d'actualité (optionnel : ?communityId=3)
    @GetMapping
    public List<PublicationResponseDTO> list(
            @AuthenticationPrincipal User current,
            @RequestParam(value = "communityId", required = false) Integer communityId) {
        return publicationService.list(current.getId(), communityId);
    }

    // Créer une publication, avec un fichier optionnel (multipart/form-data)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PublicationResponseDTO> create(
            @AuthenticationPrincipal User current,
            @RequestParam("title") String title,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "communityId", required = false) Integer communityId,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        PublicationResponseDTO created = publicationService.create(
                current.getId(), title, content, communityId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public PublicationResponseDTO update(
            @AuthenticationPrincipal User current,
            @PathVariable("id") Integer id,
            @RequestBody PublicationRequestDTO request) {
        return publicationService.update(id, current.getId(), request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal User current,
            @PathVariable("id") Integer id) {
        publicationService.delete(id, current.getId());
        return ResponseEntity.noContent().build();
    }

    // Télécharger le fichier joint
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal User current,
            @PathVariable("id") Integer id) {

        FileDownload file = publicationService.download(id, current.getId());
        String name = file.fileName() == null ? "fichier" : file.fileName();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file.resource());
    }
}
