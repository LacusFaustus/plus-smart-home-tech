-- Создание таблиц для Analyzer

CREATE TABLE IF NOT EXISTS scenarios (
                                         id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                         hub_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    UNIQUE(hub_id, name)
    );

CREATE TABLE IF NOT EXISTS sensors (
                                       id VARCHAR(255) PRIMARY KEY,
    hub_id VARCHAR(255) NOT NULL
    );

CREATE TABLE IF NOT EXISTS conditions (
                                          id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                          type VARCHAR(50) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    value INTEGER
    );

CREATE TABLE IF NOT EXISTS actions (
                                       id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                       type VARCHAR(50) NOT NULL,
    value INTEGER
    );

CREATE TABLE IF NOT EXISTS scenario_conditions (
                                                   scenario_id BIGINT REFERENCES scenarios(id) ON DELETE CASCADE,
    sensor_id VARCHAR(255) REFERENCES sensors(id) ON DELETE CASCADE,
    condition_id BIGINT REFERENCES conditions(id) ON DELETE CASCADE,
    PRIMARY KEY (scenario_id, sensor_id, condition_id)
    );

CREATE TABLE IF NOT EXISTS scenario_actions (
                                                scenario_id BIGINT REFERENCES scenarios(id) ON DELETE CASCADE,
    sensor_id VARCHAR(255) REFERENCES sensors(id) ON DELETE CASCADE,
    action_id BIGINT REFERENCES actions(id) ON DELETE CASCADE,
    PRIMARY KEY (scenario_id, sensor_id, action_id)
    );