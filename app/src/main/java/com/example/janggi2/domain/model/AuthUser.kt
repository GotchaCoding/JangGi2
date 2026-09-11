package com.example.janggi2.domain.model

/** 구글 로그인으로 인증된 사용자. */
data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?
)
