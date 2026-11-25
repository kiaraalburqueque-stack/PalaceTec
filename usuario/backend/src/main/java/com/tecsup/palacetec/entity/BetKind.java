package com.tecsup.palacetec.entity;

public enum BetKind {

    STRAIGHT_UP("Pleno"), COLOR("Color"), PARITY("Paridad"), DOZEN("Docena"), COLUMN("Columna");

    private final String label;

    BetKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
