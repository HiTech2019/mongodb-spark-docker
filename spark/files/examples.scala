
import com.mongodb.spark._
import com.mongodb.spark.config._
import org.apache.spark.sql.SQLContext

import org.bson.Document 

/* Load collection as RDD */
val rdd = MongoSpark.load(sc)
println("Input Count: " + rdd.count)
println("Input documents: ")
rdd.foreach(println)

/*
 PROCESSING  
 For example, if you have 4 documents of :

{ "doc": "A", "timestamp" : ISODate("2016-02-15T00:43:04.686Z"), "myid" : 1 }
{ "doc": "B", "timestamp" : ISODate("2016-02-15T00:43:06.310Z"), "myid" : 2 }
{ "doc": "C", "timestamp" : ISODate("2016-01-03T00:43:07.534Z"), "myid" : 1 }
{ "doc": "D", "timestamp" : ISODate("2016-01-03T00:43:09.214Z"), "myid" : 2 }

Group by `myid` sort latest timestamp, would return only two documents, doc:A and doc:B. 
Removing duplicates of myid’s by returning only documents with the latest timestamp.
*/
import org.joda.time.DateTime
val outputRDD = rdd.map(
            (tuple)=>((tuple.get("myid")), (tuple.get("timestamp")))
             ).reduceByKey((x, y) => if (new DateTime(x).isAfter(new DateTime(y))) x else y ).map(
            (tuple)=>{val newdoc = new Document(); 
                  newdoc.append("myid", tuple._1); 
                  newdoc.append("timestamp", tuple._2); 
                  newdoc
                  }
        )
println("Spark RDD processing result: ")
outputRDD.foreach(println)

/* Similar aggregation as above, but utilising MongoDB aggregation pipeline */
val aggRdd = rdd.withPipeline(Seq(
                Document.parse("{$sort:{timestamp:1}}"), 
                Document.parse("{$group:{_id:{'myid':'$myid'}, record:{'$first':'$$ROOT'}}}"), 
                Document.parse("{$project:{'_id':0, 'doc':'$record.doc', 'timestamp':'$record.timestamp', 'myid':'$record.myid'}}")
                )   
            )
println("MongoDB aggregation pipeline reult: ")
aggRdd.foreach(println)

// Save result to MongoDB
// 1) Default 
aggRdd.saveToMongoDB()
// 2) Using helper and WriteConfig to modify destination 
outputRDD.saveToMongoDB(WriteConfig(Map("uri"->"mongodb://mongodb:27017/spark.processing")))
println("RDD is written to MongoDB")

/* DataFrames examples */
val sqlContext = SQLContext.getOrCreate(sc)
val df = MongoSpark.load(sqlContext)

// Print schema 
df.printSchema()

// Filter by Integer and by String
df.filter(df("myid") < 2).show()
df.filter(df("doc") === "V ").show()

// DataFrames SQL example 
df.registerTempTable("temporary")
val sqlResult = sqlContext.sql("SELECT myid, doc, timestamp FROM temporary WHERE myid > 6 AND doc='V '")
sqlResult.show()

// Save out the filtered DataFrame result
MongoSpark.save(sqlResult.write.option("collection", "DF_times").mode("overwrite"))
// Alternatively you could also specify uri 
// MongoSpark.save(sqlResult.write.option("uri", "mongodb://mongodb:27017/spark.DF_times").mode("overwrite"))

// Read it back in 
MongoSpark.load(sqlContext, ReadConfig(Map("collection" -> "DF_times"), Some(ReadConfig(sqlContext)))).show()

println("Done")
System.exit(0);


