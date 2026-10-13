package com.safewalk.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTypeRepository extends JpaRepository<UserType, Long> {

    Optional<UserType> findByTypeName(String typeName);
}
