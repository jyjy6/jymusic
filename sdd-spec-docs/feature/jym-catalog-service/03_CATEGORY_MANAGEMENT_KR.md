# 카테고리 관리 기능 스펙 (Category Management)

> **목적**: `jym-catalog-service`의 카테고리 CRUD 관리자 API 및 프론트엔드 어드민 페이지 구현 스펙
> **연관 스펙**: `openapi.yaml` (v1.2.0), `01_TABLE_DESIGN_KR.md`, `02_IMPLEMENTATION_KR.md`

---

## 1. 개요

### 1.1 배경

초기 구현에서는 카테고리 **조회(GET)** 만 존재했으나, 실제 운영 시 관리자가 카테고리를 동적으로 추가·수정·삭제해야 하는 요구사항이 발생하여 CRUD 전체를 구현한다.

### 1.2 변경 범위

| 레이어 | 변경 내용 |
|---|---|
| Entity | `Category.update()` 메서드 추가 |
| Repository | `existsByName`, `existsByNameAndIdNot` 쿼리 메서드 추가 |
| DTO | `CategoryCreateRequest`, `CategoryUpdateRequest` 신규 생성 |
| Service | `createCategory`, `updateCategory`, `deleteCategory` 메서드 추가 |
| Controller | `POST /categories`, `PUT /categories/{id}`, `DELETE /categories/{id}` 엔드포인트 추가 |
| Frontend Composable | `useCategoryAdmin` 추가 (in `useCatalog.ts`) |
| Frontend Page | `/admin/categories` 관리 페이지 신규 생성 |
| Admin Layout | 서브 네비게이션에 "Categories" 탭 추가 |
| OAS | `openapi.yaml` v1.2.0으로 업데이트 |

---

## 2. API 엔드포인트 명세

### 2.1 공통 사항

- **Base Path**: `/api/v1/categories`
- **인증**: `POST`, `PUT`, `DELETE`는 `ROLE_ADMIN` 권한 필요 (`Authorization: Bearer <JWT>`)
- **에러 응답 형식**: `GlobalExceptionHandler` 기반 공통 포맷

```json
{
  "status": 409,
  "code": "ERR_CATEGORY_DUPLICATE",
  "message": "이미 존재하는 카테고리 이름입니다.",
  "timestamp": "2026-03-12T10:00:00"
}
```

### 2.2 GET `/api/v1/categories` — 카테고리 목록 조회 (기존)

| 항목 | 내용 |
|---|---|
| 인증 | 불필요 |
| 응답 | `200 OK` — `CategoryResponse[]` |

### 2.3 POST `/api/v1/categories` — 카테고리 생성

| 항목 | 내용 |
|---|---|
| 인증 | `ROLE_ADMIN` |
| 요청 Body | `CategoryCreateRequest` |
| 응답 | `201 Created` — `CategoryResponse` |
| 에러 | `400` (validation), `409` (이름 중복) |

**Request Body:**

```json
{
  "name": "Classical"
}
```

**Response Body:**

```json
{
  "id": 4,
  "name": "Classical"
}
```

### 2.4 PUT `/api/v1/categories/{categoryId}` — 카테고리 수정

| 항목 | 내용 |
|---|---|
| 인증 | `ROLE_ADMIN` |
| Path Variable | `categoryId` (Long) |
| 요청 Body | `CategoryUpdateRequest` |
| 응답 | `200 OK` — `CategoryResponse` |
| 에러 | `400` (validation), `404` (카테고리 없음), `409` (이름 중복) |

**Request Body:**

```json
{
  "name": "Classical Music"
}
```

### 2.5 DELETE `/api/v1/categories/{categoryId}` — 카테고리 삭제

| 항목 | 내용 |
|---|---|
| 인증 | `ROLE_ADMIN` |
| Path Variable | `categoryId` (Long) |
| 응답 | `204 No Content` |
| 에러 | `404` (카테고리 없음) |

> **주의**: 물리 삭제(hard delete). 해당 카테고리에 연결된 `Product`의 `category_id`는 DB FK 설정에 따라 `NULL` 처리된다. 운영 시 상품이 연결된 카테고리 삭제 전 사전 확인 UI 제공 필요.

---

## 3. 백엔드 구현 세부 사항

### 3.1 Domain Entity — `Category.java`

```java
public void update(String name) {
    this.name = name;
}
```

`@Column(unique = true)` 제약이 있으므로 DB 레벨 중복도 방어되나, 서비스 레이어에서 `409 CONFLICT`를 명시적으로 반환한다.

