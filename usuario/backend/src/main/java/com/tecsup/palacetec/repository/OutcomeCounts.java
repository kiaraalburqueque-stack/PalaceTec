package com.tecsup.palacetec.repository;

/** Apuestas con resultado, según si pagaron más, menos o lo mismo que lo apostado. */
public interface OutcomeCounts {

    long getWon();

    long getLost();

    long getPush();
}
