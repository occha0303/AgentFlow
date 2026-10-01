CREATE TABLE IF NOT EXISTS agent_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_run (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    result_text TEXT,
    error_message VARCHAR(500),
    created_at DATETIME NOT NULL,
    started_at DATETIME,
    finished_at DATETIME,
    heartbeat_at DATETIME,
    retry_of_run_id BIGINT,
    INDEX idx_agent_run_task_id (task_id)
);

SET @agentflow_ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE agent_run ADD COLUMN result_text TEXT AFTER status', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'agent_run' AND column_name = 'result_text');
PREPARE agentflow_stmt FROM @agentflow_ddl;
EXECUTE agentflow_stmt;
DEALLOCATE PREPARE agentflow_stmt;

SET @agentflow_ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE agent_run ADD COLUMN heartbeat_at DATETIME', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'agent_run' AND column_name = 'heartbeat_at');
PREPARE agentflow_stmt FROM @agentflow_ddl;
EXECUTE agentflow_stmt;
DEALLOCATE PREPARE agentflow_stmt;

SET @agentflow_ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE agent_run ADD COLUMN retry_of_run_id BIGINT', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'agent_run' AND column_name = 'retry_of_run_id');
PREPARE agentflow_stmt FROM @agentflow_ddl;
EXECUTE agentflow_stmt;
DEALLOCATE PREPARE agentflow_stmt;

CREATE TABLE IF NOT EXISTS agent_step (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    step_order INT NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    input_summary VARCHAR(500),
    output_summary VARCHAR(500),
    error_message VARCHAR(500),
    created_at DATETIME NOT NULL,
    started_at DATETIME,
    finished_at DATETIME,
    INDEX idx_agent_step_run_id (run_id)
);

CREATE TABLE IF NOT EXISTS agent_approval (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    step_id BIGINT NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    action_summary VARCHAR(500) NOT NULL,
    action_payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL,
    decided_at DATETIME,
    decision_reason VARCHAR(500),
    INDEX idx_agent_approval_run_id (run_id),
    INDEX idx_agent_approval_step_id (step_id)
);
