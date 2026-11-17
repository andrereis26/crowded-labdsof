package com.crowded.core.ticket.infrastructure.persistence.entity;

import com.crowded.core.ticket.domain.model.ImportMethod;
import com.crowded.core.ticket.domain.model.MatchMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Row of {@code ticket}, which shares its id with the participant's row of {@code
 * event_connection}. The entry code is stored encrypted. A ticket is only ever added, so a new one
 * is inserted without reading it first.
 */
@Entity
@Table(name = "ticket")
public class TicketEntity implements Persistable<UUID> {

  @Id private UUID id;

  @Transient private boolean stored;

  private String gate;

  private String sector;

  private String seat;

  @Column(name = "entry_code", nullable = false)
  private byte[] entryCode;

  @Column(name = "entry_code_hash", nullable = false)
  private String entryCodeHash;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "import_method", columnDefinition = "import_method", nullable = false)
  private ImportMethod importMethod;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "match_method", columnDefinition = "match_method", nullable = false)
  private MatchMethod matchMethod;

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return !stored;
  }

  @PostLoad
  @PostPersist
  void markStored() {
    stored = true;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getGate() {
    return gate;
  }

  public void setGate(String gate) {
    this.gate = gate;
  }

  public String getSector() {
    return sector;
  }

  public void setSector(String sector) {
    this.sector = sector;
  }

  public String getSeat() {
    return seat;
  }

  public void setSeat(String seat) {
    this.seat = seat;
  }

  public byte[] getEntryCode() {
    return entryCode == null ? null : entryCode.clone();
  }

  public void setEntryCode(byte[] entryCode) {
    this.entryCode = entryCode == null ? null : entryCode.clone();
  }

  public String getEntryCodeHash() {
    return entryCodeHash;
  }

  public void setEntryCodeHash(String entryCodeHash) {
    this.entryCodeHash = entryCodeHash;
  }

  public ImportMethod getImportMethod() {
    return importMethod;
  }

  public void setImportMethod(ImportMethod importMethod) {
    this.importMethod = importMethod;
  }

  public MatchMethod getMatchMethod() {
    return matchMethod;
  }

  public void setMatchMethod(MatchMethod matchMethod) {
    this.matchMethod = matchMethod;
  }
}
