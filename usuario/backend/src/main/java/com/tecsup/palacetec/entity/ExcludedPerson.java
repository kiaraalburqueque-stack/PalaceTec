package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Registro de excluidos: solo guarda el HMAC del DNI. */
@Entity
@Immutable
@Table(name = "responsible_excludedperson")
public class ExcludedPerson {

    @Id
    private Long id;

    @Column(name = "dni_hash", nullable = false, length = 64)
    private String dniHash;

    protected ExcludedPerson() {
    }
}
