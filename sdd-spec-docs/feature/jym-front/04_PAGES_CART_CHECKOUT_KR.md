# jym-front: 장바구니 & 결제 페이지 상세 설계

> **포함 도메인**: 장바구니 담기 버튼, 바로 결제 버튼, 장바구니 페이지, 결제(체크아웃) 페이지
> **연관 서비스**: `jym-order-service` → `/api/v1/cart`, `/api/v1/orders`  
> **연관 서비스**: `jym-payment-service` → `/api/v1/payments`

---

## 0. 사전 요구사항: 상품 상세 페이지 업데이트

**파일**: `pages/products/[id].vue` ← 기존 "주문하기" 버튼을 아래로 교체

### 버튼 영역 변경

기존 `[주문하기]` 단일 버튼 → 아래 2개 버튼으로 교체:

```
┌─────────────────────────────────────┐
│  [🛒 장바구니 담기]  [바로 결제 →]  │
└─────────────────────────────────────┘
```

| 버튼 | 스타일 | 동작 |
|---|---|---|
| 장바구니 담기 | `border border-indigo-600 text-indigo-600 hover:bg-indigo-50` | 아래 "장바구니 담기 흐름" 참고 |
| 바로 결제 | `bg-indigo-600 text-white hover:bg-indigo-700` | 아래 "바로 결제 흐름" 참고 |

#### 장바구니 담기 흐름
```
[장바구니 담기] 클릭
  ├─ 비로그인 → toast("로그인이 필요합니다.") + /auth/login 이동
  └─ 로그인 → POST /api/v1/cart/items { productId, quantity: 1 }
       ├─ 성공 → toast("장바구니에 담았습니다.") + 장바구니 아이콘 수량 업데이트
       └─ 실패 → toast("담기에 실패했습니다. 다시 시도해 주세요.")
```

#### 바로 결제 흐름
```
[바로 결제 →] 클릭
  ├─ 비로그인 → toast("로그인이 필요합니다.") + /auth/login 이동
  └─ 로그인 → POST /api/v1/orders (directBuy: true, items: [{productId, quantity: 1}])
       ├─ 성공 → navigateTo('/checkout?orderId={id}')
       └─ 실패 → toast("주문 생성에 실패했습니다.")
```

### 수량 선택 컴포넌트

상품 상세 페이지에 수량 선택기 추가 (두 버튼 위에 배치):

```
[-]  [ 1 ]  [+]    ← 숫자 직접 입력 가능, 최소 1, 최대 stockQuantity
```

> `components/cart/QuantityInput.vue` 를 재사용.  
> Props: `:modelValue="quantity"` `:max="product.stockQuantity"` `@update:modelValue="quantity = $event"`

---

## 1. 장바구니 페이지 `/cart`

**파일**: `pages/cart/index.vue`

### 연결 API

| 메서드 | 경로 | 용도 |
|---|---|---|
| `GET /api/v1/cart` | 장바구니 조회 | 페이지 진입 시 장바구니 내용 로드 |
| `PUT /api/v1/cart/items/{itemId}` | 수량 변경 | 수량 +/- 버튼 클릭 시 |
| `DELETE /api/v1/cart/items/{itemId}` | 아이템 삭제 | 개별 삭제 버튼 클릭 시 |
| `DELETE /api/v1/cart` | 장바구니 전체 비우기 | "장바구니 비우기" 버튼 클릭 시 |
| `POST /api/v1/orders` | 주문 생성 | "주문하기" 버튼 클릭 시 |

### UI 구성

#### 레이아웃 구조

```
[← 쇼핑 계속하기]                      [장바구니 비우기]

┌─────────────────────────────────────────────────────┐
│ [체크] 썸네일  앨범명 / 아티스트        수량  소계  [삭제] │
│ [체크] 썸네일  앨범명 / 아티스트        수량  소계  [삭제] │
│ [체크] 썸네일  앨범명 / 아티스트        수량  소계  [삭제] │
└─────────────────────────────────────────────────────┘

                              ┌──────────────────────┐
                              │  선택 상품 (3개)      │
                              │  총 금액: ₩ 87,000   │
                              │                      │
                              │  [선택 상품 주문하기] │
                              └──────────────────────┘
```

