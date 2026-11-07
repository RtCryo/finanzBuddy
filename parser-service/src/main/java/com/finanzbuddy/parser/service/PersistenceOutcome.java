package com.finanzbuddy.parser.service;

/** How many parsed transactions were saved and how many were skipped as duplicates. */
public record PersistenceOutcome(int saved, int skipped) {}
