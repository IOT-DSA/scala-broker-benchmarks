resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "4.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.2")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")