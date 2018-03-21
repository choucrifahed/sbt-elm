lazy val root = project.in(file(".")).dependsOn(elmPlugin)

lazy val elmPlugin = ClasspathDependency(RootProject(file("../../..")), None)
