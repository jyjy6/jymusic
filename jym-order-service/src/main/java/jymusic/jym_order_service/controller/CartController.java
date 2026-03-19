package jymusic.jym_order_service.controller;

import jakarta.validation.Valid;
import jymusic.jym_order_service.dto.request.AddToCartRequest;
import jymusic.jym_order_service.dto.request.UpdateCartItemRequest;
import jymusic.jym_order_service.dto.response.CartResponse;
import jymusic.jym_order_service.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal String memberId) {
        return ResponseEntity.ok(cartService.getCart(Long.parseLong(memberId)));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @AuthenticationPrincipal String memberId,
            @Valid @RequestBody AddToCartRequest request) {
        return ResponseEntity.ok(cartService.addItem(Long.parseLong(memberId), request));
    }

    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> updateItem(
            @AuthenticationPrincipal String memberId,
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(cartService.updateItem(Long.parseLong(memberId), cartItemId, request));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<Void> removeItem(
            @AuthenticationPrincipal String memberId,
            @PathVariable Long cartItemId) {
        cartService.removeItem(Long.parseLong(memberId), cartItemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal String memberId) {
        cartService.clearCart(Long.parseLong(memberId));
        return ResponseEntity.noContent().build();
    }
}
