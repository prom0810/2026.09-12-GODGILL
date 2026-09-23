package com.safewalk.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserTypeRepository : JpaRepository<UserType, Long> {
    fun findByTypeName(typeName: String): UserType?
}
