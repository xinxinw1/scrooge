import sbt._
import Keys._
import com.twitter.sbt._

object Scrooge extends Build {
  // projects that use finagle will provide their own dependent jar.
  val finagleVersion = "1.11.0"
  val utilVersion = "2.0.0"

  val generateTestThrift = TaskKey[Seq[File]](
    "generate-test-thrift",
    "generate scala/java code used for unit tests"
  )

  lazy val root = Project(
    id = "scrooge",
    base = file("."),
    settings = Project.defaultSettings ++
      StandardProject.newSettings ++
      // package the distribution zipfile too:
      addArtifact(Artifact("scrooge", "zip", "zip"), PackageDist.packageDist).settings
  ).settings(
    name := "scrooge",
    organization := "com.twitter",
    version := "2.5.5-SNAPSHOT",

    libraryDependencies ++= Seq(
      "org.apache.thrift" % "libthrift" % "0.8.0",
      "com.github.scopt" %% "scopt" % "2.0.1",
      "com.twitter" %% "util-core" % utilVersion,

      // for tests:
      "org.scala-tools.testing" %% "specs" % "1.6.9" % "test" withSources(),
      "org.scalatest" %% "scalatest" % "1.7.1" % "test",
      "org.jmock" % "jmock" % "2.4.0" % "test",
      "org.hamcrest" % "hamcrest-all" % "1.1" % "test",
      "cglib" % "cglib" % "2.1_3" % "test",
      "asm" % "asm" % "1.5.3" % "test",
      "org.objenesis" % "objenesis" % "1.1" % "test",
      "com.twitter" % "scrooge-runtime" % "1.1.2" % "test",
      "com.twitter" %% "finagle-core" % finagleVersion % "test",
      "com.twitter" %% "finagle-ostrich4" % finagleVersion % "test"
    ),

    SubversionPublisher.subversionRepository := Some("https://svn.twitter.biz/maven-public"),

    mainClass := Some("com.twitter.scrooge.Main"),

    (sourceGenerators in Test) <+= generateTestThrift,

    generateTestThrift <<= (
      streams,
      exportedProducts in Compile,
      fullClasspath in Runtime,
      sourceManaged in Test
    ) map { (out, products, cp, managed) =>
      generateThriftFor("scala", cp, managed, out.log) //++
        //generateThriftFor("java", cp, managed, out.log)
    }
  )

  def generateThriftFor(
    language: String,
    classpath: Classpath,
    managedFolder: File,
    log: Logger
  ): Seq[File] = {
    val outFolder = managedFolder / language
    outFolder.mkdirs()

    log.info("Generating " + language + " files for tests ...")
    val command = List(
      "java",
      "-cp", classpath.files.mkString(":"),
      "com.twitter.scrooge.Main",
      "--finagle",
      "--ostrich",
      "-d", outFolder.getAbsolutePath.toString,
      "-l", language,
      "src/test/resources/test.thrift"
    )
    log.debug(command.mkString(" "))
    command ! log
    (outFolder ** ("*." + language)).get.toSeq
  }
}
