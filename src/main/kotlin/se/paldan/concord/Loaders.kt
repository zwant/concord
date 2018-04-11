package se.paldan.concord

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import se.paldan.concord.exceptions.InvalidConfigurationException
import se.paldan.concord.exceptions.MissingConfigurationException
import java.io.File

interface ConfigLoader {
    fun load(): Config
}

class ResourceFileLoader(private val fileName: String) : ConfigLoader {
    override fun load(): Config {
        try {
            val fileConfig = ConfigFactory.parseResourcesAnySyntax(fileName)
            if (fileConfig.isEmpty) {
                throw MissingConfigurationException("Couldn't find config in resources [$fileName]")
            }

            return fileConfig
        } catch (e: ConfigException.Parse) {
            throw InvalidConfigurationException("Found a config file in resources at [$fileName], but it's syntax is not correct")
        }
    }
}

class FileSystemFileLoader(private val fileName: String) : ConfigLoader {
    override fun load(): Config {
        try {
            val localConfigFile = File(fileName)
            val fileContents = localConfigFile.readText()

            return ConfigFactory.parseString(fileContents)
        } catch (e: ConfigException) {
            // There was a file, but it was malformed, bail out!
            throw InvalidConfigurationException("Found a config file at [$fileName], but its syntax is not correct")
        }
    }
}