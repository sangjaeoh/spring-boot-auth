package com.example.auth.app.admin.presentation.v1;

import jakarta.validation.constraints.NotBlank;

/**
 * 역할 배정 요청이다({@code role}은 역할명 — USER·ADMIN·SUPER_ADMIN).
 */
public record RoleAssignRequest(@NotBlank String role) {}
