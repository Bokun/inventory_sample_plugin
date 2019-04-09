package io.bokun.inventory.plugin.sample;

import io.bokun.inventory.common.api.grpc.*;

/**
 * Holder of configuration parameter values.
 */
public final class Configuration {

    static final String SAMPLE_API_SCHEME = "SAMPLE_API_SCHEME";
    static final String SAMPLE_API_HOST = "SAMPLE_API_HOST";
    static final String SAMPLE_API_PORT = "SAMPLE_API_PORT";
    static final String SAMPLE_API_PATH = "SAMPLE_API_PATH";
    static final String SAMPLE_API_USERNAME = "SAMPLE_API_USERNAME";
    static final String SAMPLE_API_PASSWORD = "SAMPLE_API_PASSWORD";

    String scheme;
    String host;
    int port;
    String apiPath;
    String username;
    String password;

    private static void setParameterValue(String parameterName, String parameterValue, Configuration configuration) {
        switch (parameterName) {
            case SAMPLE_API_SCHEME: configuration.scheme = parameterValue; break;
            case SAMPLE_API_HOST: configuration.host = parameterValue; break;
            case SAMPLE_API_PORT: configuration.port = Integer.parseInt(parameterValue); break;
            case SAMPLE_API_PATH: configuration.apiPath = parameterValue; break;
            case SAMPLE_API_USERNAME: configuration.username = parameterValue; break;
            case SAMPLE_API_PASSWORD: configuration.password = parameterValue; break;
        }
    }

    public static Configuration fromGrpcParameters(Iterable<io.bokun.inventory.common.api.grpc.PluginConfigurationParameterValue> configParameters) {
        Configuration configuration = new Configuration();
        for (PluginConfigurationParameterValue parameterValue : configParameters) {
            setParameterValue(parameterValue.getName(), parameterValue.getValue(), configuration);
        }
        return configuration;
    }

    public static Configuration fromRestParameters(Iterable<io.bokun.inventory.plugin.api.rest.PluginConfigurationParameterValue> configParameters) {
        Configuration configuration = new Configuration();
        for (io.bokun.inventory.plugin.api.rest.PluginConfigurationParameterValue parameterValue : configParameters) {
            setParameterValue(parameterValue.getName(), parameterValue.getValue(), configuration);
        }
        return configuration;
    }
}
