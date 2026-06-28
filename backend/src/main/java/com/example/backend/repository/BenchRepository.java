package com.example.backend.repository;

import com.example.backend.entity.Bench;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BenchRepository extends MongoRepository<Bench, String> {
}
