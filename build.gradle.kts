repositories {
    maven("https://maven-central.storage-download.googleapis.com/maven2/") {
        name = "maven-central-mirror"
    }
    maven("https://repo1.maven.org/maven2/") {
        name = "maven-central-repo1"
    }
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc-repo" }
    maven("https://oss.sonatype.org/content/groups/public/") { name = "sonatype" }
    maven("https://repo.onarandombox.com/content/groups/public/")
    maven("https://jitpack.io") { name = "jitpack" }
    maven("https://maven.joutak.ru/snapshots")
}
