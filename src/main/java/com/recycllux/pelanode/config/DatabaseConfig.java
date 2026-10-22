package com.recycllux.pelanode.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One switch for the database: PN_DB_URL. H2 (the default, and the tests) runs in PostgreSQL
 * mode so the same SQL works on both; PostgreSQL URLs are used as given.
 */
@Configuration
public class DatabaseConfig {

    static final String H2_OPTIONS = ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

    static String withH2Options(String url) {
        if (url == null || !url.startsWith("jdbc:h2:") || url.toUpperCase().contains(";MODE=")) {
            return url;
        }
        return url + H2_OPTIONS;
    }

    @Bean
    static BeanPostProcessor h2CompatibilityOptions() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof DataSourceProperties props) {
                    props.setUrl(withH2Options(props.getUrl()));
                }
                return bean;
            }
        };
    }
}
