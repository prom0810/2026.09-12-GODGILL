package com.safewalk.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmailAndDeletedAtIsNull(email: String): User?
    fun existsByEmailAndDeletedAtIsNull(email: String): Boolean
}
