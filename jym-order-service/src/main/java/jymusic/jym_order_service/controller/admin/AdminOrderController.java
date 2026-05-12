package jymusic.jym_order_service.controller.admin;

import jakarta.validation.Valid;
import jymusic.jym_order_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_order_service.dto.request.AdminOrderSearchRequest;
import jymusic.jym_order_service.dto.request.AdminStatusUpdateRequest;
import jymusic.jym_order_service.dto.response.AdminOrderDetailResponse;
import jymusic.jym_order_service.dto.response.AdminOrderSummaryResponse;
import jymusic.jym_order_service.service.admin.AdminOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/orders/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @GetMapping
    public ResponseEntity<Page<AdminOrderSummaryResponse>> search(
            @ModelAttribute AdminOrderSearchRequest request,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        if (pageable.getPageSize() > 100) {
            throw new GlobalException("페이지 크기는 최대 100입니다.", "ERR_PAGE_SIZE_EXCEEDED");
        }
        return ResponseEntity.ok(adminOrderService.search(request, pageable));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<AdminOrderDetailResponse> detail(@PathVariable Long orderId) {
        return ResponseEntity.ok(adminOrderService.getDetail(orderId));
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<AdminOrderDetailResponse> updateStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody AdminStatusUpdateRequest request,
            @AuthenticationPrincipal String adminId
    ) {
        return ResponseEntity.ok(adminOrderService.updateStatus(orderId, request, Long.parseLong(adminId)));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(adminOrderService.statusCounts().entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue)));
    }
}
