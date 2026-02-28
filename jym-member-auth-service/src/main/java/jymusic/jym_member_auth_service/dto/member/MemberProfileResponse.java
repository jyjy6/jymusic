package jymusic.jym_member_auth_service.dto.member;

import jymusic.jym_member_auth_service.domain.member.Member;
import jymusic.jym_member_auth_service.domain.member.Role;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MemberProfileResponse {

    private Long id;
    private String username;
    private String email;
    private String nickname;
    private Role role;

    public static MemberProfileResponse fromEntity(Member member) {
        return MemberProfileResponse.builder()
                .id(member.getId())
                .username(member.getUsername())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .role(member.getRole())
                .build();
    }
}