#### 장바구니 아이템 행

| 요소 | 규칙 |
|---|---|
| 체크박스 | 선택/해제로 주문 대상 관리. 기본: 전체 선택 |
| 썸네일 | `w-16 h-16 object-cover rounded`, 없으면 기본 이미지 |
| 앨범명 | `font-medium text-sm`, 클릭 시 `/products/{productId}` 이동 |
| 아티스트 | `text-gray-500 text-xs` |
| 수량 조절 | `[-] [숫자 직접 입력] [+]` 형태. 상세 규칙은 아래 "수량 조절 상세 동작" 참고 |
| 소계 | `₩ {price × quantity}` 형식 |
| 삭제 버튼 | 휴지통 아이콘, 클릭 시 `DELETE /api/v1/cart/items/{itemId}` |

#### 수량 조절 상세 동작

```
[-]  [ 2 ]  [+]
     ↑ 직접 입력 가능한 <input type="number">
```

| 케이스 | 동작 |
|---|---|
| `[-]` 버튼 클릭 | `quantity - 1`. 결과가 0이 되면 삭제 확인 모달 표시 |
| `[+]` 버튼 클릭 | `quantity + 1`. 결과가 `stockQuantity` 초과 시 toast 경고 후 최대값으로 고정 |
| 숫자 직접 입력 후 blur / Enter | 입력값 유효성 검사 후 API 호출 |
| 입력값 `< 1` | `quantity = 1` 로 자동 보정 |
| 입력값 `> stockQuantity` | toast("재고가 부족합니다. 최대 {stockQuantity}개까지 구매 가능합니다.") + `quantity = stockQuantity` 로 자동 보정 |
| 입력값이 숫자가 아님 | 직전 유효값으로 복원 |
| API 호출 | `PUT /api/v1/cart/items/{itemId}` — 디바운스 500ms 적용 (연속 입력 시 마지막 값만 전송) |

> **`stockQuantity` 출처**: `GET /api/v1/cart` 응답의 `CartItem.stockQuantity` 필드.  
> cart-service가 아이템 조회 시 catalog-service에서 재고를 함께 페칭해 응답에 포함.

#### 총 금액 요약 (우측 고정 패널)

| 요소 | 규칙 |
|---|---|
| 선택 상품 수 | 체크된 아이템 개수 |
| 총 금액 | 체크된 아이템의 `price × quantity` 합산 |
| 주문하기 버튼 | `bg-indigo-600 text-white`, 선택 아이템 0개 시 비활성화 |

#### 주문하기 버튼 동작
```
[선택 상품 주문하기] 클릭
  → POST /api/v1/orders { items: [선택된 아이템들의 {productId, quantity}] }
      ├─ 성공 → navigateTo('/checkout?orderId={id}')
      └─ 실패 → toast("주문 생성에 실패했습니다.")
```

### 로딩 / 빈 상태 / 에러 처리

| 상태 | 처리 |
|---|---|
| API 호출 중 | 아이템 행 스켈레톤 UI 표시 (3개 placeholder) |
| 장바구니 비어있음 | "장바구니가 비어있습니다." + `[쇼핑하러 가기]` 버튼 → `/products` |
| 비로그인 접근 | 미들웨어에서 `/auth/login?redirect=/cart` 로 리다이렉트 |
| API 오류 | 에러 배너 표시 |

---

## 2. 결제(체크아웃) 페이지 `/checkout`

**파일**: `pages/checkout/index.vue`

> **쿼리 파라미터**: `?orderId={orderId}` (필수)  
> orderId 없이 접근 시 → `/cart` 로 리다이렉트

### 연결 API

| 메서드 | 경로 | 용도 |
|---|---|---|
| `GET /api/v1/orders/{orderId}` | 주문 상세 조회 | 페이지 진입 시 주문 정보 로드 |
| `POST /api/v1/payments/prepare` | 결제 준비 | 결제 버튼 클릭 시 (PG사 연동 전 서버 검증) |

### UI 구성

#### 레이아웃 구조

