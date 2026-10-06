package com.parvez.kpi.persistence;

@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "kpi_sync_state")
public class SyncState {
    @jakarta.persistence.Id
    private Integer id;
    @jakarta.persistence.Column(name = "active_generation")
    private java.util.UUID generation;
    @jakarta.persistence.Column(name = "data_as_of")
    private java.time.Instant dataAsOf;
    @jakarta.persistence.Column(name = "last_successful_sync_at")
    private java.time.Instant lastSuccessfulSyncAt;
    @jakarta.persistence.Column(name = "lease_owner")
    private java.util.UUID owner;
    @jakarta.persistence.Column(name = "lease_until")
    private java.time.Instant until;

    protected SyncState() {
    }
}
