scalaVersion := "2.12.7"

val http4sVersion = "0.18.19"
val rhoVersion    = "0.18.0"

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4")
addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4")

libraryDependencies ++= Seq(
  "org.http4s"        %% "http4s-dsl"          % http4sVersion,
  "org.http4s"        %% "http4s-blaze-server" % http4sVersion,
  "org.http4s"        %% "rho-swagger"         % rhoVersion,
  "io.chrisdavenport" %% "log4cats-slf4j"      % "0.1.1",
  "ch.qos.logback"    %  "logback-classic"     % "1.2.3"
)

scalacOptions ++= Seq("-Ypartial-unification")
