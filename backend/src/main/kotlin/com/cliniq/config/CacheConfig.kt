package com.cliniq.config

import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig {
    companion object {
        const val ANALYTICS_DASHBOARD = "analytics:dashboard"
        const val CLINIC_INFO         = "clinic:info"
        const val DOCTOR_LIST         = "clinic:doctors"
        const val DRUG_STOCK          = "pharmacy:stock"
    }

    @Bean
    fun cacheManager(factory: RedisConnectionFactory): RedisCacheManager {
        val json = GenericJackson2JsonRedisSerializer()
        val base = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(json))
            .disableCachingNullValues()

        return RedisCacheManager.builder(factory)
            .cacheDefaults(base)
            .withCacheConfiguration(ANALYTICS_DASHBOARD, base.entryTtl(Duration.ofMinutes(5)))
            .withCacheConfiguration(CLINIC_INFO,         base.entryTtl(Duration.ofHours(1)))
            .withCacheConfiguration(DOCTOR_LIST,         base.entryTtl(Duration.ofMinutes(30)))
            .withCacheConfiguration(DRUG_STOCK,          base.entryTtl(Duration.ofMinutes(2)))
            .build()
    }
}
