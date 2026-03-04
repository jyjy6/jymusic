############## Complete #################

# 00_OAS_PLAN (Member & Auth Service)

## 1. Objective

Define the API contract for authentication, user registration, and profile management for the `jym-member-auth-service`.

## 2. Core Endpoints

- `POST /api/v1/auth/login`: Issue JWT token.
- `POST /api/v1/auth/register`: Register new user.
- `GET /api/v1/members/me`: Retrieve current user profile.

## 3. Tech Stack Requirements

- Backend: Spring Boot (Spring Security)
- Database: MySQL (User/Role table)
- Error Pattern: `GlobalErrorHandler.GlobalException`
