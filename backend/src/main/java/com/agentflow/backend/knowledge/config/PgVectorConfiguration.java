package com.agentflow.backend.knowledge.config;

import javax.sql.DataSource;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class PgVectorConfiguration {

	@Bean(name = "mysqlDataSourceProperties")
	@Primary
	@ConfigurationProperties("spring.datasource")
	DataSourceProperties mysqlDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean(name = "dataSource")
	@Primary
	DataSource mysqlDataSource(@Qualifier("mysqlDataSourceProperties") DataSourceProperties properties) {
		return properties.initializeDataSourceBuilder().build();
	}

	@Bean(name = "ragDataSourceProperties")
	@ConfigurationProperties("rag.datasource")
	DataSourceProperties ragDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean(name = "ragDataSource")
	DataSource ragDataSource(@Qualifier("ragDataSourceProperties") DataSourceProperties properties) {
		return properties.initializeDataSourceBuilder().build();
	}

	@Bean(name = "ragJdbcTemplate")
	JdbcTemplate ragJdbcTemplate(@Qualifier("ragDataSource") DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	@Bean
	VectorStore knowledgeVectorStore(@Qualifier("ragJdbcTemplate") JdbcTemplate jdbcTemplate,
			EmbeddingModel embeddingModel) {
		return PgVectorStore.builder(jdbcTemplate, embeddingModel)
				.initializeSchema(true)
				.build();
	}
}
