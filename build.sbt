val scala3Version = "3.1.0"

ThisBuild / name := "repast"
ThisBuild / organization := "com.noelwelsh"
ThisBuild / scalaVersion := scala3Version
ThisBuild / version := "0.1.0"
ThisBuild / versionScheme := Some("early-semver")

ThisBuild / useSuperShell := false

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "2.7.0",
  "org.scalameta" %% "munit" % "0.7.29" % Test
)

lazy val build = taskKey[Unit]("Build everything")
build := {
  dependencyUpdates.value
  scalafmtAll.value
  scalafixAll.toTask("").value
  (Compile / compile).value
  (Test / test).value
}
