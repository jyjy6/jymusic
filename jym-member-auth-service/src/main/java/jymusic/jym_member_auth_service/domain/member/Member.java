package jymusic.jym_member_auth_service.domain.member;

import jakarta.persistence.*;
import jymusic.jym_member_auth_service.domain.common.BaseTimeEntity;
import lombok.*;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = true) // Social login may have null password
    private String password;

    @Column(length = 100)
    private String email;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider authProvider;

    @Column(length = 255)
    private String providerId;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    // Business Logic: Profile Update
    public void updateProfile(String nickname, String email) {
        this.nickname = nickname;
        this.email = email;
    }

    // Business Logic: Account Deactivation
    public void deactivate() {
        this.isActive = false;
    }
}
