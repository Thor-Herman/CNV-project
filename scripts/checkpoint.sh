#!/bin/bash
# Syntax:  ./checkpoint.sh <ip> <port> <input image>
# Example: ./checkpoint.sh 127.0.0.1 8000 res/Ct0zUz-XgAAV69z.jpg
HOST=$1
PORT=$2
INPUT=$3
function test_batch_requests {
	TRANSFORMATION=blurimage
	REQUESTS=1
	CONNECTIONS=1
	echo "data:image/jpg;base64,$(base64 --wrap=0 $INPUT)" > /tmp/image.body
	ab -n $REQUESTS -c $CONNECTIONS -p /tmp/image.body "$HOST:$PORT/$TRANSFORMATION"
}
function test_single_requests {
	BODY=$(base64 --wrap=0 $INPUT)
	BODY=$(echo "data:image/jpg;base64,$BODY")
	for TRANSFORMATION in "blurimage" "enhanceimage" "detectqrcode" "classifyimage"
	do
		curl -s -d $BODY $HOST:$PORT/$TRANSFORMATION -o /tmp/$TRANSFORMATION-image.dat
		cat /tmp/$TRANSFORMATION-image.dat | base64 -d > /tmp/$TRANSFORMATION-image.png
	done
}
#test_single_requests
test_batch_requests
