package com.example.backend.repository;

import com.example.backend.entity.Profile;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfileRepository extends MongoRepository<Profile, String> {
    Optional<Profile> findByEmployeeId(String employeeId);
}
