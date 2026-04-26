package jymusic.jym_member_auth_service.service.member;

import jymusic.jym_member_auth_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_member_auth_service.common.jwt.JwtProvider;
import jymusic.jym_member_auth_service.domain.member.Member;
import jymusic.jym_member_auth_service.domain.member.MemberRepository;
import jymusic.jym_member_auth_service.dto.member.MemberLoginRequest;
import jymusic.jym_member_auth_service.dto.member.MemberProfileResponse;
import jymusic.jym_member_auth_service.dto.member.MemberRegistrationRequest;
import jymusic.jym_member_auth_service.dto.member.InternalMemberSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
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
        String accessToken = jwtProvider.createAccessToken(member.getId(), member.getUsername(), member.getRole().name(), member.getNickname());
        String refreshToken = jwtProvider.createRefreshToken(member.getUsername());

        // 4. Return both for controller to handle
        return Map.of("accessToken", accessToken, "refreshToken", refreshToken);
    }

    public MemberProfileResponse getProfile(String username) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new GlobalException("사용자를 찾을 수 없습니다.", "ERR_MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND));

        return MemberProfileResponse.fromEntity(member);
    }

    @Transactional
    public Map<String, String> refreshTokens(String refreshToken) {
        // 1. 서명 검증 + username 추출 (위·변조 토큰은 여기서 GlobalException 발생)
        String username = jwtProvider.extractUsername(refreshToken);

        // 2. Redis 검증 + RTR: 새 Refresh Token 발급 (탈취 감지 시 GlobalException)
        String newRefreshToken = jwtProvider.rotateRefreshToken(refreshToken, username);

        // 3. 새 Access Token 발급 (DB 조회로 최신 role, nickname 반영)
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new GlobalException("사용자를 찾을 수 없습니다.", "ERR_MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND));

        String newAccessToken = jwtProvider.createAccessToken(
                member.getId(), member.getUsername(), member.getRole().name(), member.getNickname()
        );

        return Map.of("accessToken", newAccessToken, "refreshToken", newRefreshToken);
    }

    public void logout(String refreshToken) {
        try {
            String username = jwtProvider.extractUsername(refreshToken);
            jwtProvider.deleteRefreshToken(username);
            log.info("로그아웃 완료 - Redis Refresh Token 삭제: {}", username);
        } catch (Exception e) {
            // 이미 만료되었거나 유효하지 않은 토큰이어도 로그아웃은 정상 처리
            log.warn("로그아웃 시 토큰 파싱 실패 (무시): {}", e.getMessage());
        }
    }

    public InternalMemberSummaryResponse getMemberById(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new GlobalException("사용자를 찾을 수 없습니다.", "ERR_MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND));
        return InternalMemberSummaryResponse.fromEntity(member);
    }

    public List<InternalMemberSummaryResponse> searchMembers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new GlobalException("keyword는 필수입니다.", "ERR_MISSING_PARAMETER", HttpStatus.BAD_REQUEST);
        }

        return memberRepository.findTop50ByUsernameContainingIgnoreCaseOrNicknameContainingIgnoreCase(keyword, keyword)
                .stream()
                .map(InternalMemberSummaryResponse::fromEntity)
                .toList();
    }

    public List<InternalMemberSummaryResponse> getMembersBatch(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return memberRepository.findByIdIn(ids).stream()
                .map(InternalMemberSummaryResponse::fromEntity)
                .toList();
    }
}
