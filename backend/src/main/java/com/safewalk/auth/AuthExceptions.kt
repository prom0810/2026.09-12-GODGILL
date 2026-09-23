package com.safewalk.auth

/** 이미 가입된 이메일로 회원가입을 시도한 경우. GlobalExceptionHandler가 409로 변환한다. */
class DuplicateEmailException(message: String) : RuntimeException(message)

/** 로그인 시 이메일 또는 비밀번호가 일치하지 않는 경우. GlobalExceptionHandler가 401로 변환한다. */
class InvalidCredentialsException(message: String) : RuntimeException(message)
