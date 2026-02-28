package jymusic.jym_member_auth_service.dto.member;

import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AuthTokenResponse {

    private String accessToken;
    
    @Builder.Default
    private String tokenType = "Bearer";
}
