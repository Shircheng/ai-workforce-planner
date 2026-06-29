package com.example.backend.repository;

import com.example.backend.entity.Availability;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AvailabilityRepository extends MongoRepository<Availability, String> {
    List<Availability> findByEmployeeIdOrderByWeekStartDateAsc(String employeeId);
}
