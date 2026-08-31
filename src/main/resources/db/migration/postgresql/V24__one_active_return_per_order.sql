-- H1 hardening (audit Phase 8.1): the service-level guard is check-then-act, so two
-- concurrent createReturn calls could both observe "no return yet" and insert two active
-- rows — each independently triggering refund processing, and breaking the
-- Optional-returning findByOrderId read itself. Same partial-unique shape as
-- uq_delete_requests_pending_user (V23): every non-terminal status blocks; REJECTED is
-- the one re-entry door, so rejected rows stay excluded and resubmission stays legal.
CREATE UNIQUE INDEX uq_returns_one_active_per_order
    ON returns (order_id)
    WHERE status <> 'REJECTED';
