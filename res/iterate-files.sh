#!/bin/bash
instanceAddress=127.0.0.1

for file in *.jpg; do 
    if [ -f "$file" ]; then 
        echo "$file" 
        body=$(base64 --wrap=0 $file)
        body=$(echo "data:image/jpg;base64,$body")
        curl -s -d $body $instanceAddress:8000/blurimage -o /tmp/output.tmp
        curl -s -d $body $instanceAddress:8000/enhanceimage -o /tmp/output.tmp
        curl -s -d $body $instanceAddress:8000/classifyimage -o /tmp/output.tmp
        curl -s -d $body $instanceAddress:8000/detectqrcode -o /tmp/output.tmp
    fi  
done
