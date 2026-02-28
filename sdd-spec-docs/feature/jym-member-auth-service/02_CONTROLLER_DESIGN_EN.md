# 02_CONTROLLER_DESIGN (Member & Auth Service)

## 1. Overview
This document defines the REST API controllers for `jym-member-auth-service` based on the OpenAPI Specification.

## 2. Controller: `MemberAuthController`
Handles user registration and authentication workflows.

| Endpoint | Method | Request DTO | Response DTO | Description |
| :--- | :--- | :--- | :--- | :--- |
| `/api/v1/auth/register` | `POST` | `MemberRegistrationRequest` | `MemberProfileResponse` | Create a new user account |
| `/api/v1/auth/login` | `POST` | `MemberLoginRequest` | `AuthTokenResponse` | Authenticate user and issue JWT |

## 3. Controller: `MemberController`
Handles profile management and user-specific queries.

| Endpoint | Method | Request DTO | Response DTO | Description |
| :--- | :--- | :--- | :--- | :--- |
| `/api/v1/members/me` | `GET` | (None) | `MemberProfileResponse` | Retrieve current user profile |

## 4. Implementation Details
- **Validation**: Use `@Valid` to trigger Bean Validation on request DTOs.
- **Success Responses**: Return `HttpStatus.CREATED` (201) for registration and `HttpStatus.OK` (200) for profile queries.
- **Service Interaction**: Controllers MUST only call Service layer methods.