```
[← 장바구니로 돌아가기]

┌─────────────────────────────────┬──────────────────────────┐
│  주문 상품 확인                  │  결제 금액 요약           │
│  ─────────────────────────────  │  ─────────────────────── │
│  썸네일  앨범명 / 아티스트       │  상품 금액: ₩ 87,000     │
│          수량: 1  소계: ₩29,000  │  배송비: 무료             │
│  썸네일  앨범명 / 아티스트       │  ─────────────────────── │
│          수량: 2  소계: ₩58,000  │  최종 결제금액            │
│                                 │  ₩ 87,000                │
│  배송지 정보                     │                          │
│  ─────────────────────────────  │  결제 수단 선택           │
│  이름: [입력]                   │  ○ 신용카드               │
│  연락처: [입력]                  │  ○ 계좌이체               │
│  주소: [입력]                   │  ○ 카카오페이             │
│                                 │  ○ 네이버페이             │
│                                 │                          │
│                                 │  [결제하기  ₩ 87,000]    │
└─────────────────────────────────┴──────────────────────────┘
```

#### 주문 상품 확인 섹션

| 요소 | 규칙 |
|---|---|
| 상품 목록 | `GET /api/v1/orders/{orderId}` 응답의 `items` 렌더링 |
| 썸네일 | `w-14 h-14 object-cover rounded` |
| 소계 | `₩ {price × quantity}` |

#### 배송지 정보 폼

| 필드 | 유효성 검사 |
|---|---|
| 받는 분 이름 | 필수, 최대 50자 |
| 연락처 | 필수, 숫자+하이픈 형식 (예: 010-1234-5678) |
| 주소 | 필수, 최대 200자 |
| 상세 주소 | 선택, 최대 100자 |

> 향후 주소 검색 API (카카오 우편번호 서비스) 연동 예정 — 현재는 직접 입력

#### 결제 수단 선택

| 수단 | 내부 값 | 비고 |
|---|---|---|
| 신용/체크카드 | `CARD` | 기본 선택 |
| 계좌이체 | `VIRTUAL_ACCOUNT` | |
| 카카오페이 | `KAKAO_PAY` | |
| 네이버페이 | `NAVER_PAY` | |

#### 결제하기 버튼 동작 (Toss Payments 위젯 방식)

```
[결제하기] 클릭
  1. 폼 유효성 검사 실패 → 각 필드 에러 메시지 표시
  2. POST /api/v1/payments/prepare { orderId, amount }
       ├─ 실패 → toast("결제 준비에 실패했습니다.")
       └─ 성공 → { paymentKey, clientKey } 수신
  3. Toss Payments SDK 호출 (tossPayments.requestPayment)
       - successUrl: /checkout/success?orderId={orderId}
       - failUrl: /checkout/fail?orderId={orderId}
  4. 사용자 → Toss 결제창에서 결제 진행 (리다이렉트)
```

#### 결제 성공 페이지 `/checkout/success`

**파일**: `pages/checkout/success.vue`

쿼리 파라미터: `?paymentKey=&orderId=&amount=`

```
결제 성공 시 Toss가 successUrl로 리다이렉트
  → POST /api/v1/payments/confirm { paymentKey, orderId, amount }
      ├─ 성공 → 성공 화면 렌더링 (주문번호, 결제금액, 날짜 표시)
      │         [주문 내역 확인] 버튼 → /orders/{orderId}
      └─ 실패 → 실패 화면 + [다시 시도] 버튼
```

#### 결제 실패 페이지 `/checkout/fail`

**파일**: `pages/checkout/fail.vue`

쿼리 파라미터: `?code=&message=&orderId=`

```
에러 코드와 메시지 표시
[다시 결제하기] 버튼 → /checkout?orderId={orderId}
[장바구니로 돌아가기] 버튼 → /cart
```

### 로딩 / 에러 처리

| 상태 | 처리 |
|---|---|
| 주문 조회 중 | 스켈레톤 UI |
| orderId 누락 | `/cart` 리다이렉트 |
| 주문 상태가 `PAID` 이상 | "이미 결제된 주문입니다." + `/orders/{orderId}` 이동 |
| 비로그인 접근 | `/auth/login?redirect=/checkout` 리다이렉트 |

---

## 3. 타입 정의 (`types/cart.ts`, `types/checkout.ts`)

