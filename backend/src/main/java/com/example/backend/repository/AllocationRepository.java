package com.example.backend.repository;

import com.example.backend.entity.Allocation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AllocationRepository extends MongoRepository<Allocation, String> {
}
