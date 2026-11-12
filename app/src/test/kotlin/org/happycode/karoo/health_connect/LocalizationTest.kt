package org.happycode.karoo.health_connect

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class LocalizationTest : FreeSpec({

    fun parseStringKeys(file: File): Map<String, String> {
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = docBuilder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i) as Element
            val name = node.getAttribute("name")
            val text = node.textContent
            result[name] = text
        }
        return result
    }

    fun findResFile(subPath: String): File {
        val direct = File(subPath)
        if (direct.exists()) return direct
        val appPrefixed = File("app", subPath)
        if (appPrefixed.exists()) return appPrefixed
        error("Could not find file: $subPath")
    }

    "should ensure German translations match default strings" {
        val defaultStringsFile = findResFile("src/main/res/values/strings.xml")
        val germanStringsFile = findResFile("src/main/res/values-de/strings.xml")

        val defaultStrings = parseStringKeys(defaultStringsFile)
        val germanStrings = parseStringKeys(germanStringsFile)

        defaultStrings.keys shouldContainExactlyInAnyOrder germanStrings.keys

        germanStrings.forEach { (key, value) ->
            value.shouldNotBeBlank()
        }
    }

    "should ensure format specifiers match between default and German translations" {
        val defaultStringsFile = findResFile("src/main/res/values/strings.xml")
        val germanStringsFile = findResFile("src/main/res/values-de/strings.xml")

        val defaultStrings = parseStringKeys(defaultStringsFile)
        val germanStrings = parseStringKeys(germanStringsFile)

        val specifierRegex = Regex("%(\\d+\\$)?[sdf]")

        defaultStrings.forEach { (key, defaultText) ->
            val germanText = germanStrings[key] ?: error("Missing German key: $key")
            val defaultSpecifiers = specifierRegex.findAll(defaultText).map { it.value }.toList()
            val germanSpecifiers = specifierRegex.findAll(germanText).map { it.value }.toList()

            germanSpecifiers shouldBe defaultSpecifiers
        }
    }
})
