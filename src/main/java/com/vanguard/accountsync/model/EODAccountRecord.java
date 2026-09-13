package com.vanguard.accountsync.model;

import com.vanguard.accountsync.util.PKGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Represents an account record as returned by the DB2 EOD stored procedure (EOD SP).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EODAccountRecord {

    private String vastAccountNumber;
    private String portId;
    private LocalDate createdDate;

    public String getPk() {
        return PKGenerator.generate(vastAccountNumber, portId, createdDate);
    }
}
