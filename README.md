
several complete scala applications built on the spark api, for data transformation and
building predictive models.

dependencies:

* hdfs (hadoop 2.7.1)

* spark (>= 1.4.1)


the data set is available from an [online archive!](http://www-etud.iro.umontreal.ca/~bergstrj/audioscrobbler_data.html) at:




includes:

* a _build.sbt_ to build the app as an uber jar

* an init script to trigger the build & submit the uber jar to spark

