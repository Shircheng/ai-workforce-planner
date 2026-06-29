package com.example.backend.repository;

import com.example.backend.entity.Bench;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BenchRepository extends MongoRepository<Bench, String> {
    List<Bench> findByEmployeeId(String employeeId);

    List<Bench> findByEmployeeIdIn(Collection<String> employeeIds);
}
