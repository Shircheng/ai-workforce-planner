package com.example.backend.repository;

import com.example.backend.entity.EmployeeSkill;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeSkillRepository extends MongoRepository<EmployeeSkill, String> {
}
