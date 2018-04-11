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