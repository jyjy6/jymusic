package jymusic.jym_member_auth_service.controller.member;

import jymusic.jym_member_auth_service.dto.member.MemberProfileResponse;
import jymusic.jym_member_auth_service.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
