
/* DataModel.scala
*/

import java.lang.Math._
import java.util.Date
import java.text.NumberFormat.getIntegerInstance

import scala.collection.Map
import scala.collection.mutable.ArrayBuffer
import scala.util.{Random => RND}

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.mllib.recommendation._
import org.apache.spark.mllib.recommendation.ALS


object DataModel {

	def main(args:Array[String]) = {
		val cluster_url = "local"
		val conf = new SparkConf()
					.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
					// .set("spark.kryo.registrationRequired", "true")
					// .registgerKryoClasses(
					// Array( scala.Tuple2[].class )
					// )
					.setMaster(cluster_url)
					.setAppName("MDM")
					.set("spark.driver.memory", "6g")
		val sc = new SparkContext(conf)
		val filePath = "/Users/douglasybarbo/Projects/scala-spark-pipelines/src/main/resources/ex1.data"
		val d = sc.textFile(filePath)
		val fnx = (r:String) => r.split(" ").map(_.trim).filter(_.size != 0).map(_.toFloat)
		val data = d.map(fnx).cache()
		val fnx1 = (r:Array[Float]) => r.reduce((u, v) => u + v)
		val sumRow = data.map(fnx1).sum()
		val numberOfRows = data.count()
		val formatter = getIntegerInstance
		println(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::|")
		println(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::|")
		println(f"current date & time: ${new Date()}")
		println( "number of rows in dataset: " + formatter.format(numberOfRows) )
		println("total per row " + formatter.format(sumRow))
		println("::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::|")
		println("::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::|")
		val hdfsPrefix = "hdfs://localhost:8020/user/doug/lastfm"
		val playCountsFile = hdfsPrefix + "/user_artist_data.txt"
		val artistsFile = hdfsPrefix + "/artist_data.txt"
		val artistAliasFile = hdfsPrefix + "/artist_alias.txt"
		val rawUserArtistData = sc.textFile(playCountsFile, 4)
		val rawArtistData = sc.textFile(artistsFile)
		val rawArtistAlias = sc.textFile(artistAliasFile)

		case class UserArtistPlayCounts(
			userId:Int,
			artistId:Int,
			uaPlayCount:Double
		)

		val artistAlias = rawArtistAlias.flatMap { line =>
			val tokens = line.split('\t')
			if (tokens(0).isEmpty) {
				None
			} else {
				Some((tokens(0).toInt, tokens(1).toInt))
			}
		}.collectAsMap()

		val bArtistAlias = sc.broadcast(artistAlias)

		val trainData = rawUserArtistData.map { line =>
			val Array(userID, artistID, count) = line.split(" ").map(_.toInt)
			val finalArtistID = bArtistAlias.value.getOrElse(artistID, artistID)
			Rating(userID, finalArtistID, count)
		}.cache()

		// building the model
		val model_nnma = ALS.trainImplicit(
							trainData,
							rank=10,
							iterations=5,
							lambda=0.01,
							alpha=1.0
		)

		// using the model to make predictions
		 val userId = 2093760
		val numRecommendations = 5

		val rec = model_nnma.recommendProducts(userId, numRecommendations)

		println(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::|")
		println(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::|")
		rec.foreach(println)
		println(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::|")
		println(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::|")

		// --------------------------------- descriptive statistics -----------------------------//

		def etl0(rowStr:String) = {
			val rowInt = rowStr.split(" ").map(_.toInt)
			UserArtistPlayCounts( rowInt(0), rowInt(1), rowInt(2) )
		}

		val D = rawUserArtistData.map(row => etl0(row))

		val userIds = D.map(_.userId).cache()
		val artistIds = D.map(_.artistId).cache()
		val uaPlayCounts = D.map(_.uaPlayCount).cache()

		// descriptive stats on play count values
		val sumStats = uaPlayCounts.stats()

		// count userIds
		val cnt = userIds.count()

		//  count unique userIds
		val usersUnique = userIds.distinct()

		// roll up user counts (not per artist)
		val d1 = D.map(row => (row.userId, row.uaPlayCount))
		val playsByUser= d1.reduceByKey(_ + _)

		// generate play counts frequencies
		val fnx2 = (t:Tuple2[Int, Double]) => t._2
		val playCountsByVal = playsByUser.map(fnx2).countByValue()

		// sort user counts
		val playCountsSorted = playCountsByVal.values.toArray.sortWith(_ > _)
		val highCount = playCountsSorted(0)

		val tx = sc.parallelize(playCountsSorted)
		val meanCount = tx.stats.mean

		println(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::|")
		println(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::|")
		println(f"current date & time: ${new Date()}")
		println("number of rows in dataset: " + formatter.format(cnt))
		println("mean play count per user per artist is: " + formatter.format(meanCount))
		println("top user's play count, all artists: " + formatter.format(highCount))
		println(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::|")
		println(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::|")



	}
}

