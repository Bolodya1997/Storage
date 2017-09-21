Distributed key-value storage.
Key format - string;
Value format - integer;

Configuration:
1)  nodes addresses list is in:
src/main/resources/ru/nsu/fit/popov/storage/config.properties
2)  replication degree is in:
src/main/java/ru/nsu/fit/popov/storage/memory/ReplicationPolicy.java (line 9)
3) heartbeat timeout is in:
src/main/java/ru/nsu/fit/popov/storage/failuredetector/FailureDetector.java (line 51)

To build .jar file:
  mvn clean compile assembly:single

To start:
  java -jar name.jar node_number

Commands:
  write key value
  read key
  exit
