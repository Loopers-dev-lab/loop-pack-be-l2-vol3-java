package com.loopers.utils;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

@Component
public class DatabaseCleanUp implements InitializingBean {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Autowired
    private DataSource dataSource;

    private final List<String> tableNames = new ArrayList<>();

    @Override
    public void afterPropertiesSet() {
        entityManager.getMetamodel().getEntities().stream()
            .filter(entity -> entity.getJavaType().getAnnotation(Entity.class) != null)
            .map(entity -> entity.getJavaType().getAnnotation(Table.class).name())
            .forEach(tableNames::add);
    }

    @Transactional
    public void truncateAllTables() {
        entityManager.flush();
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();

        for (String table : tableNames) {
            entityManager.createNativeQuery("TRUNCATE TABLE `" + table + "`").executeUpdate();
        }

        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();

        if (cacheManager != null) {
            cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        }

        try (java.sql.Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            conn.createStatement().execute("DELETE FROM shedlock");
        } catch (Exception ignored) {
        }
    }
}
