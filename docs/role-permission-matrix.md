# Role & Permission Matrix

## HDFC NetBanking — RBAC Reference

All permissions are enforced at two layers:
1. **API Gateway / Admin Gateway** — JWT role validation before request reaches any service
2. **User Service** — `@PreAuthorize` method-level enforcement as second layer (defence in depth)

---

## User & Authentication Endpoints

| Endpoint | Method | CUSTOMER | TELLER | ADMIN | Notes |
|---|---|:---:|:---:|:---:|---|
| `/api/v1/users/register` | POST | ✅ | ✅ | ✅ | Public — no auth required |
| `/api/v1/auth/login` | POST | ✅ | ✅ | ✅ | Public — no auth required |
| `/api/v1/auth/refresh` | POST | ✅ | ✅ | ✅ | Requires valid refresh token |
| `/api/v1/auth/logout` | POST | ✅ | ✅ | ✅ | Requires valid access token |
| `/api/v1/2fa/setup` | POST | ✅ | ✅ | ✅ | Authenticated users only |
| `/api/v1/2fa/verify` | POST | ✅ | ✅ | ✅ | Authenticated users only |
| `/api/v1/2fa/validate` | POST | ✅ | ✅ | ✅ | Authenticated users only |
| `/api/v1/2fa/disable` | DELETE | ✅ | ✅ | ✅ | Authenticated users only |
| `/api/v1/users/me` | GET | ✅ | ✅ | ✅ | Own profile only |
| `/api/v1/users/me` | PUT | ✅ | ✅ | ✅ | Own profile only |
| `/api/v1/users/me/change-password` | POST | ✅ | ✅ | ✅ | Own account only |

---

## User Management Endpoints

| Endpoint | Method | CUSTOMER | TELLER | ADMIN | Notes |
|---|---|:---:|:---:|:---:|---|
| `/api/v1/users/{userId}` | GET | ❌ | ✅ | ✅ | View any user's profile |
| `/api/v1/users/{userId}/kyc` | PUT | ❌ | ❌ | ✅ | KYC verification — ADMIN only |

---

## Role Assignment Endpoints

| Endpoint | Method | CUSTOMER | TELLER | ADMIN | Notes |
|---|---|:---:|:---:|:---:|---|
| `/api/v1/roles/{targetUserId}/assign` | POST | ❌ | ⚠️ | ✅ | TELLER can only assign CUSTOMER role |
| `/api/v1/roles/{targetUserId}/revoke` | DELETE | ❌ | ⚠️ | ✅ | TELLER can only revoke CUSTOMER role |

---

## Role Assignment Rules (Business Logic)

| Assigner Role | Can Assign | Cannot Assign |
|---|---|---|
| ADMIN | CUSTOMER, TELLER, ADMIN | — |
| TELLER | CUSTOMER only | TELLER, ADMIN |
| CUSTOMER | — (blocked) | All roles |

> ⚠️ Attempting to assign/revoke beyond your permission level throws `UnauthorizedRoleAssignmentException` (HTTP 403).

---

## Gateway Routing Rules

| Traffic Type | Entry Point | Port | JWT Requirement |
|---|---|---|---|
| Customer-facing requests | API Gateway | 8080 | Any valid JWT (CUSTOMER, TELLER, ADMIN) |
| Admin-only requests | Admin Gateway | 8090 | ADMIN role required — all others rejected at gateway |

---

## Role Hierarchy Summary

```
ADMIN
  └── Full access to all endpoints
  └── Can assign/revoke any role

TELLER
  └── Can view any user profile
  └── Can assign/revoke CUSTOMER role only
  └── Cannot access admin endpoints or KYC verification

CUSTOMER
  └── Self-service only (own profile, own password, own 2FA)
  └── Cannot view other users
  └── Cannot assign any roles
```

---

*Last updated: After User Service completion*  
*Security implementation: Spring Security `@PreAuthorize` + JWT filter chain*  
*JWT claim: `role` — values: `ROLE_ADMIN`, `ROLE_TELLER`, `ROLE_CUSTOMER`*
