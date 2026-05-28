package com.example.dataprocess.agent.model;

import java.util.List;

/**
 * Agent-inferred binding plan for all source-dependent rules.
 */
public record FieldBindingPlan(
        List<FieldBindingItem> items
) {
}
