package com.vanguard.accountsync.repository;

import com.vanguard.accountsync.model.DynamoDBRecord;
import com.vanguard.accountsync.model.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Local (H2) mirror of the NewT DynamoDB "account transfers" table, accessed via JPA.
 */
@Repository
public interface DynamoDBRepository extends JpaRepository<DynamoDBRecord, Long> {

    Optional<DynamoDBRecord> findByPk(String pk);

    Optional<DynamoDBRecord> findFirstByVastAccountNumberAndPortId(String vastAccountNumber, String portId);

    List<DynamoDBRecord> findByReconciliationDate(LocalDate reconciliationDate);

    List<DynamoDBRecord> findByReconciliationDateAndStatus(LocalDate reconciliationDate, MatchStatus status);

    boolean existsByReconciliationDate(LocalDate reconciliationDate);
}
