package com.tricycle.gate.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

@Configuration
@MapperScan(basePackages = "com.tricycle.gate.mapper.postgre", annotationClass = PostgreConnMapper.class, sqlSessionFactoryRef = "postgreSqlSessionFactory")
public class PostgreDatabaseConfig {

    @Bean(name = "postgreDataSource", destroyMethod = "close")
    @ConfigurationProperties(prefix = "spring.datasource2")
    public DataSource postgreDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean(name = "postgreSqlSessionFactory")
    public SqlSessionFactory postgreSqlSessionFactory(@Qualifier("postgreDataSource")DataSource postgreDataSource, ApplicationContext applicationContext) throws Exception {
        SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
        sqlSessionFactoryBean.setDataSource(postgreDataSource);
        sqlSessionFactoryBean.setMapperLocations((new PathMatchingResourcePatternResolver().getResources("classpath:/mapper/**/*.xml")));
        return sqlSessionFactoryBean.getObject();
    }

    @Bean(name = "postgreSqlSessionTemplate")
    public SqlSessionTemplate postgreSqlSessionTemplate(SqlSessionFactory postgreSqlSessionFactory) throws Exception {
        return new SqlSessionTemplate((postgreSqlSessionFactory));
    }
}
