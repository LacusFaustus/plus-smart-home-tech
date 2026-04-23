-- Датчики (устройства), зарегистрированные в хабах
CREATE TABLE IF NOT EXISTS sensors (
                                       id VARCHAR(255) PRIMARY KEY,
    hub_id VARCHAR(255) NOT NULL
    );

-- Сценарии умного дома
CREATE TABLE IF NOT EXISTS scenarios (
                                         id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                         hub_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    UNIQUE(hub_id, name)
    );

-- Условия активации сценариев
CREATE TABLE IF NOT EXISTS conditions (
                                          id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                          type VARCHAR(50) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    value INTEGER
    );

-- Действия, выполняемые при активации сценария
CREATE TABLE IF NOT EXISTS actions (
                                       id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                       type VARCHAR(50) NOT NULL,
    value INTEGER
    );

-- Связь сценариев с условиями и датчиками
CREATE TABLE IF NOT EXISTS scenario_conditions (
                                                   scenario_id BIGINT REFERENCES scenarios(id) ON DELETE CASCADE,
    sensor_id VARCHAR(255) REFERENCES sensors(id) ON DELETE CASCADE,
    condition_id BIGINT REFERENCES conditions(id) ON DELETE CASCADE,
    PRIMARY KEY (scenario_id, sensor_id, condition_id)
    );

-- Связь сценариев с действиями и датчиками
CREATE TABLE IF NOT EXISTS scenario_actions (
                                                scenario_id BIGINT REFERENCES scenarios(id) ON DELETE CASCADE,
    sensor_id VARCHAR(255) REFERENCES sensors(id) ON DELETE CASCADE,
    action_id BIGINT REFERENCES actions(id) ON DELETE CASCADE,
    PRIMARY KEY (scenario_id, sensor_id, action_id)
    );

-- Удаляем старую функцию, если существует
DROP FUNCTION IF EXISTS check_hub_id() CASCADE;

-- Функция для проверки соответствия hub_id у сценария и датчика
CREATE OR REPLACE FUNCTION check_hub_id()
RETURNS TRIGGER AS $$
BEGIN
    IF (SELECT hub_id FROM scenarios WHERE id = NEW.scenario_id) !=
       (SELECT hub_id FROM sensors WHERE id = NEW.sensor_id) THEN
        RAISE EXCEPTION 'Hub IDs do not match for scenario_id % and sensor_id %',
            NEW.scenario_id, NEW.sensor_id;
END IF;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Триггер для проверки hub_id при вставке в scenario_conditions
DROP TRIGGER IF EXISTS tr_bi_scenario_conditions_hub_id_check ON scenario_conditions;
CREATE TRIGGER tr_bi_scenario_conditions_hub_id_check
    BEFORE INSERT ON scenario_conditions
    FOR EACH ROW
    EXECUTE FUNCTION check_hub_id();

-- Триггер для проверки hub_id при вставке в scenario_actions
DROP TRIGGER IF EXISTS tr_bi_scenario_actions_hub_id_check ON scenario_actions;
CREATE TRIGGER tr_bi_scenario_actions_hub_id_check
    BEFORE INSERT ON scenario_actions
    FOR EACH ROW
    EXECUTE FUNCTION check_hub_id();