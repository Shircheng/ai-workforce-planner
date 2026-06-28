package com.example.backend.repository;

import com.example.backend.entity.ProjectHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectHistoryRepository extends MongoRepository<ProjectHistory, String> {
}
