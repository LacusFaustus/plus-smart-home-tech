package ru.yandex.practicum.telemetry.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.telemetry.analyzer.model.entity.Condition;

@Repository
public interface ConditionRepository extends JpaRepository<Condition, Long> {
}