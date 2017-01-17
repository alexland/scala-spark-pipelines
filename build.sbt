
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

SbtScalariform.scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
	.setPreference(AlignSingleLineCaseStatements, true)
	.setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
	.setPreference(DoubleIndentClassDeclaration, true)
	.setPreference(PreserveDanglingCloseParenthesis, true)
	.setPreference(RewriteArrowSymbols, true)
	.setPreference(IndentWithTabs, true)
	.setPreference(IndentPackageBlocks, true)
	.setPreference(DoubleIndentClassDeclaration, true)

// enable the appropriate archetype
enablePlugins(JavaAppPackaging)

shellPrompt := { state =>
  s"\033[48;5;171m[${name.value}]\033[0m >> "
}


addCommandAlias(
	"theWorks",
	";clean;compile;test;coverageReport;scalastyle")

lazy val gitProj1 = RootProject(
											uri("git://github.com/soc/scala-java-time")
)

val gitHeadCommitSha = taskKey[String]("retrieves commit SHA")

val makeVersionProperties = taskKey[Seq[File]](
	"creates a version.properties file for runtime access"
)

gitHeadCommitSha := Process("git rev-parse HEAD")
											.lines
											.head


lazy val commonSettings = Seq(
	version in ThisBuild := "0.1-SNAPSHOT",
	organization in ThisBuild := "org.dougybarbo",
	scalaVersion := "2.11.8",
	autoScalaLibrary := true,
	coverageEnabled := true,
	doctestTestFramework := DoctestTestFramework.ScalaTest,
	fork in run := true,
	javaHome := Some(
								file(
									"/Library/Java/JavaVirtualMachines/jdk1.8.0_102.jdk/Contents/Home"
								)
	),
	scalacOptions in Test ++= Seq("-Yrangepos"),
	scalacOptions in (Compile, doc) ++= Seq(
		"-unchecked",
		"-optimize",
		"-Yinline-warnings",
		"-feature",
		"-Yrangepos"
	),
	makeVersionProperties := {
		 val propFile = (resourceManaged in Compile).value / "version.properties"
		 val content = "version=%s" format (gitHeadCommitSha.value)
		 IO.write(propFile, content)
		 Seq(propFile)
	},
	resourceGenerators in Compile <+= makeVersionProperties,
	resolvers ++= {
		Seq(
			"scalaz-bintray" 					at		"https://dl.bintray.com/scalaz/releases",
			"Local Maven Repository"	at		"file://"+Path.userHome.absolutePath+"/.m2/repository",
			"Sonatype Snapshots"			at		"https://oss.sonatype.org/content/repositories/snapshots/",
			"Sonatype Releases"				at		"https://oss.sonatype.org/content/repositories/releases/",
			"Artima Maven Repository" at 		"http://repo.artima.com/releases"
		)
	},
	libraryDependencies ++= {
		Seq(
			"org.apache.spark"						%% 		"spark-core"								% 		"2.1.0"			%		"provided",
			"org.scala-lang.modules"			%%		"scala-parser-combinators"	%			"1.0.4",
			"org.slf4j"										%			"slf4j-simple"							%			"1.7.21",
			"org.slf4j"										%			"slf4j-api"									%			"1.7.21",
			"net.sourceforge.f2j"					%			"arpack_combined_all"				%			"0.1",
			"org.spire-math"							%%		"spire"											%			"0.12.0",
			"org.scalaz"									%%		"scalaz-core"								%			"7.2.6",
			"com.github.scala-blitz"			%%		"scala-blitz"								%			"1.2",
			"org.scalactic"								%%		"scalactic"									%			"3.0.0",
			"org.scalatest"								%%		"scalatest"									%			"3.0.0"		 % "test"
		)
	},
	excludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
		cp filter { f =>
			(f.data.getName contains "commons-logging") ||
			(f.data.getName contains "sbt-link")
		}
	},
	assemblyMergeStrategy in assembly := {
		case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
		case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
		case "application.conf"                            => MergeStrategy.concat
		case "unwanted.txt"                                => MergeStrategy.discard
		case x                                             =>
			val oldStrategy = (assemblyMergeStrategy in assembly).value
			oldStrategy(x)
	},
	mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => {
		case "application.conf"                           => MergeStrategy.concat
		case "reference.conf"                             => MergeStrategy.concat
		case "META-INF/spring.tooling"                    => MergeStrategy.concat
		case "overview.html"                              => MergeStrategy.rename
		case PathList("javax", "servlet", xs @ _*)        => MergeStrategy.last
		case PathList("org", "apache", xs @ _*)           => MergeStrategy.last
		case PathList("META-INF", xs @ _*)                => MergeStrategy.discard
		case PathList("com", "esotericsoftware", xs @ _*) => MergeStrategy.last
		case "about.html"                                 => MergeStrategy.rename
		case x                                            => old(x)
	}}
)

lazy val root = (project in file("."))
	.settings(Revolver.settings)
	.settings(commonSettings: _*)
	.dependsOn(gitProj1)
	.settings(
		assemblyJarName in assembly := "tsOps.jar",
		mainClass in Compile := Some("org.dougybarbo.TsOps.Ts"),
		mainClass in assembly := Some("org.dougybarbo.TsOps.Ts"),
		javaOptions in (Compile, run) ++= Seq(
			"-Xincgc",
			"-Xms6g",
			"-Xmx6g",
			"-Xmn2g",
			"-Xss20m",
			"-XX:+AggressiveOpts",
			"-XX:+PrintGCDetails",
			"-XX:+PrintGCDateStamps",
			"-Xloggc:gc_log.gc"
		),
		coverageEnabled in Test := true,
		// coverage thresholds that cause the build to fail
		coverageMinimum := 25,
		coverageFailOnMinimum := true,
		coverageHighlighting := {
			if(scalaBinaryVersion.value == "2.11") true
			else false
		},
		publishArtifact in Test := false,
		parallelExecution in Test := false,
		/**
			*	this setting scoped to the 'Compile' configuration &
			*	the 'unmanagedSources' task
			*/
		excludeFilter in (Compile, unmanagedSources) := new FileFilter {
			def accept(f: File) = {
				"/srcAlt/.*"
					.r
					.pattern
					.matcher(f.getAbsolutePath)
					.matches
			}
		}
)
