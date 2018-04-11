package se.paldan.concord

import com.typesafe.config.*
import se.paldan.concord.exceptions.InvalidConfigurationException
import se.paldan.concord.exceptions.InvalidEnvironmentConfigurationException
import se.paldan.concord.exceptions.NotAllowedEnvironmentConfigurationException
import java.io.FileNotFoundException

open class EnvironmentConfiguration(envVarPrefix: String) {
    private val envVarSuffix = "ENVIRONMENT"
    val environmentVarName = "${envVarPrefix}_$envVarSuffix"

    fun environmentName(): String? {
        return getEnvVar(environmentVarName)
    }

    open fun getEnvVar(name: String): String? {
        return System.getenv(name)
    }
}

class Configurator(private val environment: EnvironmentConfiguration,
                   private val localConfigLoader: ConfigLoader = FileSystemFileLoader("/etc/local.conf"),
                   private val fallbackLocalConfigLoader: ConfigLoader = ResourceFileLoader("local"),
                   private val baseConfigLoader: ConfigLoader = ResourceFileLoader("base"),
                   private val environmentConfigLoader: ConfigLoader = ResourceFileLoader("environments")) {
    companion object {
        private const val ALLOWED_ENVIRONMENTS_CONFIG_NAME = "allowed-environments"
        private const val SELECTED_ENVIRONMENT_CONFIG_NAME = "current-environment"

        internal fun isSelectedEnvironmentAllowed(selectedEnvironment: String, allowedEnvironments: List<String>): Boolean {
            if (allowedEnvironments.contains(selectedEnvironment)) return true

            return false
        }

        internal fun getAllowedEnvironmentsFromConfig(config: Config): List<String> {
            try {
                return config.getStringList(ALLOWED_ENVIRONMENTS_CONFIG_NAME)
            } catch (e: ConfigException.Missing) {
                throw NotAllowedEnvironmentConfigurationException("No allowed environments set in local config. Should be called [$ALLOWED_ENVIRONMENTS_CONFIG_NAME]")
            } catch (e: ConfigException.WrongType) {
                throw NotAllowedEnvironmentConfigurationException("Allowed environments is not a list of strings, it's: ${config.getAnyRef(ALLOWED_ENVIRONMENTS_CONFIG_NAME)}")
            }
        }

        internal fun isSelectedEnvironmentInConfiguration(selectedEnvironment: String, environmentConfiguration: Config): Boolean {
            if (environmentConfiguration.hasPath(selectedEnvironment)) return true
            return false
        }
    }

    fun load(): Config {
        val selectedEnv = getSelectedEnvironmentFromEnvVar()
        val localConfig = loadLocalConfiguration()

        val allowedEnvironments = getAllowedEnvironmentsFromConfig(localConfig)

        if (!isSelectedEnvironmentAllowed(selectedEnv, allowedEnvironments)) {
            throw NotAllowedEnvironmentConfigurationException("The selected environment [$selectedEnv] is not allowed here. Allowed are: $allowedEnvironments")
        }

        val environmentConfig = environmentConfigLoader.load()

        if (!isSelectedEnvironmentInConfiguration(selectedEnv, environmentConfig)) {
            throw InvalidConfigurationException("The selected environment [$selectedEnv] is not configured in the environment config file")
        }

        val selectedEnvironmentConfiguration = extractEnvironmentConfiguration(selectedEnv, environmentConfig)
        val baseConfig = baseConfigLoader.load()
        val selectedEnvironmentValue = ConfigValueFactory.fromAnyRef(selectedEnv)
        // Merge the trees, in this order (lower takes precedence over higher):
        // 1. Base Config
        // 2. Environment config
        // 3. Local config
        return localConfig
                .withValue(SELECTED_ENVIRONMENT_CONFIG_NAME, selectedEnvironmentValue)
                .withFallback(selectedEnvironmentConfiguration.withFallback(baseConfig))
    }

    internal fun getSelectedEnvironmentFromEnvVar(): String {
        return environment.environmentName()
                ?: throw InvalidEnvironmentConfigurationException("No env var by the name [${environment.environmentVarName}] is set")
    }

    private fun extractEnvironmentConfiguration(selectedEnvironment: String, environmentConfiguration: Config): Config {
        return environmentConfiguration.getConfig(selectedEnvironment)
    }

    internal fun loadBaseConfiguration(): Config {
        return baseConfigLoader.load()
    }

    internal fun loadEnvironmentConfiguration(): Config {
        return environmentConfigLoader.load()
    }

    internal fun loadLocalConfiguration(): Config {
        return try {
            localConfigLoader.load()
        } catch (e: FileNotFoundException) {
            fallbackLocalConfigLoader.load()
        }
    }
}