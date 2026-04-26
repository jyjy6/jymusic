package jymusic.jym_member_auth_service.controller.member;

import jymusic.jym_member_auth_service.dto.member.MemberProfileResponse;
import jymusic.jym_member_auth_service.dto.member.InternalMemberSummaryResponse;
import jymusic.jym_member_auth_service.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/me")
    public ResponseEntity<MemberProfileResponse> getMyProfile(
            @RequestHeader(value = "X-User-Name", required = false) String gatewayUsername,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        // 1. If called through Gateway, use the injected header
        String username = (gatewayUsername != null) ? gatewayUsername : 
                          (userDetails != null ? userDetails.getUsername() : null);

        if (username == null) {
            return ResponseEntity.status(401).build();
        }

        MemberProfileResponse response = memberService.getProfile(username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<List<InternalMemberSummaryResponse>> searchMembers(
            @RequestParam String keyword
    ) {
        return ResponseEntity.ok(memberService.searchMembers(keyword));
    }

    @GetMapping("/batch")
    public ResponseEntity<List<InternalMemberSummaryResponse>> getMembersBatch(
            @RequestParam(name = "ids") List<Long> ids
    ) {
        return ResponseEntity.ok(memberService.getMembersBatch(ids));
    }

    @GetMapping("/{memberId}")
    public ResponseEntity<InternalMemberSummaryResponse> getMemberById(@PathVariable Long memberId) {
        return ResponseEntity.ok(memberService.getMemberById(memberId));
    }
}
