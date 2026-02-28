package jymusic.jym_member_auth_service.service.member;

import jymusic.jym_member_auth_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_member_auth_service.domain.member.Member;
import jymusic.jym_member_auth_service.domain.member.MemberRepository;
import jymusic.jym_member_auth_service.dto.member.MemberProfileResponse;
import jymusic.jym_member_auth_service.dto.member.MemberRegistrationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional
    public MemberProfileResponse register(MemberRegistrationRequest request) {
        // 1. Check Duplicate Username
        if (memberRepository.existsByUsername(request.getUsername())) {
            throw new GlobalException("이미 사용 중인 아이디입니다.", "ERR_DUPLICATE_USERNAME", HttpStatus.CONFLICT);
        }

        // 2. Check Duplicate Email (If provided)
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (memberRepository.existsByEmail(request.getEmail())) {
                throw new GlobalException("이미 등록된 이메일입니다.", "ERR_DUPLICATE_EMAIL", HttpStatus.CONFLICT);
            }
        }

        // 3. Encrypt Password (Placeholder for now)
        String encodedPassword = request.getPassword(); // TODO: Implement BCrypt

        // 4. Save Member
        Member member = request.toEntity(encodedPassword);
        Member savedMember = memberRepository.save(member);

        // 5. Return DTO
        return MemberProfileResponse.fromEntity(savedMember);
    }

    public MemberProfileResponse getProfile(String username) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new GlobalException("사용자를 찾을 수 없습니다.", "ERR_MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND));
        
        return MemberProfileResponse.fromEntity(member);
    }
}
