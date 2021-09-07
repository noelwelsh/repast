val scala3Version = "3.0.2"

ThisBuild / name := "repast"
ThisBuild / scalaVersion := scala3Version
ThisBuild / useSuperShell := false
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "2.6.1",
  "org.scalameta" %% "munit" % "0.7.29" % Test,
  "com.novocode" % "junit-interface" % "0.11" % Test
)

lazy val build = taskKey[Unit]("Build everything")
build := {
  dependencyUpdates.value
  scalafmtAll.value
  scalafixAll.toTask("").value
  (Compile / compile).value
  (Test / test).value
}