```typescript
// types/cart.ts

export interface CartItem {
  cartItemId: number
  productId: number
  title: string
  artist: string
  thumbnailUrl: string | null
  price: number
  quantity: number
  stockQuantity: number  // catalog-service에서 조회한 현재 재고 수량 (수량 상한 검증용)
}

export interface CartResponse {
  cartId: number
  items: CartItem[]
}

export interface AddToCartRequest {
  productId: number
  quantity: number
}

export interface UpdateCartItemRequest {
  quantity: number
}
```

```typescript
// types/checkout.ts

export type PaymentMethod = 'CARD' | 'VIRTUAL_ACCOUNT' | 'KAKAO_PAY' | 'NAVER_PAY'

export interface ShippingInfo {
  recipientName: string
  phone: string
  address: string
  addressDetail: string
}

export interface PaymentPrepareRequest {
  orderId: number
  amount: number
}

export interface PaymentPrepareResponse {
  paymentKey: string
  clientKey: string
  amount: number
}

export interface PaymentConfirmRequest {
  paymentKey: string
  orderId: number
  amount: number
}

export interface PaymentConfirmResponse {
  transactionId: string
  status: 'SUCCESS' | 'FAILED'
  paidAmount: number
  completedAt: string
}
```

---

## 4. 상태 관리 — Pinia 장바구니 스토어 (`stores/cart.ts`)

> 장바구니 아이콘의 수량 뱃지는 전역 상태가 필요하므로 Pinia store 사용

```typescript
// stores/cart.ts
export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItem[]>([])
  const totalCount = computed(() => items.value.reduce((sum, i) => sum + i.quantity, 0))
  const totalPrice = computed(() => items.value.reduce((sum, i) => sum + i.price * i.quantity, 0))

  async function fetchCart(): Promise<void> { ... }
  async function addItem(productId: number, quantity: number): Promise<void> { ... }
  async function updateItemQuantity(cartItemId: number, quantity: number): Promise<void> { ... }
  async function removeItem(cartItemId: number): Promise<void> { ... }
  async function clearCart(): Promise<void> { ... }

  return { items, totalCount, totalPrice, fetchCart, addItem, updateItemQuantity, removeItem, clearCart }
})
```

---

## 5. 공통 컴포넌트 (장바구니/결제 관련)

### `components/cart/CartIcon.vue`
- 네비게이션 바에 배치
- Props: (없음, store에서 직접 읽음)
- `useCartStore().totalCount` 를 뱃지로 표시
- 클릭 시 `/cart` 이동

### `components/cart/CartItemRow.vue`
- Props: `item: CartItem`, `isSelected: boolean`
- Emits: `update:isSelected`, `update:quantity(newQty: number)`, `remove`
- 장바구니 페이지의 각 아이템 행
- 내부적으로 `localQuantity: ref<number>` 를 두어 입력 중 UI 즉시 반응, blur/Enter 시 `update:quantity` emit
- `item.stockQuantity` 를 수량 입력의 상한값으로 사용

### `components/cart/QuantityInput.vue`
- Props: `modelValue: number`, `max: number`, `min: number = 1`
- Emits: `update:modelValue`
- 재사용 가능한 수량 입력 컴포넌트 (`CartItemRow`, 상품 상세 페이지에서 공용 사용)
- `<input type="number" :min="min" :max="max">` + `[-]` / `[+]` 버튼 조합
- `max` 초과 시 내부에서 toast 경고 후 `max` 값으로 보정하여 emit

### `components/checkout/OrderSummaryPanel.vue`
- Props: `items: OrderItemDetail[]`, `totalAmount: number`
- 결제 페이지 우측 요약 패널

### `components/checkout/ShippingForm.vue`
- Props/Emits: `v-model:shippingInfo`
- 배송지 입력 폼, vee-validate 또는 네이티브 유효성 검사 사용

---

## 6. 라우트 가드 (미들웨어)

`middleware/auth.ts` 에 `/cart`, `/checkout/**` 경로에 대한 인증 가드 추가:

```typescript
// middleware/auth.ts (기존 파일에 경로 추가)
const protectedRoutes = ['/cart', '/checkout']
if (protectedRoutes.some(r => to.path.startsWith(r)) && !isLoggedIn) {
  return navigateTo(`/auth/login?redirect=${to.fullPath}`)
}
```
