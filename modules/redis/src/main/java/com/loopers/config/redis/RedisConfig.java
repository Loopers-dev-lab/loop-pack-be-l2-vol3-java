package com.loopers.config.redis;


import io.lettuce.core.ReadFrom;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisStaticMasterReplicaConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

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


    /**
     * replica가 master와 동일한 주소이거나 없으면 Standalone으로 fallback한다.
     * Lettuce의 StaticMasterReplicaTopologyProvider는 master와 replica가 같은 주소일 때
     * 토폴로지 검증에 실패하므로, 단일 노드 환경(로컬/테스트)에서는 Standalone이 적합하다.
     */
    private LettuceConnectionFactory lettuceConnectionFactory(
            int database,
            RedisNodeInfo master,
            List<RedisNodeInfo> replicas,
            Consumer<LettuceClientConfiguration.LettuceClientConfigurationBuilder> customizer
    ){
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder();
        if(customizer != null) customizer.accept(builder);
        LettuceClientConfiguration clientConfig = builder.build();

        List<RedisNodeInfo> distinctReplicas = (replicas == null) ? List.of() : replicas.stream()
                .filter(r -> !isSameNode(r, master))
                .toList();

        if (distinctReplicas.isEmpty()) {
            RedisStandaloneConfiguration standaloneConfig =
                    new RedisStandaloneConfiguration(master.host(), master.port());
            standaloneConfig.setDatabase(database);
            return new LettuceConnectionFactory(standaloneConfig, clientConfig);
        }

        RedisStaticMasterReplicaConfiguration masterReplicaConfig =
                new RedisStaticMasterReplicaConfiguration(master.host(), master.port());
        masterReplicaConfig.setDatabase(database);
        for (RedisNodeInfo r : distinctReplicas) {
            masterReplicaConfig.addNode(r.host(), r.port());
        }
        return new LettuceConnectionFactory(masterReplicaConfig, clientConfig);
    }

    private boolean isSameNode(RedisNodeInfo a, RedisNodeInfo b) {
        return a.host().equals(b.host()) && a.port() == b.port();
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
