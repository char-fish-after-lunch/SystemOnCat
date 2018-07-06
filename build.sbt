ThisBuild / scalaVersion := "2.12.6"
ThisBuild / organization := "cn.edu.tsinghua.cs"

lazy val soc = (project in file(".")).settings(
    name := "SystemOnCat",
    libraryDependencies ++= Seq(
        "edu.berkeley.cs" %% "chisel3" % "3.1.0",
        "org.scalatest" %% "scalatest" % "3.0.5"
    )
)