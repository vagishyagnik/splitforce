package com.splitforce.config;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

@Configuration
@EnableConfigurationProperties(DynamoDbProperties.class)
public class DynamoDbConfig {

    @Bean(destroyMethod = "close")
    public DynamoDbClient dynamoDbClient(DynamoDbProperties props) {
        var builder = DynamoDbClient.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .region(Region.of(props.getRegion()));
        if (props.getEndpoint() != null && !props.getEndpoint().isBlank()) {
            // DynamoDB Local accepts any static credentials.
            var creds = AwsBasicCredentials.create(orLocal(props.getAccessKey()), orLocal(props.getSecretKey()));
            builder.endpointOverride(URI.create(props.getEndpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(creds));
        } else {
            // Real AWS: use the execution role (Lambda) or the local AWS profile.
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    private static String orLocal(String value) {
        return value == null || value.isBlank() ? "local" : value;
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient client) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
    }
}
