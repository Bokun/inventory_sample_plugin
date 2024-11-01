load('../bokun_platform/tilt/utils/Tiltfile', 'dotenv', 'zsh', 'run_no_print', 'localhost_domain', 'run_mysql', 'sdkman_init', 'kill_port')

serve_env = dotenv({
    'SAMPLE_PLUGIN_PORT': '18080',
})

local_resource(
    'inventory_sample_plugin',
    serve_cmd=zsh(sdkman_init + ' && ./gradlew run --args="-rest"'),
    labels=['inventory-service'],
    allow_parallel=True,
    resource_deps=['mysql-ready'],
    deps=['src', 'settings.gradle'],
    serve_env=serve_env,
    readiness_probe=probe(
        period_secs=15,
        http_get=http_get_action(port=18080, path="/monitor")
    ),
    links=['http://' + localhost_domain + ':18080'],
)
