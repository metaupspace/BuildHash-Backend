package com.builddash.backend.domain.port;

import java.util.UUID;

/**
 * Statement number allocation (9-E): ST-YYYYMM-#### per company per period, separate
 * from gst_sequences. Callers allocate only inside the finalize transaction after both
 * artifacts are stored, so failed generations never consume a number. Not gapless by design.
 */
public interface StatementSequenceRepository {

    /** Must join an existing transaction (the statement finalize Tx2). */
    String nextNumber(UUID companyId, String periodKey);
}
