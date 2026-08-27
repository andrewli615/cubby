package com.cubby.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cubby.backend.model.Document;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    
}