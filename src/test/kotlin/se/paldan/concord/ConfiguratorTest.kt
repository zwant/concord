package se.paldan.concord

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import se.paldan.concord.exceptions.InvalidConfigurationException
import se.paldan.concord.exceptions.InvalidEnvironmentConfigurationException
import se.paldan.concord.exceptions.MissingConfigurationException
import se.paldan.concord.exceptions.NotAllowedEnvironmentConfigurationException
import java.io.FileNotFoundException
import java.util.*

val isNull: Matcher<Any?> = Matcher(Objects::isNull)

class ConfigLoaderFactoryTest : Spek({
    describe("an environment configuration") {
        it("should properly build the environment variable name") {
            val config = EnvironmentConfiguration("TEST")
            val envVarName = config.environmentVarName
            assert.that(envVarName, equalTo("TEST_ENVIRONMENT"))
        }

        on("an environment variable properly set") {
            class MockEnvironmentConfig : EnvironmentConfiguration("TEST") {
                override fun getEnvVar(name: String): String? {
                    assert.that(name, equalTo("TEST_ENVIRONMENT"))
                    return "testing"
                }
            }

            val config = MockEnvironmentConfig()
            val selectedEnv = config.environmentName()

            it("should be able to retrieve the env variable") {
                assert.that(selectedEnv, equalTo("testing"))
            }
        }

        on("no correct environment variable set") {
            class MockEnvironmentConfig : EnvironmentConfiguration("LOL") {
                override fun getEnvVar(name: String): String? {
                    assert.that(name, equalTo("LOL_ENVIRONMENT"))
                    return null
                }
            }

            val config = MockEnvironmentConfig()
            val selectedEnv = config.environmentName()

            it("should return an empty optional if env var is not set") {
                assert.that(selectedEnv, isNull)
            }
        }
    }

    describe("a config loader") {
        class FileNotFoundExceptionConfigLoader : ConfigLoader {
            override fun load(): Config {
                throw FileNotFoundException()
            }
        }

        class MissingConfigurationExceptionConfigLoader : ConfigLoader {
            override fun load(): Config {
                throw MissingConfigurationException("Missing config")
            }
        }

        class SyntaxErrorConfigLoader : ConfigLoader {
            override fun load(): Config {
                throw InvalidConfigurationException("Invalid")
            }
        }

        describe("selected environment configuration") {
            on("no environment variable being set for the current environment") {
                class MockEnvironmentConfig : EnvironmentConfiguration("LOL") {
                    override fun getEnvVar(name: String): String? {
                        assert.that(name, equalTo("LOL_ENVIRONMENT"))
                        return null
                    }
                }

                val loader = Configurator(MockEnvironmentConfig())

                it("should raise an exception") {
                    assert.that({ loader.getSelectedEnvironmentFromEnvVar() },
                            throws<InvalidEnvironmentConfigurationException>())
                }
            }
            on("a valid environment configuration set in env vars") {
                class MockEnvironmentConfig : EnvironmentConfiguration("MY_APP") {
                    override fun getEnvVar(name: String): String? {
                        assert.that(name, equalTo("MY_APP_ENVIRONMENT"))
                        return "test"
                    }
                }

                val loader = Configurator(MockEnvironmentConfig())

                it("should properly return the current environment if set") {
                    assert.that(loader.getSelectedEnvironmentFromEnvVar(), equalTo("test"))
                }
            }
        }

        describe("Allowed environment configuration") {
            on("missing allowed environment configuration") {
                val config = ConfigFactory.parseString("testing = [a, b, c]")

                it("should throw exception if there's no configuration for allowed environments") {
                    assert.that({ Configurator.getAllowedEnvironmentsFromConfig(config) },
                            throws<NotAllowedEnvironmentConfigurationException>())
                }
            }

            on("a list of allowed environments that's not a list of strings") {
                val config = ConfigFactory.parseString("allowed-environments = \"a\"")

                it("should throw an exception") {
                    assert.that({ Configurator.getAllowedEnvironmentsFromConfig(config) },
                            throws<NotAllowedEnvironmentConfigurationException>())
                }
            }

            on("a valid list of allowed environments") {
                val config = ConfigFactory.parseString("allowed-environments = [a, b]")

                val allowedEnvs = Configurator.getAllowedEnvironmentsFromConfig(config)

                it("should return the allowed environments") {
                    assert.that(allowedEnvs,
                            equalTo(listOf("a", "b")))
                }
            }

            on("a selected environment that is not allowed") {
                val isAllowed = Configurator.isSelectedEnvironmentAllowed("test", listOf("a", "b"))
                it("should return false") {
                    assert.that(isAllowed,
                            equalTo(false))
                }
            }

            on("a selected environment that is allowed") {
                val isAllowed = Configurator.isSelectedEnvironmentAllowed("test", listOf("test", "b"))
                it("should return true") {
                    assert.that(isAllowed,
                            equalTo(true))
                }
            }
        }

        describe("loading local configuration") {
            on("missing file and missing fallback config") {
                val loader = Configurator(EnvironmentConfiguration("MY_APP"),
                        localConfigLoader = FileNotFoundExceptionConfigLoader(),
                        fallbackLocalConfigLoader = MissingConfigurationExceptionConfigLoader())
                it("should throw an exception") {
                    assert.that({ loader.loadLocalConfiguration() },
                            throws<MissingConfigurationException>())
                }
            }

            on("missing file but with proper fallback config on classpath") {
                val loader = Configurator(EnvironmentConfiguration("MY_APP"),
                        localConfigLoader = FileNotFoundExceptionConfigLoader(),
                        fallbackLocalConfigLoader = ResourceFileLoader("test-missing-but-with-fallback"))
                val localConfig = loader.loadLocalConfiguration()
                it("should load the fallback") {
                    assert.that(localConfig.getString("test-config"),
                            equalTo("hej"))
                }
            }

            on("missing file but with invalid fallback config on classpath") {
                val loader = Configurator(EnvironmentConfiguration("MY_APP"),
                        localConfigLoader = FileNotFoundExceptionConfigLoader(),
                        fallbackLocalConfigLoader = ResourceFileLoader("test-invalid-file"))
                it("should throw an exception") {
                    assert.that({ loader.loadLocalConfiguration() },
                            throws<InvalidConfigurationException>())
                }
            }

            on("existing file with syntax error") {
                val loader = Configurator(EnvironmentConfiguration("MY_APP"),
                        localConfigLoader = SyntaxErrorConfigLoader())
                it("should throw an exception") {
                    assert.that({ loader.loadLocalConfiguration() },
                            throws<InvalidConfigurationException>())
                }
            }
        }

        describe("loading base configuration") {
            on("missing file") {
                val loader = Configurator(EnvironmentConfiguration("MY_APP"),
                        baseConfigLoader = MissingConfigurationExceptionConfigLoader())
                it("should throw an exception") {
                    assert.that({ loader.loadBaseConfiguration() },
                            throws<MissingConfigurationException>())
                }
            }

            on("existing file with syntax error") {
                val loader = Configurator(EnvironmentConfiguration("MY_APP"),
                        baseConfigLoader = ResourceFileLoader("test-invalid-file"))
                it("should throw an exception") {
                    assert.that({ loader.loadBaseConfiguration() },
                            throws<InvalidConfigurationException>())
                }
            }

            on("existing file with correct syntax") {
                val loader = Configurator(EnvironmentConfiguration("MY_APP"),
                        baseConfigLoader = ResourceFileLoader("test-valid-base-config"))
                it("should properly load the file") {
                    val baseConfig = loader.loadBaseConfiguration()
                    assert.that(baseConfig.getString("test"), equalTo("hej"))
                }
            }
        }

        describe("loading environment configuration") {
            on("missing file") {
                val loader = Configurator(EnvironmentConfiguration("MY_APP"),
                        environmentConfigLoader = MissingConfigurationExceptionConfigLoader())
                it("should throw an exception") {
                    assert.that({ loader.loadEnvironmentConfiguration() },
                            throws<MissingConfigurationException>())
                }
            }

            on("existing file with syntax error") {
                val loader = Configurator(EnvironmentConfiguration("MY_APP"),
                        environmentConfigLoader = ResourceFileLoader("test-invalid-file"))
                it("should throw an exception") {
                    assert.that({ loader.loadEnvironmentConfiguration() },
                            throws<InvalidConfigurationException>())
                }
            }

            on("existing file with correct syntax") {
                val loader = Configurator(EnvironmentConfiguration("MY_APP"),
                        environmentConfigLoader = ResourceFileLoader("test-valid-environment-config"))
                it("should properly load the file") {
                    val baseConfig = loader.loadEnvironmentConfiguration()
                    assert.that(baseConfig.getString("test"), equalTo("hej"))
                }
            }
        }

        describe("selected environment should be configured") {
            val config = ConfigFactory.parseString("test = {first = \"hej\"}")
            on("selected environment not in config") {
                it("should return false") {
                    assert.that(Configurator.isSelectedEnvironmentInConfiguration("meh", config),
                            equalTo(false))
                }
            }

            on("selected environment is in config") {
                it("should return true") {
                    assert.that(Configurator.isSelectedEnvironmentInConfiguration("test", config),
                            equalTo(true))
                }
            }
        }

        describe("merging configurations") {
            class MockEnvironmentConfig : EnvironmentConfiguration("MY_APP") {
                override fun getEnvVar(name: String): String? {
                    assert.that(name, equalTo("MY_APP_ENVIRONMENT"))
                    return "test"
                }
            }

            on("no overlapping configurations") {
                val loader = Configurator(MockEnvironmentConfig(),
                        localConfigLoader = FileNotFoundExceptionConfigLoader(),
                        fallbackLocalConfigLoader = ResourceFileLoader("merge-tests/no-overlap/local"),
                        baseConfigLoader = ResourceFileLoader("merge-tests/no-overlap/base"),
                        environmentConfigLoader = ResourceFileLoader("merge-tests/no-overlap/environments"))
                val config = loader.load()
                it("should create a union of config files, and load the correct environment") {
                    assert.that(config.getString("base"), equalTo("base"))
                    assert.that(config.getString("env"), equalTo("env"))
                    assert.that(config.getString("local"), equalTo("local"))
                    assert.that(config.hasPath("dev"), equalTo(false))
                    assert.that(config.hasPath("prod"), equalTo(false))
                }
            }

            on("config in base overridden by environment") {
                val loader = Configurator(MockEnvironmentConfig(),
                        localConfigLoader = FileNotFoundExceptionConfigLoader(),
                        fallbackLocalConfigLoader = ResourceFileLoader("merge-tests/environment-override-base/local"),
                        baseConfigLoader = ResourceFileLoader("merge-tests/environment-override-base/base"),
                        environmentConfigLoader = ResourceFileLoader("merge-tests/environment-override-base/environments"))
                val config = loader.load()
                it("should properly get the config from environment") {
                    assert.that(config.getString("base"), equalTo("env"))
                    assert.that(config.getString("local"), equalTo("local"))
                    assert.that(config.getString("base2"), equalTo("base2"))
                    assert.that(config.getString("env"), equalTo("env"))
                    assert.that(config.hasPath("prod"), equalTo(false))
                    assert.that(config.hasPath("dev"), equalTo(false))
                }
            }

            on("config in base overridden by local") {
                val loader = Configurator(MockEnvironmentConfig(),
                        localConfigLoader = FileNotFoundExceptionConfigLoader(),
                        fallbackLocalConfigLoader = ResourceFileLoader("merge-tests/local-override-base/local"),
                        baseConfigLoader = ResourceFileLoader("merge-tests/local-override-base/base"),
                        environmentConfigLoader = ResourceFileLoader("merge-tests/local-override-base/environments"))
                val config = loader.load()
                it("should properly get the config from local") {
                    assert.that(config.getString("base"), equalTo("local"))
                    assert.that(config.getString("base2"), equalTo("base2"))
                    assert.that(config.getString("env"), equalTo("env"))
                    assert.that(config.hasPath("dev"), equalTo(false))
                    assert.that(config.hasPath("prod"), equalTo(false))
                }
            }

            on("config in base overridden by both environment and local") {
                val loader = Configurator(MockEnvironmentConfig(),
                        localConfigLoader = FileNotFoundExceptionConfigLoader(),
                        fallbackLocalConfigLoader = ResourceFileLoader("merge-tests/base-override-by-both/local"),
                        baseConfigLoader = ResourceFileLoader("merge-tests/base-override-by-both/base"),
                        environmentConfigLoader = ResourceFileLoader("merge-tests/base-override-by-both/environments"))
                val config = loader.load()
                it("should use the value from local only") {
                    assert.that(config.getString("base"), equalTo("local"))
                    assert.that(config.getString("base2"), equalTo("base2"))
                    assert.that(config.getString("local"), equalTo("local"))
                    assert.that(config.getString("env"), equalTo("env"))
                    assert.that(config.hasPath("dev"), equalTo(false))
                    assert.that(config.hasPath("prod"), equalTo(false))
                }
            }
        }
    }
})