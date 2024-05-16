import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

plugins {
    `maven-publish`
}

@Serializable
data class Publish(
    val modules: List<Module>
)

@Serializable
data class Module(
    val name: String,
    val artifactId: String,
    val version: String
)

var json = Json { ignoreUnknownKeys = true }
var publishConf = json.decodeFromString<Publish>(rootProject.file("publish.json").readText())

val publishMavenURL =
    project.findProperty("mavenURL") ?: "https://pkgs.dev.azure.com/foodtruckinc/Wonder/_packaging/maven-local/maven/v1"
val publishMavenAccessToken: String? =
    project.findProperty("mavenAccessToken")?.toString() ?: System.getenv("MAVEN_ACCESS_TOKEN")

publishConf.modules.forEach { module ->
    project(module.name) {
        pluginManager.apply(MavenPublishPlugin::class.java)

        publishing {
            publications {
                register("apiInterface", MavenPublication::class) {
                    groupId = "com.wonder"
                    artifactId = module.artifactId
                    version = module.version
                    from(components["java"])

                    pom {
                        withXml {
                            val moduleFile = file("${layout.buildDirectory.asFile.get().absolutePath}/publications/apiInterface/module.json")
                            val json = Json { prettyPrint = true }
                            moduleFile.writeText(json.encodeToString(JsonElement.serializer(), buildJsonObject {
                                json.parseToJsonElement(moduleFile.readText()).jsonObject.entries.forEach { (key, value) ->
                                    put(key, if (key != "variants") value else buildJsonArray {
                                        value.jsonArray.forEach { variant ->
                                            add(if (!variant.jsonObject.containsKey("dependencies")) variant else buildJsonObject {
                                                variant.jsonObject.entries.forEach { (key, value) ->
                                                    put(key, if (key != "dependencies") value else buildJsonArray {
                                                        value.jsonArray.forEach { dependency ->
                                                            if (!(dependency.jsonObject["group"]?.jsonPrimitive?.content == "com.wonder" && dependency.jsonObject["module"]?.jsonPrimitive?.content == "wonder-dependencies")) {
                                                                add(dependency)
                                                            }
                                                        }
                                                    })
                                                }
                                            })
                                        }
                                    })
                                }
                            }))
                        }
                    }
                }
            }
            repositories {
                maven {
                    url = uri(publishMavenURL)
                    credentials {
                        username = "foodtruckinc"
                        password = publishMavenAccessToken
                    }
                }
            }
        }
    }
}