description "CarbonChat-Common"

dependencies {
  checkstyle("ca.stellardrift:stylecheck:${vers["stylecheck"]}")

  api project(":CarbonChat-API")

  implementation("io.lettuce:lettuce-core:${vers["lettuce-core"]}") {
    exclude group: "io.netty"
  }

  implementation "org.spongepowered:configurate-hocon:${vers["configurate"]}"
  implementation "org.spongepowered:configurate-yaml:${vers["configurate"]}"

  implementation "com.proximyst.moonshine:core:${vers["moonshine"]}"

  implementation "co.aikar:idb-core:${vers["idb-core"]}"
  implementation "com.zaxxer:HikariCP:${vers["hikari"]}"

  compileOnly "com.google.code.gson:gson:${vers["gson"]}"

  compileOnly "org.slf4j:slf4j-api:${vers["slf4j-api"]}"
}
