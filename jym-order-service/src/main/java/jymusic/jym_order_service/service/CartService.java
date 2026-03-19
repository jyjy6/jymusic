package jymusic.jym_order_service.service;

import jymusic.jym_order_service.client.CatalogClient;
import jymusic.jym_order_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_order_service.domain.entity.Cart;
import jymusic.jym_order_service.domain.entity.CartItem;
import jymusic.jym_order_service.domain.repository.CartItemRepository;
import jymusic.jym_order_service.domain.repository.CartRepository;
import jymusic.jym_order_service.dto.request.AddToCartRequest;
import jymusic.jym_order_service.dto.request.UpdateCartItemRequest;
import jymusic.jym_order_service.dto.response.CartItemResponse;
import jymusic.jym_order_service.dto.response.CartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CatalogClient catalogClient;

    public CartResponse getCart(Long memberId) {
        return cartRepository.findByMemberId(memberId)
                .map(this::toCartResponse)
                .orElseGet(() -> CartResponse.builder().cartId(null).items(List.of()).build());
    }

    @Transactional
    public CartResponse addItem(Long memberId, AddToCartRequest request) {
        CatalogClient.ProductInfo productInfo = catalogClient.getProductInfo(request.getProductId());

        if (productInfo.stockQuantity() < request.getQuantity()) {
            throw new GlobalException(
                    "재고가 부족합니다. 최대 " + productInfo.stockQuantity() + "개까지 구매 가능합니다.",
                    "ERR_INSUFFICIENT_STOCK"
            );
        }

        Cart cart = cartRepository.findByMemberId(memberId)
                .orElseGet(() -> cartRepository.save(Cart.builder().memberId(memberId).build()));

        cartItemRepository.findByCartIdAndProductId(cart.getId(), request.getProductId())
                .ifPresentOrElse(
                        existing -> {
                            int newQty = existing.getQuantity() + request.getQuantity();
                            if (newQty > productInfo.stockQuantity()) {
                                throw new GlobalException(
                                        "재고가 부족합니다. 최대 " + productInfo.stockQuantity() + "개까지 구매 가능합니다.",
                                        "ERR_INSUFFICIENT_STOCK"
                                );
                            }
                            existing.updateQuantity(newQty);
                        },
                        () -> {
                            CartItem newItem = CartItem.builder()
                                    .cart(cart)
                                    .productId(request.getProductId())
                                    .quantity(request.getQuantity())
                                    .build();
                            cart.getItems().add(newItem);
                        }
                );

        Cart saved = cartRepository.save(cart);
        return toCartResponse(saved);
    }

    @Transactional
    public CartResponse updateItem(Long memberId, Long cartItemId, UpdateCartItemRequest request) {
        Cart cart = getCartOrThrow(memberId);
        CartItem item = getCartItemOrThrow(cartItemId);
        verifyCartOwnership(cart, item);

        if (request.getQuantity() == 0) {
            cart.getItems().remove(item);
            cartItemRepository.delete(item);
        } else {
            CatalogClient.ProductInfo productInfo = catalogClient.getProductInfo(item.getProductId());
            if (request.getQuantity() > productInfo.stockQuantity()) {
                throw new GlobalException(
                        "재고가 부족합니다. 최대 " + productInfo.stockQuantity() + "개까지 구매 가능합니다.",
                        "ERR_INSUFFICIENT_STOCK"
                );
            }
            item.updateQuantity(request.getQuantity());
        }

        return toCartResponse(cartRepository.save(cart));
    }

    @Transactional
    public void removeItem(Long memberId, Long cartItemId) {
        Cart cart = getCartOrThrow(memberId);
        CartItem item = getCartItemOrThrow(cartItemId);
        verifyCartOwnership(cart, item);
        cart.getItems().remove(item);
        cartItemRepository.delete(item);
    }

    @Transactional
    public void clearCart(Long memberId) {
        cartRepository.findByMemberId(memberId).ifPresent(cart -> {
            cart.getItems().clear();
            cartRepository.save(cart);
        });
    }

    private Cart getCartOrThrow(Long memberId) {
        return cartRepository.findByMemberId(memberId)
                .orElseThrow(() -> new GlobalException("장바구니를 찾을 수 없습니다.", "ERR_CART_NOT_FOUND", HttpStatus.NOT_FOUND));
    }

    private CartItem getCartItemOrThrow(Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new GlobalException("장바구니 아이템을 찾을 수 없습니다.", "ERR_CART_ITEM_NOT_FOUND", HttpStatus.NOT_FOUND));
    }

    private void verifyCartOwnership(Cart cart, CartItem item) {
        if (!item.getCart().getId().equals(cart.getId())) {
            throw new GlobalException("접근 권한이 없습니다.", "ERR_FORBIDDEN", HttpStatus.FORBIDDEN);
        }
    }

    private CartResponse toCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(item -> {
                    CatalogClient.ProductInfo info = catalogClient.getProductInfo(item.getProductId());
                    return CartItemResponse.of(item, info.title(), info.artist(),
                            info.thumbnailUrl(), info.price(), info.stockQuantity());
                })
                .toList();
        return CartResponse.of(cart, itemResponses);
    }
}
