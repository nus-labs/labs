def scalacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // If we're building with Scala > 2.11, enable the compile option
    //  switch to support our anonymous Bundle definitions:
    //  https://github.com/scala/bug/issues/10047
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Long)) if scalaMajor < 12 => Seq()
      case _ => Seq("-Xsource:2.11")
    }
  }
}

def javacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // Scala 2.12 requires Java 8. We continue to generate
    //  Java 7 compatible code for Scala 2.11
    //  for compatibility with old clients.
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Long)) if scalaMajor < 12 =>
        Seq("-source", "1.7", "-target", "1.7")
      case _ =>
        Seq("-source", "1.8", "-target", "1.8")
    }
  }
}

organization := "nus"
name := "labs"
version := "0.2"
scalacOptions := Seq("-deprecation", "-feature") ++ scalacOptionsVersion(scalaVersion.value)
scalaVersion := "2.12.4"
crossScalaVersions := Seq("2.11.12", "2.12.4")
libraryDependencies += "com.github.scopt" %% "scopt" % "3.6.0"
libraryDependencies += "org.json4s" %% "json4s-native" % "3.5.3"
addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)

Compile / unmanagedSourceDirectories += baseDirectory.value / "firrtl/src"

val defaultVersions = Map("chisel3" -> "3.2.7",
                          "chisel-iotesters" -> "1.2.+",
                          "firrtl"  -> "1.2.8" )

libraryDependencies ++= defaultVersions.map{ case (k, v) =>
  "edu.berkeley.cs" %% k % sys.props.getOrElse(k + "Version", v) }.toSeq
