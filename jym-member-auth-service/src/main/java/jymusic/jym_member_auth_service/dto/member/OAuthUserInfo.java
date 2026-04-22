package jymusic.jym_member_auth_service.dto.member;

import jymusic.jym_member_auth_service.domain.member.AuthProvider;
import lombok.*;

/**
 * Provider(Google/Kakao)의 UserInfo 응답을 정규화한 DTO.
 * 각 Provider의 원시 JSON을 이 공통 구조로 변환합니다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OAuthUserInfo {

    /** Provider 측의 고유 사용자 식별자 (Google: sub, Kakao: id) */
    private String providerId;

    /** 사용자 이메일 (nullable: Provider에서 미제공 가능) */
    private String email;

    /** 사용자 이름/닉네임 */
    private String nickname;

    /** 이 사용자 정보를 제공한 Provider */
    private AuthProvider provider;
}
