package jymusic.jym_order_service.client;

import jymusic.jym_order_service.common.GlobalErrorHandler.GlobalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemberClient {

    private final RestClient memberAuthRestClient;

    public List<Long> searchMemberIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        try {
            MemberSearchResponse[] results = memberAuthRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/members/search")
                            .queryParam("keyword", keyword)
                            .build())
                    .retrieve()
                    .body(MemberSearchResponse[].class);

            return Arrays.stream(Objects.requireNonNullElse(results, new MemberSearchResponse[0]))
                    .map(MemberSearchResponse::memberId)
                    .toList();
        } catch (Exception e) {
            log.error("member-auth 검색 실패: keyword={}", keyword, e);
            throw new GlobalException(
                    "회원 정보를 조회할 수 없습니다.",
                    "ERR_MEMBER_UNAVAILABLE",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    public Map<Long, MemberSummary> getMembers(Set<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }

        String ids = memberIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        try {
            MemberSummary[] results = memberAuthRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/members/batch")
                            .queryParam("ids", ids)
                            .build())
                    .retrieve()
                    .body(MemberSummary[].class);

            return Arrays.stream(Objects.requireNonNullElse(results, new MemberSummary[0]))
                    .collect(Collectors.toMap(MemberSummary::memberId, summary -> summary));
        } catch (Exception e) {
            log.error("member-auth 배치 조회 실패: ids={}", ids, e);
            throw new GlobalException(
                    "회원 정보를 조회할 수 없습니다.",
                    "ERR_MEMBER_UNAVAILABLE",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    public MemberSummary getMember(Long memberId) {
        try {
            MemberSummary result = memberAuthRestClient.get()
                    .uri("/api/v1/members/{id}", memberId)
                    .retrieve()
                    .body(MemberSummary.class);

            return result == null ? MemberSummary.unknown(memberId) : result;
        } catch (Exception e) {
            log.error("member-auth 단건 조회 실패: memberId={}", memberId, e);
            throw new GlobalException(
                    "회원 정보를 조회할 수 없습니다.",
                    "ERR_MEMBER_UNAVAILABLE",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    public record MemberSearchResponse(Long memberId, String username, String nickname) {
    }

    public record MemberSummary(Long memberId, String username, String nickname, String email) {
        public static MemberSummary unknown(Long memberId) {
            return new MemberSummary(memberId, "unknown", "unknown", "");
        }
    }
}
