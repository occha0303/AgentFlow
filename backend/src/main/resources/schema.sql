CREATE TABLE IF NOT EXISTS agent_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_run (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    error_message VARCHAR(500),
    created_at DATETIME NOT NULL,
    started_at DATETIME,
    finished_at DATETIME,
    INDEX idx_agent_run_task_id (task_id)
);

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
