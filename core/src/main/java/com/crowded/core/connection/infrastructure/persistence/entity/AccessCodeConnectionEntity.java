package com.crowded.core.connection.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Row of {@code access_code_connection}: a connection to a public event by its access code. */
@Entity
@Table(name = "access_code_connection")
public class AccessCodeConnectionEntity extends EventConnectionEntity {}
