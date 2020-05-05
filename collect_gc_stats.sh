#!/bin/bash
#
# Author: Jonatan Kazmierczak (Jonatan at Son-of-God.info)
#

# define variables
STATS_DIR=stats
RESPONSE_SIZE=50000
RESPONSE_COUNT=99
THREAD_COUNT=3
JAVA_APP="-jar build/libs/request-processor.jar $RESPONSE_SIZE $RESPONSE_COUNT $THREAD_COUNT"
RESPONSE_TIMES_FILE=response_times.txt
# Path to java command from OpenJDK build including Shenandoah GC - i.e. AdoptOpenJDK
JAVA_CMD=/home/jason/jdk-14.0.1+7_hotspot/bin/java
# Path to java command from OpenJDK with OpenJ9 VM
JAVA_J9_CMD=/home/jason/jdk-14.0.1+7-jre_openj9_05_04/bin/java
## Path to java command from Zing VM
#JAVA_ZING_CMD=/opt2/zing-jdk11.0.0-19.10.1.0-3/bin/java
OPTIONS="-Xmx1000m -Xms1000m"

echo "-- Java GC Performance Statistics Collector --"
echo "Please make sure, that you are not running it in virtualized environment (i.e. Docker) and that your CPU runs on a constant frequency"
echo

$JAVA_CMD -version
echo

OUT_FILE=$STATS_DIR/gc.txt

mkdir -p $STATS_DIR

rm -f $OUT_FILE

# OpenJ9

arr=(gencon balanced optavgpause optthruput metronome)
i=0

while [ $i -lt ${#arr[@]} ]
do
	echo -n vm=OpenJ9,gc=${arr[$i]}
	echo -n vm=OpenJ9,gc=${arr[$i]}, >> $OUT_FILE
	/usr/bin/time $JAVA_J9_CMD $OPTIONS -Xgcpolicy:${arr[$i]} -Xverbosegclog:$STATS_DIR/verbose-${arr[$i]}.txt $JAVA_APP 2>>$OUT_FILE
	sleep 1
	mv $RESPONSE_TIMES_FILE $STATS_DIR/${arr[$i]}.csv
	i=`expr $i + 1`
done


# HotSpot

arr=(Serial Parallel G1 Z Shenandoah)
i=0

while [ $i -lt ${#arr[@]} ]
do
	echo -n vm=HotSpot,gc=${arr[$i]}
	echo -n vm=HotSpot,gc=${arr[$i]}, >> $OUT_FILE
	/usr/bin/time $JAVA_CMD $OPTIONS -XX:+UnlockExperimentalVMOptions -XX:+Use${arr[$i]}GC $JAVA_APP 2>>$OUT_FILE
	sleep 1
	mv $RESPONSE_TIMES_FILE $STATS_DIR/${arr[$i]}.csv
	i=`expr $i + 1`
done

# Zing

#echo -n vm=Zing,gc=C4, >> $OUT_FILE
#/usr/bin/time $JAVA_ZING_CMD $JAVA_APP 2>>$OUT_FILE
#mv $RESPONSE_TIMES_FILE $STATS_DIR/C4.csv

# SVM

#echo -n vm=Substrate,gc=SVM, >> $OUT_FILE
#/usr/bin/time ./request-processor $RESPONSE_SIZE $RESPONSE_COUNT $THREAD_COUNT 2>>$OUT_FILE
#mv $RESPONSE_TIMES_FILE $STATS_DIR/SVM.csv
