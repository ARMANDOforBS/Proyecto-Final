package com.utp.proyectoFinal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.utp.proyectoFinal.model.Test;
import com.utp.proyectoFinal.model.TestType;

@Repository
public interface TestRepository extends JpaRepository<Test, Long> {
    List<Test> findByTestType(TestType testType);
    List<Test> findByIsActiveTrue();
    List<Test> findByDifficultyLevelLessThanEqual(Integer difficultyLevel);
}
