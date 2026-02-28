package jymusic.jym_member_auth_service.service.member;

import jymusic.jym_member_auth_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_member_auth_service.common.jwt.JwtProvider;
import jymusic.jym_member_auth_service.domain.member.Member;
import jymusic.jym_member_auth_service.domain.member.MemberRepository;
import jymusic.jym_member_auth_service.dto.member.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

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

        // 3. Encrypt Password
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 4. Save Member
        Member member = request.toEntity(encodedPassword);
        Member savedMember = memberRepository.save(member);

        // 5. Return DTO
        return MemberProfileResponse.fromEntity(savedMember);
    }

    @Transactional
    public Map<String, String> login(MemberLoginRequest request) {
        // 1. Find User
        Member member = memberRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new GlobalException("아이디 또는 비밀번호가 올바르지 않습니다.", "ERR_LOGIN_FAILED", HttpStatus.UNAUTHORIZED));

        // 2. Check Password
        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new GlobalException("아이디 또는 비밀번호가 올바르지 않습니다.", "ERR_LOGIN_FAILED", HttpStatus.UNAUTHORIZED);
        }

        // 3. Issue Tokens
        String accessToken = jwtProvider.createAccessToken(member.getId(), member.getUsername(), member.getRole().name());
        String refreshToken = jwtProvider.createRefreshToken(member.getUsername());

        // 4. Return both for controller to handle
        return Map.of("accessToken", accessToken, "refreshToken", refreshToken);
    }

    public MemberProfileResponse getProfile(String username) {
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
