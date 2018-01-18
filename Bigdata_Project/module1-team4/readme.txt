/home/hadoop/spark/bin/spark-submit --class "m1.Recommender" --master spark://192.168.0.36:7077 fileName textFile GPStextFile jaccard k

/home/hadoop/spark/bin/spark-submit --class "m1.Recommender" --master spark://192.168.0.36:7077 Bigdata.jar /home/hadoop/data/email-Enron.txt /home/hadoop/data/email-Enron-GPS.txt 0.6 100

/home/hadoop/spark/bin/spark-submit --class "m1.Recommender" --master spark://192.168.0.36:7077 Bigdata.jar /home/hadoop/data/email-Enron.txt /home/hadoop/data/email-Enron-GPS.txt 0.7 100

/home/hadoop/spark/bin/spark-submit --class "m1.Recommender" --master spark://192.168.0.36:7077 Bigdata.jar /home/hadoop/data/email-Enron.txt /home/hadoop/data/email-Enron-GPS.txt 0.8 100
