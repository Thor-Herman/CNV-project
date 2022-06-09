aws lambda invoke --function-name eg-lambda out --payload '{ "number": "10" }' --log-type Tail --query 'LogResult' --output text |  base64 -d
