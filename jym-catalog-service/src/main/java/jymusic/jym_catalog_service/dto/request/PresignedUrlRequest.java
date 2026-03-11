package jymusic.jym_catalog_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PresignedUrlRequest {

    @NotBlank(message = "파일명은 필수입니다.")
    private String filename;

    @NotBlank(message = "contentType은 필수입니다.")
    private String contentType;
}
