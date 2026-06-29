package com.example.backend.repository;

import com.example.backend.entity.Allocation;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AllocationRepository extends MongoRepository<Allocation, String> {
    List<Allocation> findByEmployeeIdOrderByStartDateDesc(String employeeId);
}
