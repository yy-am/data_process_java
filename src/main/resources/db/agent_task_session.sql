CREATE TABLE IF NOT EXISTS agent_task_session (
    session_id VARCHAR(128) PRIMARY KEY,
    source_doc_id VARCHAR(256),
    result_doc_id VARCHAR(256),
    parsed_file_ref VARCHAR(256),
    stage VARCHAR(64) NOT NULL,
    state_json JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_task_session_source_doc_id
    ON agent_task_session (source_doc_id);

CREATE INDEX IF NOT EXISTS idx_agent_task_session_result_doc_id
    ON agent_task_session (result_doc_id);

CREATE INDEX IF NOT EXISTS idx_agent_task_session_stage
    ON agent_task_session (stage);
