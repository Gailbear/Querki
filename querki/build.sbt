import sbt.Project.projectToRef

lazy val clients = Seq(querkiClient)

lazy val scalaV = "2.11.6"
lazy val appV = "1.1"

lazy val sharedSrcDir = "scala"

lazy val querkiServer = (project in file("scalajvm")).settings(
  scalaVersion := scalaV,
  version := appV,
  scalaJSProjects := clients,
  pipelineStages := Seq(scalaJSProd, gzip),
  // To prevent duplicate-artifact errors in Stage:
  publishArtifact in (Compile, packageSrc) := false,
  libraryDependencies ++= sharedDependencies.value ++ Seq(
    // Main Play dependencies
    jdbc,
    anorm,
    // Add your project dependencies here,
    "mysql" % "mysql-connector-java" % "5.1.23",
    "javax.mail" % "javax.mail-api" % "1.5.0",
    "com.sun.mail" % "smtp" % "1.5.0",
    "com.sun.mail" % "mailapi" % "1.5.0",
    "com.github.nscala-time" %% "nscala-time" % "1.6.0",
    "com.typesafe.akka" %% "akka-testkit" % "2.3.9",
    "org.imgscalr" % "imgscalr-lib" % "4.2",
    "com.amazonaws" % "aws-java-sdk" % "1.8.4",
    "com.vmunier" %% "play-scalajs-scripts" % "0.1.0",
	"com.lihaoyi" %% "utest" % "0.3.1",
	"org.querki" %% "requester" % "1.0"
  ),
  EclipseKeys.skipParents in ThisBuild := false).
  settings(sharedDirectorySettings: _*).
  enablePlugins(PlayScala).
  aggregate(clients.map(projectToRef): _*)

lazy val querkiClient = (project in file("scalajs")).settings(
  scalaVersion := scalaV,
  version := appV,
  persistLauncher := true,
  persistLauncher in Test := false,
  sourceMapsDirectories += file(sharedSrcDir),
  unmanagedSourceDirectories in Compile := Seq((scalaSource in Compile).value),
  
  jsDependencies += RuntimeDOM,
//  postLinkJSEnv := PhantomJSEnv(autoExit = false).value,
  testFrameworks += new TestFramework("utest.runner.Framework"),

  // Javascript libraries we require:
  skip in packageJSDependencies := false,
  
  // When we need to debug into jQuery itself, uncomment these two lines, and comment out the one below them:
//  jsDependencies += "org.webjars" % "jquery" % "2.1.3" / "jquery.js" dependsOn "jquery.min.js",
//  jsDependencies += ProvidedJS / "jquery-shim.js" dependsOn "jquery.js",
  // Normal case: use the minified version of JQuery:
  jsDependencies += ProvidedJS / "jquery-shim.js" dependsOn "jquery.min.js",
  
  jsDependencies += ProvidedJS / "jquery-ui-1.10.0.custom.js" dependsOn "jquery-shim.js",
  jsDependencies += ProvidedJS / "jquery.manifest.js" dependsOn "jquery-shim.js",
  jsDependencies += ProvidedJS / "jquery.ui.touch-punch.js" dependsOn "jquery-ui-1.10.0.custom.js",
  jsDependencies += "org.webjars" % "bootstrap" % "3.3.4" / "bootstrap.min.js" dependsOn "jquery-shim.js",
  jsDependencies += ProvidedJS / "jquery.autosize.min.js" dependsOn "jquery-shim.js",
  jsDependencies += ProvidedJS / "jquery.raty.js" dependsOn "jquery-shim.js",
  jsDependencies += ProvidedJS / "jquery.histogram.js" dependsOn "jquery-shim.js",
  jsDependencies += ProvidedJS / "moment.min.js",

  jsDependencies += ProvidedJS / "load-image.min.js" dependsOn "jquery-ui-1.10.0.custom.js",
  jsDependencies += ProvidedJS / "canvas-to-blob.min.js" dependsOn "load-image.min.js",
  jsDependencies += ProvidedJS / "jquery.iframe-transport.js" dependsOn "load-image.min.js",
  jsDependencies += ProvidedJS / "jquery.fileupload.js" dependsOn "jquery.iframe-transport.js",
  jsDependencies += ProvidedJS / "jquery.fileupload-process.js" dependsOn "jquery.fileupload.js",
  jsDependencies += ProvidedJS / "jquery.fileupload-image.js" dependsOn "jquery.fileupload.js",
  
  libraryDependencies ++= sharedDependencies.value ++ Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.8.0",
    "com.lihaoyi" %%% "utest" % "0.3.1",
    "org.scala-js" %%% "scala-parser-combinators" % "1.0.2",
    "org.scala-lang.modules" %% "scala-async" % "0.9.2",
	"org.querki" %%% "querki-jsext" % "0.5",
	"org.querki" %%% "jquery-facade" % "0.4",
	"org.querki" %%% "bootstrap-datepicker-facade" % "0.1"
  )).
  settings(sharedDirectorySettings: _*).
  enablePlugins(ScalaJSPlugin, ScalaJSPlay)

lazy val sharedDirectorySettings = Seq(
  unmanagedSourceDirectories in Compile += new File((file(".") / sharedSrcDir / "src" / "main" / "scala").getCanonicalPath),
  unmanagedSourceDirectories in Test += new File((file(".") / sharedSrcDir / "src" / "test" / "scala").getCanonicalPath),
  unmanagedResourceDirectories in Compile += file(".") / sharedSrcDir / "src" / "main" / "resources",
  unmanagedResourceDirectories in Test += file(".") / sharedSrcDir / "src" / "test" / "resources"
)

lazy val sharedDependencies = Def.setting(Seq(
  "com.lihaoyi" %%% "upickle" % "0.2.7",
  "com.lihaoyi" %%% "scalarx" % "0.2.8",
  "com.lihaoyi" %%% "autowire" % "0.2.5",
  "com.lihaoyi" %%% "scalatags" % "0.4.6",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
))

onLoad in Global := (Command.process("project querkiServer", _: State)) compose (onLoad in Global).value