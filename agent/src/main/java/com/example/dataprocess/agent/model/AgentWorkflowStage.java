package com.example.dataprocess.agent.model;

/**
 * Agent workflow stages up to and including user confirmation.
 */
public enum AgentWorkflowStage {
    RECEIVED,
    TEMPLATE_RECOGNIZED,
    CONFIRMATION_ANALYZED,
    USER_CONFIRMATION_REQUIRED,
    USER_CONFIRMED,
    FAILED,
    COMPLETED
}
