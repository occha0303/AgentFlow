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
