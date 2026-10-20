package br.bundled.money.business._0_domain.model;

public interface Auditable {

    /** Timestamp in millis since epoch */
    long createdAt();

    /** Timestamp in millis since epoch */
    long updatedAt();

}
