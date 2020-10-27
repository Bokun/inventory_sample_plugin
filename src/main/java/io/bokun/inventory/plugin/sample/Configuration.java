package io.bokun.inventory.plugin.sample;


/**
 * Holder of configuration parameter values.
 */
public final class Configuration {

    static final String VIATOR_API_SCHEME = "VIATOR_API_SCHEME";
    static final String VIATOR_API_HOST = "VIATOR_API_HOST";
    static final String VIATOR_API_PORT = "VIATOR_API_PORT";
    static final String VIATOR_API_PATH = "VIATOR_API_PATH";
    static final String VIATOR_API_USERNAME = "VIATOR_API_USERNAME";
    static final String VIATOR_API_PASSWORD = "VIATOR_API_PASSWORD";

    String scheme;
    String host;
    int port;
    String apiPath;
    String username;
    String password;

    private static void setParameterValue(String parameterName, String parameterValue, Configuration configuration) {
        switch (parameterName) {
            case VIATOR_API_SCHEME: configuration.scheme = parameterValue; break;
            case VIATOR_API_HOST: configuration.host = parameterValue; break;
            case VIATOR_API_PORT: configuration.port = Integer.parseInt(parameterValue); break;
            case VIATOR_API_PATH: configuration.apiPath = parameterValue; break;
            case VIATOR_API_USERNAME: configuration.username = parameterValue; break;
            case VIATOR_API_PASSWORD: configuration.password = parameterValue; break;
            default: break;
        }
    }

    public static Configuration fromRestParameters(Iterable<io.bokun.inventory.plugin.api.rest.PluginConfigurationParameterValue> configParameters) {
        Configuration configuration = new Configuration();
        for (io.bokun.inventory.plugin.api.rest.PluginConfigurationParameterValue parameterValue : configParameters) {
            setParameterValue(parameterValue.getName(), parameterValue.getValue(), configuration);
        }
        return configuration;
    }
}
