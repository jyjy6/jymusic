package jymusic.jym_member_auth_service.dto.member;

import jymusic.jym_member_auth_service.domain.member.Member;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InternalMemberSummaryResponse {
    private Long memberId;
    private String username;
    private String nickname;
    private String email;

    public static InternalMemberSummaryResponse fromEntity(Member member) {
        return InternalMemberSummaryResponse.builder()
                .memberId(member.getId())
                .username(member.getUsername())
                .nickname(member.getNickname())
                .email(member.getEmail())
                .build();
    }
}
