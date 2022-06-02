package pt.ulisboa.tecnico.cnv.imageproc;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.MonitorInstancesRequest;

public class AutoScaler implements Runnable {

    private AmazonEC2 ec2;
    private AmazonCloudWatch cloudWatch;
    private static final int OBS_TIME_MINUTES = 2;
    private static final int OBS_TIME_MS = 1000 * 60 * OBS_TIME_MINUTES;
    private static final int MIN_VM_AMOUNT = 1; // TODO: Validate that min is less than max
    private static final int MAX_VM_AMOUNT = 3;

    public static Map<String, VM> vms = new ConcurrentHashMap<>();

    public AutoScaler(AmazonEC2 ec2, AmazonCloudWatch cloudWatch) {
        this.ec2 = ec2;
        this.cloudWatch = cloudWatch;
        try {
            loadVMsFromAmazon();
            if (vms.values().size() < MIN_VM_AMOUNT) {
                launchVMsUntilMinimumReached();
            }
            printVMs();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void launchVMsUntilMinimumReached() {
        int currentNumOfVMs = vms.values().size();
        int desiredNumOfVMs = MIN_VM_AMOUNT - currentNumOfVMs;
        System.out.println(String.format("Launching %s number of instances", desiredNumOfVMs));
        if (0 < desiredNumOfVMs && desiredNumOfVMs < MAX_VM_AMOUNT) {
            EC2Utility.runNewInstances(ec2, desiredNumOfVMs);
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                System.out.println("Monitoring...");

                Dimension instanceDimension = new Dimension();
                instanceDimension.setName("InstanceId");
                Double totalAvg = 0.0;

                Set<Instance> instances = EC2Utility.getInstances(ec2);
                for (Instance instance : instances) {
                    String iid = instance.getInstanceId();
                    String state = instance.getState().getName();
                    System.out.println(iid);
                    if (state.equals("running")) {
                        instanceDimension.setValue(iid);
                        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                                .withStartTime(new Date(new Date().getTime() - OBS_TIME_MS))
                                .withNamespace("AWS/EC2")
                                .withPeriod(60)
                                .withMetricName("CPUUtilization")
                                .withStatistics("Average")
                                .withDimensions(instanceDimension)
                                .withEndTime(new Date());
                        System.out.println(cloudWatch.getMetricStatistics(request));
                        Double instanceAvg = 0.0; // FOR SOME REASON QUERYING FOR JUST 1 MINUTE DOESNT WORK. SO HAVE TO GET SEVERAL DATA POINTS AND DIVIDE. 
                        for (Datapoint dp : cloudWatch.getMetricStatistics(request).getDatapoints()) {
                            instanceAvg += dp.getAverage();
                            System.out.println(" CPU utilization for instance " + iid + " = " + instanceAvg);
                        }
                        totalAvg += instanceAvg / OBS_TIME_MINUTES;
                        System.out.println("Total average: " + totalAvg);
                    }
                }
                Thread.sleep(OBS_TIME_MS);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void loadVMsFromAmazon() throws Exception {
        List<Instance> instances = EC2Utility.getRunningInstances(ec2);
        for (Instance instance : instances) {
            String id = instance.getInstanceId();
            String ip = instance.getPublicIpAddress();
            VM vm = new VM(id, ip, false);
            vms.put(id, vm);
        }
    }

    private void printVMs() {
        System.out.println("Printing vms: ");
        vms.keySet().stream().forEach(vm -> System.out.println(vms.get(vm)));
    }
}
