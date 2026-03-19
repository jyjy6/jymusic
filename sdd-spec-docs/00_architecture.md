# Jymusic Project: Architectural Constitution (AI-Focused)

## 1. System Overview

The Jymusic project is a Music Album E-commerce platform built on a Microservices Architecture (MSA).

### 1.1 Core Technology Stack

- **Frontend**: Nuxt 4 (Vue 3, TypeScript, Nitro engine)
- **Backend**: Spring Boot 3.x / 4.x (Java 21), Spring Cloud
- **API Gateway**: Spring Cloud Gateway (WebMvc)
- **Database**: MySQL (Database-per-service pattern)

## 2. Fundamental Principles

### 2.1 Frontend Standards (Nuxt 4)

- **Composition API**: MUST use `<script setup lang="ts">` for all components.
- **API Communication**: MUST use **Axios** for all external and internal API calls.
- **State Management**: Use Pinia for centralized state management if needed.
- **Styling**: MUST use **Tailwind CSS** for all styling. No scoped `<style>` blocks or external CSS files unless absolutely necessary. Class-based utility styling only.

### 2.2 Backend Standards (Spring Boot)

- **Lombok**: MANDATORY use of Lombok to reduce boilerplate code.
- **DTO (Data Transfer Object)**: MUST use DTOs for API requests and responses. NEVER expose Entities directly.
- **Builder Pattern**: Highly PREFERRED for creating DTO and Entity instances to ensure immutability and readability.
- **Global Error Handling**: MUST use `GlobalErrorHandler.GlobalException` for all business logic exceptions.
- **Exception Pattern**: Implement a unified error response via the centralized `GlobalExceptionHandler`.
- **API Gateway**: The gateway (`jym-api-gateway`) is the single entry point for all client requests.

### 2.3 Testing Standards

- **Unit Testing**: MANDATORY for all services.
- **Coverage**: Maintain a minimum unit test coverage of **70%**.
- **Integration Testing**: Deferred for now; focus on high-quality unit tests.

### 2.3.1 Development & Testing Workflow

① Write API Spec (OAS)
↓
② Implement Business Code
↓
③ Write Test Code

- Happy path cases
- Boundary value / Exception cases
- If gaps are found in the spec → patch spec & business code accordingly
  ↓
  ④ Tests act as a regression safety net for future changes

### 2.4 Spec-Driven Development (SDD)

- All API changes start with an OpenAPI Specification openapi.yaml (OAS 3.0/3.1) .
- Documentation is the source of truth.

## 3. Communication & Security

- **RESTful**: Adhere to REST maturity level 2/3.
- **Statelessness**: No session state; use JWT for authorization.
- **Database-per-service**: Services must never access other services' databases directly.

---

_Note: This document is the primary reference for AI agents to understand the system architecture and constraints._
