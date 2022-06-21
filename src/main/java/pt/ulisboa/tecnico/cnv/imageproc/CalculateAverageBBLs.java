package pt.ulisboa.tecnico.cnv.imageproc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;

public class CalculateAverageBBLs {

    public static void main(String[] args) {
        String[] paths = { "/blurimage", "/enhanceimage", "/classifyimage", "/detectqrcode" };
        AmazonDynamoDB db = DynamoDBUtil.getDynamoDB();
        for (String path : paths) {
            HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
            Condition pathCondition = new Condition().withComparisonOperator(ComparisonOperator.EQ.toString())
                    .withAttributeValueList(new AttributeValue(path));
            scanFilter.put("path", pathCondition);
            ScanRequest scanRequest = new ScanRequest("vms2").withScanFilter(scanFilter);
            ScanResult scanResult = db.scan(scanRequest);

            long totalBBLs = 0;
            long totalPixels = 0;
            for (Map<String, AttributeValue> vals : scanResult.getItems()) {
                AttributeValue bblValue = vals.get("bbls");
                totalBBLs += Long.parseLong(bblValue.getN());
                AttributeValue resValue = vals.get("resolution");
                totalPixels += Long.parseLong(resValue.getN());
            }
            long avg = Math.round(totalBBLs / totalPixels);
            String pathFormatted = path.equals("/blurimage") ? "/blurimage\t" : path;
            System.out.println("Path:" + pathFormatted + "\tvalue: " + avg);
        }
    }
}
