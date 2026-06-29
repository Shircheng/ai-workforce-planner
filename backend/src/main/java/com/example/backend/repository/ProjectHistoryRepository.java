package com.example.backend.repository;

import com.example.backend.entity.ProjectHistory;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectHistoryRepository extends MongoRepository<ProjectHistory, String> {
    List<ProjectHistory> findByEmployeeIdOrderByStartDateDesc(String employeeId);

    List<ProjectHistory> findByEmployeeId(String employeeId);
}
