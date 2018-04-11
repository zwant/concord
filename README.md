Concord
------

A config-management framework built on top of typesafe's Config, in Kotlin.

Uses a convention for configuration with a machine-local config file which defines which environment-configuration is allowed
when running the application on that machine.
This is useful to protect against accidentally running with production configuration when testing or when developing.

Loads the following files (lower in list takes precedence over higher in list):
1. Base Config (defaults to resources file "base.conf")
2. Environment config (defaults to resources file "environments.conf")
3. Local config (will first default to look in /etc/local.conf, and if not found will fall-back to resources file "local.conf")

All configuration files are written in HOCON syntax.

Selecting the current environment
--------
The current environment config you want to load is defined by an env variable, the name of which is defined by the params sent to the `Configurator` constructor.

For example:
```
val loader = Configurator(EnvironmentConfiguration("EXAMPLE_APP"))
val config = loader.load()
```
will loook for an env var called `EXAMPLE_APP_ENVIRONMENT`. It will then check if that envioronment is allowed, according to the setting in the local config file (see below for details). If the selected environment isn't allowed by the local config, the library will throw a `NotAllowedEnvironmentConfigurationException` on loading the configuration:
```
Exception in thread "main" se.paldan.concord.exceptions.NotAllowedEnvironmentConfigurationException: The selected environment [test] is not allowed here. Allowed are: [dev]
	at se.paldan.concord.Configurator.load(Configurator.kt:60)
	at se.paldan.concord.example.ExampleAppKt.main(ExampleApp.kt:8)
```
this prevents unexpected problems arising from for example an env-var being inherited through SSH sessions etc.

The currently selected environment is available in the configuration as `current-environment`. This config can be used for example to safe-guard test-suites to only be able to run in a certain environment, or for example a db-cleanup script not allowing to be run in production.
 
Base Config
--------
Defines configuration that is common across all environments. This could be for example which plugins to run or business-logic configuration.

Environment Config
--------
Defines environment-specific configuration that could override base configuration.
Typical examples are for example db-connection strings or memory settings.
Example:
```
dev = {
  db-connection-string: "my-url"
}

test = {
  db-connection-string: "my-other-url"
}
```

Only the environment configuration for the currently selected environment will be loaded. This is to safeguard against
accidentally using another environment's config in code.

Local Config
-------
Defines machine-local configuration and this file could be provisioned by for example Chef, Puppet or Salt.
Should contain a config `allowed-environments` which should be a list of what environment configurations are allowed on this machine.
Example:
`allowed-environments: [dev, unit-test]`
