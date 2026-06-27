package com.example.backend.repository;

import com.example.backend.entity.SkillCatalog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SkillCatalogRepository extends MongoRepository<SkillCatalog, String> {
}
