import com.typesafe.sbt.packager.archetypes.TemplateWriter
import sbt.Keys.{sourceGenerators, _}
import sbt._
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import sbtassembly.MergeStrategy

enablePlugins(JavaServerAppPackaging, JDebPackaging, SystemdPlugin, GitVersioning)
scalafmtOnCompile in ThisBuild := true

val network = SettingKey[Network]("network")
network := { Network(sys.props.get("network")) }
name := "lunes"
normalizedName := s"${name.value}${network.value.packageSuffix}"

git.useGitDescribe := true
git.uncommittedSignifier := Some("DIRTY")
logBuffered := false

inThisBuild(
  Seq(
    scalaVersion := "2.12.6",
    organization := "io.lunes",
    crossPaths := false,
    scalacOptions ++= Seq("-feature", "-deprecation", "-language:higherKinds", "-language:implicitConversions", "-Ywarn-unused:-implicits", "-Xlint")
  ))

resolvers += Resolver.bintrayRepo("ethereum", "maven")

fork in run := true
javaOptions in run ++= Seq(
  "-XX:+IgnoreUnrecognizedVMOptions",
  "--add-modules=java.xml.bind"
)

val aopMerge: MergeStrategy = new MergeStrategy {
  val name = "aopMerge"
  import scala.xml._
  import scala.xml.dtd._

  def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
    val dt                         = DocType("aspectj", PublicID("-//AspectJ//DTD//EN", "http://www.eclipse.org/aspectj/dtd/aspectj.dtd"), Nil)
    val file                       = MergeStrategy.createMergeTarget(tempDir, path)
    val xmls: Seq[Elem]            = files.map(XML.loadFile)
    val aspectsChildren: Seq[Node] = xmls.flatMap(_ \\ "aspectj" \ "aspects" \ "_")
    val weaverChildren: Seq[Node]  = xmls.flatMap(_ \\ "aspectj" \ "weaver" \ "_")
    val options: String            = xmls.map(x => (x \\ "aspectj" \ "weaver" \ "@options").text).mkString(" ").trim
    val weaverAttr                 = if (options.isEmpty) Null else new UnprefixedAttribute("options", options, Null)
    val aspects                    = new Elem(null, "aspects", Null, TopScope, false, aspectsChildren: _*)
    val weaver                     = new Elem(null, "weaver", weaverAttr, TopScope, false, weaverChildren: _*)
    val aspectj                    = new Elem(null, "aspectj", Null, TopScope, false, aspects, weaver)
    XML.save(file.toString, aspectj, "UTF-8", xmlDecl = false, dt)
    IO.append(file, IO.Newline.getBytes(IO.defaultCharset))
    Right(Seq(file -> path))
  }
}

inTask(assembly)(
  Seq(
    test := {},
    assemblyJarName := s"lunesnode-${version.value}.jar",
    assemblyMergeStrategy := {
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.concat
      case PathList("META-INF", "aop.xml")                      => aopMerge
      case other                                                => (assemblyMergeStrategy in assembly).value(other)
    }
  ))

inConfig(Compile)(
  Seq(
    mainClass := Some("io.lunes.LunesNode"),
  ))

inConfig(Universal)(
  Seq(
    javaOptions ++= Seq(
      // -J prefix is required by the bash script
      "-J-server",
      // JVM memory tuning for 2g ram
      "-J-Xms128m",
      "-J-Xmx2g",
      "-J-XX:+ExitOnOutOfMemoryError",
      // Java 9 support
      "-J-XX:+IgnoreUnrecognizedVMOptions",
      "-J--add-modules=java.xml.bind",
      // from https://groups.google.com/d/msg/akka-user/9s4Yl7aEz3E/zfxmdc0cGQAJ
      "-J-XX:+UseG1GC",
      "-J-XX:+UseNUMA",
      "-J-XX:+AlwaysPreTouch",
      // probably can't use these with jstack and others tools
      "-J-XX:+PerfDisableSharedMem",
      "-J-XX:+ParallelRefProcEnabled",
      "-J-XX:+UseStringDeduplication"
    )
  ))


lazy val lang =
  crossProject(JSPlatform, JVMPlatform)
    .withoutSuffixFor(JVMPlatform)
    .settings(
      version := "0.0.1",
      // the following line forces scala version across all dependencies
      scalaModuleInfo ~= (_.map(_.withOverrideScalaVersion(true))),
      test in assembly := {},
      addCompilerPlugin(Dependencies.kindProjector),
      libraryDependencies ++=
        Dependencies.cats ++
          Dependencies.fp ++
          Dependencies.scalacheck ++
          Dependencies.scorex ++
          Dependencies.scalatest ++
          Dependencies.scalactic ++
          Dependencies.monix.value ++
          Dependencies.scodec.value ++
          Dependencies.fastparse.value,
      resolvers += Resolver.bintrayIvyRepo("portable-scala", "sbt-plugins")
    )
    .jsSettings(
      scalaJSLinkerConfig ~= {
        _.withModuleKind(ModuleKind.CommonJSModule)
      }
    )
    .jvmSettings(
      libraryDependencies ++= Seq(
        "org.scala-js"                %% "scalajs-stubs" % "0.6.22" % "provided"
      ) ++ Dependencies.logging.map(_ % "test") // scrypto logs an error if a signature verification was failed
    )

lazy val langJS  = lang.js
lazy val langJVM = lang.jvm

lazy val node = project
  .in(file("."))
  .settings(
    addCompilerPlugin(Dependencies.kindProjector),
    libraryDependencies ++=
      Dependencies.network ++
        Dependencies.db ++
        Dependencies.http ++
        Dependencies.akka ++
        Dependencies.serialization ++
        Dependencies.testKit.map(_ % "test") ++
        Dependencies.logging ++
        Dependencies.matcher ++
        Dependencies.metrics ++
        Dependencies.fp ++
        Dependencies.meta ++
        Dependencies.ficus ++
        Dependencies.scorex ++
        Dependencies.commons_net ++
        Dependencies.monix.value
  )
  .dependsOn(langJVM)
