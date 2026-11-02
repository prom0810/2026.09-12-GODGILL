package com.safewalk.emergency;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyEventLogRepository extends JpaRepository<EmergencyEventLog, Long> {

    List<EmergencyEventLog> findByEmergency_EmergencyIdOrderByCreatedAtAscEventLogIdAsc(Long emergencyId);
}
