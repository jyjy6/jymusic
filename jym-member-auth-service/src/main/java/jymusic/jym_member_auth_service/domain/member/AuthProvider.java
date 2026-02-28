package jymusic.jym_member_auth_service.domain.member;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthProvider {
    LOCAL("일반 가입"),
    GOOGLE("구글 로그인"),
    NAVER("네이버 로그인"),
    KAKAO("카카오 로그인");

    private final String description;
}
