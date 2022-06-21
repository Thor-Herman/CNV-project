package pt.ulisboa.tecnico.cnv.imageproc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import com.amazonaws.services.dynamodbv2.util.TableUtils.TableNeverTransitionedToStateException;

public class DynamoDBUtil {

    private static String AWS_REGION = "us-east-1";

    public static AmazonDynamoDB getDynamoDB() {
        return AmazonDynamoDBClientBuilder.standard().withCredentials(new EnvironmentVariableCredentialsProvider())
                .withRegion(AWS_REGION)
                .build();
    }

    public static void createNewTable(AmazonDynamoDB dynamoDB, String tableName, String primaryKey)
            throws TableNeverTransitionedToStateException, InterruptedException {
        CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
                .withKeySchema(new KeySchemaElement().withAttributeName(primaryKey).withKeyType(KeyType.HASH))
                .withAttributeDefinitions(
                        new AttributeDefinition().withAttributeName(primaryKey)
                                .withAttributeType(ScalarAttributeType.S))
                .withProvisionedThroughput(
                        new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));
        TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
        describeTable(dynamoDB, tableName);
    }

    public static void describeTable(AmazonDynamoDB dynamoDB, String tableName)
            throws TableNeverTransitionedToStateException, InterruptedException {
        TableUtils.waitUntilActive(dynamoDB, tableName);
        DescribeTableRequest describeTableRequest = new DescribeTableRequest().withTableName(tableName);
        TableDescription tableDescription = dynamoDB.describeTable(describeTableRequest).getTable();
        System.out.println("Table Description: " + tableDescription);
    }

    public static void putNewResult(AmazonDynamoDB dynamoDB, String tableName, UUID id, String path, long resolution,
            long basicblocks) {
        PutItemResult result = dynamoDB
                .putItem(new PutItemRequest(tableName, newItem(id, path, resolution, basicblocks)));
        System.out.println(result);
    }

    public static ScanResult filterDBForResolution(AmazonDynamoDB dynamoDB, String tableName, String path, long gtValue,
            long ltValue) {
        HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
        Condition resolutionCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.BETWEEN.toString())
                .withAttributeValueList(new AttributeValue().withN(Long.toString(gtValue)),
                        new AttributeValue().withN(Long.toString(ltValue)));
        Condition pathCondition = new Condition().withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue(path));
        scanFilter.put("resolution", resolutionCondition);
        scanFilter.put("path", pathCondition);
        ScanRequest scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
        ScanResult scanResult = dynamoDB.scan(scanRequest);
        // System.out.println("Result: " + scanResult);
        return scanResult;
    }

    public static void printASE(AmazonServiceException ase) {
        System.out.println("Caught an AmazonServiceException, which means your request made it "
                + "to AWS, but was rejected with an error response for some reason.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("AWS Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }

    public static void printACE(AmazonClientException ace) {
        System.out.println("Caught an AmazonClientException, which means the client encountered "
                + "a serious internal problem while trying to communicate with AWS, "
                + "such as not being able to access the network.");
        System.out.println("Error Message: " + ace.getMessage());
    }

    private static Map<String, AttributeValue> newItem(UUID id, String path, long resolution, long bbls) {
        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        item.put("id", new AttributeValue(id.toString()));
        item.put("path", new AttributeValue(path));
        item.put("resolution", new AttributeValue().withN(Long.toString(resolution)));
        item.put("bbls", new AttributeValue().withN(Long.toString(bbls)));
        return item;
    }

}
