package pt.ulisboa.tecnico.cnv.imageproc;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;

public class EC2Utility {

    private static String AWS_REGION = "us-east-1";
    private static String AMI_ID = "ami-0a5dd0179de745d4c";
    private static String KEY_NAME = "cnv-lab-ssh-1";
    private static String SEC_GROUP_ID = "sg-04e43cb406b45627c";

    public static AmazonEC2 getEC2Client() {
        AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard().withRegion(AWS_REGION)
                .withCredentials(new EnvironmentVariableCredentialsProvider()).build();
        return ec2;
    }

    public static AmazonCloudWatch getCloudWatch() {
        AmazonCloudWatch cloudWatch = AmazonCloudWatchClientBuilder.standard().withRegion(AWS_REGION)
                .withCredentials(new EnvironmentVariableCredentialsProvider()).build();
        return cloudWatch;
    }

    public static Set<Instance> getInstances(AmazonEC2 ec2) throws Exception {
        Set<Instance> instances = new HashSet<Instance>();
        for (Reservation reservation : ec2.describeInstances().getReservations()) {
            instances.addAll(reservation.getInstances());
        }
        return instances;
    }

    public static List<Instance> getRunningInstances(AmazonEC2 ec2) throws Exception {
        Set<Instance> instances = getInstances(ec2);
        return instances.stream().filter(i -> i.getPublicIpAddress() != null).collect(Collectors.toList());
    }

    public static RunInstancesResult runNewInstance(AmazonEC2 ec2) {
        return runNewInstances(ec2, 1);
    }

    public static RunInstancesResult runNewInstances(AmazonEC2 ec2, int numOfInstances) {
        if (numOfInstances > 5)
            return new RunInstancesResult(); // Just for security's sake
        System.out.println("Starting a new instance.");
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
        runInstancesRequest.withImageId(AMI_ID)
                .withInstanceType("t2.micro")
                .withMinCount(numOfInstances)
                .withMaxCount(numOfInstances)
                .withKeyName(KEY_NAME)
                .withSecurityGroupIds(SEC_GROUP_ID)
                .withMonitoring(true);
        RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
        return runInstancesResult;
    }

    public static String getInstanceId(RunInstancesResult runInstancesResult) {
        String newInstanceId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();
        return newInstanceId;
    }

    public static void terminateInstance(String instanceId, AmazonEC2 ec2) {
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instanceId);
        ec2.terminateInstances(termInstanceReq);
    }

    public static void printAse(AmazonServiceException ase) {
        System.out.println("Caught Exception: " + ase.getMessage());
        System.out.println("Reponse Status Code: " + ase.getStatusCode());
        System.out.println("Error Code: " + ase.getErrorCode());
        System.out.println("Request ID: " + ase.getRequestId());
    }
}
