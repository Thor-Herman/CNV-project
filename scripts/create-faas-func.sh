aws lambda create-function \
        --function-name eg-lambda \
        --zip-file fileb://../target/lab-faas-1.0-SNAPSHOT-jar-with-dependencies.jar \
        --handler pt.ulisboa.tecnico.cnv.faas.Handler \
        --runtime java11 \
        --timeout 5 \
        --memory-size 256 \
        --role arn:aws:iam::$AWS_ACCOUNT_ID:role/lambda-role
