plugins {
    id("io.quarkus")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val kubernetesClientVersion = "19.0.0"

dependencies {
    // Import Quarkus BOM
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.8.6"))
    
    // Quarkus RESTEasy Reactive (REST API)
    implementation("io.quarkus:quarkus-resteasy-reactive-jackson")
    
    // Quarkus Hibernate Validator (Validation)
    implementation("io.quarkus:quarkus-hibernate-validator")
    
    // Quarkus Health & Metrics
    implementation("io.quarkus:quarkus-smallrye-health")
    
    // Quarkus Arc (CDI)
    implementation("io.quarkus:quarkus-arc")
    
    // Kubernetes Java Client
    implementation("io.kubernetes:client-java:${kubernetesClientVersion}")
    
    // Testing
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
}

group = "com.example"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}
