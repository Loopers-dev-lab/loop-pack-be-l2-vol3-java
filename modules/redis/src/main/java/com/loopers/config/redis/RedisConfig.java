package com.loopers.config.redis;


import io.lettuce.core.ReadFrom;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisStaticMasterReplicaConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

@Configuration
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfig{
    private static final String CONNECTION_MASTER = "redisConnectionMaster";
    public static final String REDIS_TEMPLATE_MASTER = "redisTemplateMaster";

    private final RedisProperties redisProperties;

    public RedisConfig(RedisProperties redisProperties){
        this.redisProperties = redisProperties;
    }

    @Primary
    @Bean
    public LettuceConnectionFactory defaultRedisConnectionFactory() {
        if (redisProperties.isCluster()) {
            return clusterConnectionFactory(b -> b.readFrom(ReadFrom.REPLICA_PREFERRED));
        }
        int database = redisProperties.database();
        RedisNodeInfo master = redisProperties.master();
        List<RedisNodeInfo> replicas = redisProperties.replicas();
        return lettuceConnectionFactory(
                database, master, replicas,
                b -> b.readFrom(ReadFrom.REPLICA_PREFERRED)
        );
    }

    @Qualifier(CONNECTION_MASTER)
    @Bean
    public LettuceConnectionFactory masterRedisConnectionFactory() {
        if (redisProperties.isCluster()) {
            return clusterConnectionFactory(b -> b.readFrom(ReadFrom.MASTER));
        }
        int database = redisProperties.database();
        RedisNodeInfo master = redisProperties.master();
        List<RedisNodeInfo> replicas = redisProperties.replicas();
        return lettuceConnectionFactory(
                database, master, replicas,
                b -> b.readFrom(ReadFrom.MASTER)
        );
    }

    @Primary
    @Bean
    public RedisTemplate<String, String> defaultRedisTemplate(LettuceConnectionFactory lettuceConnectionFactory) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        return defaultRedisTemplate(redisTemplate, lettuceConnectionFactory);
    }

    @Qualifier(REDIS_TEMPLATE_MASTER)
    @Bean
    public RedisTemplate<String, String> masterRedisTemplate(
            @Qualifier(CONNECTION_MASTER) LettuceConnectionFactory lettuceConnectionFactory
    ) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        return defaultRedisTemplate(redisTemplate, lettuceConnectionFactory);
    }

    private LettuceConnectionFactory clusterConnectionFactory(
            Consumer<LettuceClientConfiguration.LettuceClientConfigurationBuilder> customizer
    ) {
        List<RedisNodeInfo> clusterNodes = redisProperties.clusterNodes();
        List<String> nodes = clusterNodes.stream()
                .map(n -> n.host() + ":" + n.port())
                .toList();
        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(nodes);
        clusterConfig.setMaxRedirects(3);

        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2));
        if (customizer != null) customizer.accept(builder);
        LettuceClientConfiguration clientConfig = builder.build();

        return new LettuceConnectionFactory(clusterConfig, clientConfig);
    }

    private LettuceConnectionFactory lettuceConnectionFactory(
            int database,
            RedisNodeInfo master,
            List<RedisNodeInfo> replicas,
            Consumer<LettuceClientConfiguration.LettuceClientConfigurationBuilder> customizer
    ){
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2));
        if(customizer != null) customizer.accept(builder);
        LettuceClientConfiguration clientConfig = builder.build();
        RedisStaticMasterReplicaConfiguration masterReplicaConfig = new RedisStaticMasterReplicaConfiguration(master.host(), master.port());
        masterReplicaConfig.setDatabase(database);
        for(RedisNodeInfo r : replicas){
            masterReplicaConfig.addNode(r.host(), r.port());
        }
        return new LettuceConnectionFactory(masterReplicaConfig, clientConfig);
    }

    private <K,V> RedisTemplate<K,V> defaultRedisTemplate(
            RedisTemplate<K,V> template,
            LettuceConnectionFactory connectionFactory
    ){
        StringRedisSerializer s = new StringRedisSerializer();
        template.setKeySerializer(s);
        template.setValueSerializer(s);
        template.setHashKeySerializer(s);
        template.setHashValueSerializer(s);
        template.setConnectionFactory(connectionFactory);
        return template;
    }
}
