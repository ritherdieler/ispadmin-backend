package com.dscorp.wispadmin.wispadmin.search.infrastructure.meili

import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.search.api.SearchEngine
import com.dscorp.wispadmin.wispadmin.search.api.SearchIndexer
import com.dscorp.wispadmin.wispadmin.search.application.SubscriptionDocumentMapper
import com.dscorp.wispadmin.wispadmin.search.application.SubscriptionSearchReindexer
import com.meilisearch.sdk.Client
import com.meilisearch.sdk.Config
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
@ConditionalOnProperty(prefix = "search", name = ["provider"], havingValue = "meilisearch", matchIfMissing = true)
class MeiliClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "search", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun meiliClient(
        @org.springframework.beans.factory.annotation.Value("\${search.meili.host:http://localhost:7700}") host: String,
        @org.springframework.beans.factory.annotation.Value("\${search.meili.api-key:}") apiKey: String
    ): Client {
        return Client(Config(host, apiKey))
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "search", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun meiliSearchEngine(
        client: Client,
        @org.springframework.beans.factory.annotation.Value("\${search.index:subscriptions}") indexName: String
    ): SearchEngine {
        return MeiliSearchEngineAdapter(client, indexName)
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "search", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun meiliSearchIndexer(
        client: Client,
        @org.springframework.beans.factory.annotation.Value("\${search.index:subscriptions}") indexName: String
    ): SearchIndexer {
        return MeiliSearchIndexerAdapter(client, indexName)
    }

    @Bean
    @ConditionalOnProperty(prefix = "search", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun meiliIndexBootstrap(
        client: Client,
        @org.springframework.beans.factory.annotation.Value("\${search.index:subscriptions}") indexName: String,
        reindexer: SubscriptionSearchReindexer
    ): MeiliIndexBootstrap {
        return MeiliIndexBootstrap(client, indexName, reindexer)
    }
}
