package jymusic.jym_catalog_service.controller;

import jakarta.validation.Valid;
import jymusic.jym_catalog_service.dto.request.PresignedUrlRequest;
import jymusic.jym_catalog_service.dto.response.PresignedUrlResponse;
import jymusic.jym_catalog_service.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/presigned-url")
    public ResponseEntity<PresignedUrlResponse> getPresignedUrl(
            @Valid @RequestBody PresignedUrlRequest request) {
        return ResponseEntity.ok(mediaService.generatePresignedUrl(request));
    }
}
