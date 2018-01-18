package m1;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.math3.analysis.function.Add;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.storage.StorageLevel;


public class Recommender {
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		SparkConf conf = new SparkConf();
		conf.setAppName("Recommender");
		JavaSparkContext ctx = new JavaSparkContext(conf);
		String friendList_filePath = args[0];
		float jaccard = Float.parseFloat(args[2]); 
		int k = Integer.parseInt(args[3]);
		JavaRDD<String> data = ctx.textFile(friendList_filePath);
		// 개인마다 친구를 리스트 형태로 나누어 저장
		
		JavaPairRDD<Integer, List<Integer>> friendPair = data.flatMapToPair(
				line -> {
					String[] pair = line.split("\t");
					List<Tuple2<Integer, List<Integer>>> outList = new ArrayList<>();
					List<Integer> first_list = new ArrayList<>();
					List<Integer> second_list = new ArrayList<>();
					int i1 = Integer.parseInt(pair[0]);
					int i2 = Integer.parseInt(pair[1]);
					first_list.add(i1);
					outList.add(new Tuple2(i2,first_list));
					second_list.add(i2);
					outList.add(new Tuple2(i1,second_list));
					return outList.iterator();
				}
		).reduceByKey((a,b) -> {a.addAll(b); return a;}).persist(StorageLevel.MEMORY_AND_DISK());
		
		// 개인 친구 수
	    JavaPairRDD<Integer, Integer> numOfFriend = friendPair.mapToPair( x -> new Tuple2(x._1, x._2.size())).persist(StorageLevel.MEMORY_AND_DISK());
		
	    Broadcast<HashMap<Integer, Integer>> numOfFriends2 = ctx.broadcast(new HashMap(numOfFriend.collectAsMap()));
	    //Broadcast<List<Tuple2<Integer,Integer>>> numOfFriends = ctx.broadcast(numOfFriend.collect());
	    
	    // 각쌍의 교집합 구하기
	    JavaPairRDD<Tuple2<Integer, Integer>, Integer> allPairOfFriend = friendPair.flatMapToPair( x-> {
	        List<Tuple2< Tuple2<Integer, Integer>, Integer >> list = new ArrayList();
	        List<Integer> friend_List = x._2;
	        int size = friend_List.size();
	        for(int i=0;i<size;i++) {
	            for(int j=i+1;j<size;j++) {
	            	if(friend_List.get(i) < friend_List.get(j)){ 
	            		list.add(new Tuple2(new Tuple2(friend_List.get(i),friend_List.get(j)),1));
	            	}
	            	else {
	            		list.add(new Tuple2(new Tuple2(friend_List.get(j),friend_List.get(i)),1));
	            	}
	            }
	            list.add(new Tuple2(new Tuple2(x._1, x._2.get(i)),Integer.MIN_VALUE/2 -1));
	        }
	        return list.iterator();
	    }).reduceByKey( (a, b) -> a + b ).filter(x -> x._2 > 0).persist(StorageLevel.MEMORY_AND_DISK());
	    
	    JavaPairRDD<Tuple2<Integer, Integer>, Float> similarityList = allPairOfFriend.flatMapToPair(x->{
	    	List<Tuple2<Tuple2<Integer, Integer>, Float>> list = new ArrayList<>();
	    	HashMap<Integer,Integer> num = numOfFriends2.value();
            if(!(((num.get(x._1._1) + num.get(x._1._2)) - x._2) == 0)) {
            	float similarity = (float) x._2 / ((num.get(x._1._1) + num.get(x._1._2)) - x._2);		// similarity (jaccard)
            	if(similarity >=jaccard) {
            		list.add(new Tuple2(new Tuple2(x._1._1, x._1._2), similarity));
                }
            }else {
            	list.add(new Tuple2(new Tuple2(x._1._1, x._1._2), 1.0));
            }
	    	return list.iterator();
	    			
	    }).persist(StorageLevel.MEMORY_AND_DISK());     
	    
	    String distance_filePath = args[1];
	    
	    JavaRDD<String> distance_data = ctx.textFile(distance_filePath);
	    
	    // 유사도가 확인된 pair 거리 구하기
	    JavaPairRDD<Integer, Tuple2<Float, Float>> distanceOfPair = distance_data.flatMapToPair(
				line -> {
					String[] a = line.split("\t");
					List<Tuple2<Integer, Tuple2<Float, Float>>> outList = new ArrayList<>();
					int i1 = Integer.parseInt(a[0]);
					float i2 = Float.parseFloat(a[1]);
					float i3 = Float.parseFloat(a[2]);
					outList.add(new Tuple2(i1,new Tuple2(i2, i3)));
					return outList.iterator();
				}
		).sortByKey(true).persist(StorageLevel.MEMORY_AND_DISK());
	    
	    //Broadcast<List<Tuple2<Integer,Tuple2<Float, Float>>>> distance = ctx.broadcast(distanceOfPair.collect());
	    Broadcast<HashMap<Integer,Tuple2<Float, Float>>> distance2 = ctx.broadcast(new HashMap(distanceOfPair.collectAsMap()));
	    JavaPairRDD<Double, Tuple2<Tuple2<Integer, Integer>, Float>> similarity_pair = similarityList.mapToPair(x->{
	    	HashMap<Integer,Tuple2<Float, Float>> dis = distance2.value();
	    	Tuple2<Double, Tuple2<Tuple2<Integer, Integer>, Float>> out = new Tuple2(Math.sqrt(Math.pow(dis.get(x._1._1)._1 - dis.get(x._1._2)._1, 2) + Math.pow(dis.get(x._1._1)._2 - dis.get(x._1._2)._2, 2)), x);
	        return out;
	    }).sortByKey(true).persist(StorageLevel.MEMORY_AND_DISK());
	    
	    List<Tuple2<Double, Tuple2<Tuple2<Integer, Integer>, Float>>> result_list = similarity_pair.collect();
	    
	    // 최종 결과 출력
	    try{
	        BufferedWriter writer = new BufferedWriter(new FileWriter("./result_" + jaccard +"_"+ k + ".txt"));
	        for(int i = 0; i < k; i++) {
	        	writer.write(result_list.get(i)._2._1._1 + "\t" + result_list.get(i)._2._1._2 + "\t" + result_list.get(i)._1 + "\t" + result_list.get(i)._2._2 +"\n");
	        }
	        writer.close();
	    } catch (IOException e) {
	    }
		ctx.close();
	}
}