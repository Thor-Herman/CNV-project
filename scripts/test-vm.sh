#!/bin/bash

source config.sh

# Requesting an instance reboot.
aws ec2 reboot-instances --instance-ids $(cat instance.id)
echo "Rebooting instance to test web server auto-start."

# Letting the instance shutdown.
sleep 1

# Wait for port 8000 to become available.
while ! nc -z $(cat instance.dns) 8000; do
	echo "Waiting for $(cat instance.dns):8000..."
	sleep 0.5
done

body=$(base64 --wrap=0 ~/cnv-shared/cnv22-g16/res/bird.jpg)
body=$(echo "data:image/jpg;base64,$body")
# Sending a query!
echo "Sending a query!"
curl -s -d $body $(cat instance.dns):8000/blurimage -o /tmp/output.tmp
cat /tmp/output.tmp | base64 -d > bird-transformed.jpg
