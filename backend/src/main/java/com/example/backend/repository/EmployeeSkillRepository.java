package com.example.backend.repository;

import com.example.backend.entity.EmployeeSkill;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeSkillRepository extends MongoRepository<EmployeeSkill, String> {
    List<EmployeeSkill> findByEmployeeId(String employeeId);
    List<EmployeeSkill> findByEmployeeIdIn(Collection<String> employeeIds);
}
