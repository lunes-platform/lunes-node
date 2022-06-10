name         := "lunes-node"
organization := "io.lunes"
scalaVersion := "2.12.14"
version      := "0.0.9"
fork in run  := true

mainClass in Compile := Some("io.lunes.LunesNode")
resolvers += Resolver.bintrayRepo("fusesource", "maven")

val network = SettingKey[Network]("network")
network        := { Network(sys.props.get("network")) }
normalizedName := network.value.name

lazy val node = project
  .in(file("."))
  .settings(
    libraryDependencies ++=
      Dependencies.network ++
        Dependencies.db ++
        Dependencies.http ++
        Dependencies.akka ++
        Dependencies.serialization ++
        Dependencies.logging ++
        Dependencies.scalatest ++
        Dependencies.metrics ++
        Dependencies.fp ++
        Dependencies.ficus ++
        Dependencies.scorex ++
        Dependencies.commons_net ++
        Dependencies.monix.value
  )

assemblyJarName in assembly := s"lunesnode-${version.value}.jar"
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "io.netty.versions.properties") =>
    MergeStrategy.concat
  case other => (assemblyMergeStrategy in assembly).value(other)
}
