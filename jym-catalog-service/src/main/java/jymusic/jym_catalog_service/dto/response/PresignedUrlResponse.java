package jymusic.jym_catalog_service.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PresignedUrlResponse {

    private String presignedUrl;
    private String objectKey;
}
