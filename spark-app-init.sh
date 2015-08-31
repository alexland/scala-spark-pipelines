#!/usr/local/bin/zsh

# starter.sh

##########
#	compiles scala code written against the scala-spark api
#	to a jar, then sends it to the spark cluster via the
#	spark submitter
#
#	does not build spark, but relies on an extant install
#	which is accessed by the environment variable below
#
#	environment variable SPARK_HOME must be set & alias
#	top-level directory of spark src, eg set in .bash_profile, .zshrc, or other:
#	export SPARK_HOME="$HOME/spark"
#
#
##########

# stop hdfs server
sh /usr/local/Cellar/hadoop/2.7.1/libexec/sbin/stop-dfs.sh

# restart hdfs server
sh /usr/local/Cellar/hadoop/2.7.1/libexec/sbin/start-dfs.sh


sbt clean assembly

# open http://192.168.100.56:4040/jobs/


$SPARK_HOME/bin/spark-submit \
	--driver-memory 6g \
	--class "DataModel" \
	--master "local[*]" \
	--deploy-mode client \
	target/scala-2.10/mdm.jar

