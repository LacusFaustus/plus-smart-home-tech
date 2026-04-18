package ru.yandex.practicum.warehouse.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableJpaRepositories(
        basePackages = "ru.yandex.practicum.warehouse.repository",
        entityManagerFactoryRef = "warehouseEntityManagerFactory",
        transactionManagerRef = "warehouseTransactionManager"
)
public class DatabaseConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties warehouseDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource warehouseDataSource() {
        HikariDataSource dataSource = warehouseDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setJdbcUrl("jdbc:h2:mem:warehouse;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;INIT=CREATE SCHEMA IF NOT EXISTS warehouse_db");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setDriverClassName("org.h2.Driver");
        return dataSource;
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean warehouseEntityManagerFactory() {
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(true);
        vendorAdapter.setShowSql(true);

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(warehouseDataSource());
        factory.setPackagesToScan("ru.yandex.practicum.warehouse.model");
        factory.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> jpaProperties = new HashMap<>();
        jpaProperties.put("hibernate.hbm2ddl.auto", "create-drop");
        jpaProperties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        jpaProperties.put("hibernate.show_sql", "true");
        factory.setJpaPropertyMap(jpaProperties);

        return factory;
    }

    @Bean
    @Primary
    public PlatformTransactionManager warehouseTransactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(warehouseEntityManagerFactory().getObject());
        return transactionManager;
    }
}