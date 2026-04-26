package jymusic.jym_member_auth_service.domain.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByUsername(String username);
    Optional<Member> findByEmail(String email);
    Optional<Member> findByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    List<Member> findTop50ByUsernameContainingIgnoreCaseOrNicknameContainingIgnoreCase(
            String usernameKeyword,
            String nicknameKeyword
    );

    List<Member> findByIdIn(Collection<Long> ids);
}
