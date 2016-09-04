lazy val root = project.in(file(".")).dependsOn(elmPlugin)

lazy val elmPlugin = file("src")
