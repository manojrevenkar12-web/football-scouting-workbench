// The simulated leagues know the truth. They must not depend on :core or read config/platform.json.
dependencies {
    implementation("tools.jackson.core:jackson-databind")
}