### 3.2 Repository — `CategoryRepository.java`

```java
boolean existsByName(String name);
boolean existsByNameAndIdNot(String name, Long id);
```

- `existsByName`: 생성 시 이름 중복 검사
- `existsByNameAndIdNot`: 수정 시 자기 자신을 제외한 이름 중복 검사

### 3.3 DTO

#### `CategoryCreateRequest`

```java
@NotBlank
@Size(max = 50)
private String name;
```

#### `CategoryUpdateRequest`

```java
@NotBlank
@Size(max = 50)
private String name;
```

> 두 Request는 현재 구조가 동일하나 향후 확장성을 위해 별도 클래스로 분리한다.

### 3.4 Service — `CategoryService.java`

| 메서드 | 트랜잭션 | 설명 |
|---|---|---|
| `getAllCategories()` | `readOnly` | 전체 조회 |
| `createCategory(request)` | `@Transactional` | 이름 중복 검사 후 저장 |
| `updateCategory(id, request)` | `@Transactional` | 존재 확인 → 이름 중복 검사 → dirty checking으로 수정 |
| `deleteCategory(id)` | `@Transactional` | 존재 확인 후 물리 삭제 |

#### 에러 코드 정의

| 코드 | HTTP | 설명 |
|---|---|---|
| `ERR_CATEGORY_NOT_FOUND` | `404` | 해당 ID의 카테고리가 없음 |
| `ERR_CATEGORY_DUPLICATE` | `409` | 동일 이름의 카테고리가 이미 존재함 |

### 3.5 Controller — `CategoryController.java`

```java
@PostMapping
public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryCreateRequest request)

@PutMapping("/{categoryId}")
public ResponseEntity<CategoryResponse> updateCategory(@PathVariable Long categoryId, @Valid @RequestBody CategoryUpdateRequest request)

@DeleteMapping("/{categoryId}")
public ResponseEntity<Void> deleteCategory(@PathVariable Long categoryId)
```

---

## 4. 프론트엔드 구현 세부 사항

### 4.1 Composable — `useCategoryAdmin` (in `useCatalog.ts`)

헌법 2.1 준수: Axios 사용, Composition API

```typescript
export const useCategoryAdmin = () => {
  const { $axios } = useNuxtApp();
  const isSubmitting = ref(false);

  const createCategory = async (name: string): Promise<Category>
  const updateCategory = async (id: number, name: string): Promise<Category>
  const deleteCategory = async (id: number): Promise<void>

  return { isSubmitting, createCategory, updateCategory, deleteCategory };
};
```

`categories` 상태는 기존 `useCategories`의 `useState`를 공유하므로 `useCategoryAdmin`에서는 직접 `categories.value`를 수정하거나, 페이지에서 반환값으로 목록을 갱신한다.

### 4.2 Page — `/admin/categories/index.vue`

| 기능 | 설명 |
|---|---|
| 카테고리 목록 표시 | ID, 이름, 수정/삭제 버튼 |
| 새 카테고리 추가 폼 | 상단 인라인 폼, Enter 키 미지원 (버튼 클릭) |
| 인라인 수정 | 수정 버튼 클릭 시 해당 행이 input으로 전환, Enter/Escape 지원 |
| 삭제 확인 모달 | 실수 삭제 방지 목적 |
| 로딩 스켈레톤 | 목록 조회 중 pulse 애니메이션 표시 |
| 토스트 알림 | 성공/실패 피드백 (`useUiToast`) |
| 인증 가드 | `adminMiddleware` 적용 |

### 4.3 Admin Layout — `admin.vue`

서브 네비게이션에 "Categories" 탭 추가.

```
Products | Add Product | Categories
```

`getTabClass`가 `'products' | 'new' | 'categories'` 유니온 타입으로 확장됨.
`route.path.startsWith('/admin/categories')`로 활성 상태 판단.

---

## 5. OAS 변경 이력

| 버전 | 날짜 | 내용 |
|---|---|---|
| 1.1.0 | 2026-03-10 | 초기 상품/카테고리/미디어 API 정의 |
| 1.2.0 | 2026-03-12 | 카테고리 관리자 CRUD 엔드포인트 추가 (`POST /categories`, `PUT /categories/{id}`, `DELETE /categories/{id}`), `CategoryCreateRequest`·`CategoryUpdateRequest` 스키마 추가, `CategoryId` 파라미터 컴포넌트 추가, 카테고리 태그 공개/관리자로 분리 |
