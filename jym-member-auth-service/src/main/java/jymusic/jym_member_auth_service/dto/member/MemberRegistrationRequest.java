package jymusic.jym_member_auth_service.dto.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jymusic.jym_member_auth_service.domain.member.AuthProvider;
import jymusic.jym_member_auth_service.domain.member.Member;
import jymusic.jym_member_auth_service.domain.member.Role;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MemberRegistrationRequest {

    @NotBlank(message = "아이디는 필수입니다.")
    @Size(min = 4, max = 50)
    private String username;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 4, max = 100)
    private String password;

    @NotBlank(message = "닉네임은 필수입니다.")
    private String nickname;

    @Email(message = "유효한 이메일 형식이 아닙니다.")
    private String email;

    public Member toEntity(String encodedPassword) {
        return Member.builder()
                .username(username)
                .password(encodedPassword)
                .nickname(nickname)
                .email(email)
                .role(Role.ROLE_USER)
                .authProvider(AuthProvider.LOCAL)
                .isActive(true)
                .build();
    }
}
