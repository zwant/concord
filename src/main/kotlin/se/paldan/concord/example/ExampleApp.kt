package se.paldan.concord.example

import se.paldan.concord.Configurator
import se.paldan.concord.EnvironmentConfiguration

fun main(args: Array<String>) {
    val loader = Configurator(EnvironmentConfiguration("EXAMPLE_APP"))
    val config = loader.load()

    System.out.println(config)
}