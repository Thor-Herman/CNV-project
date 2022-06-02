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
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.MonitorInstancesRequest;

public class AutoScaler implements Runnable {

    private AmazonEC2 ec2;
    private AmazonCloudWatch cloudWatch;
    private static final int OBS_TIME = 1000 * 60;

    public static Map<String, VM> vms = new ConcurrentHashMap<>();

    public AutoScaler(AmazonEC2 ec2, AmazonCloudWatch cloudWatch) {
        this.ec2 = ec2;
        this.cloudWatch = cloudWatch;
        try {
            loadVMsFromAmazon();
            printVMs();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(OBS_TIME);
                System.out.println("Monitoring...");

                Dimension instanceDimension = new Dimension();
                instanceDimension.setName("InstanceId");
                Double totalAvg = 0.0;

                Set<Instance> instances = EC2Utility.getInstances(ec2);
                for (Instance instance : instances) {
                    String iid = instance.getInstanceId();
                    String state = instance.getState().getName();
                    if (state.equals("running")) {
                        instanceDimension.setValue(iid);
                        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                                .withStartTime(new Date(new Date().getTime() - OBS_TIME))
                                .withNamespace("AWS/EC2")
                                .withPeriod(60)
                                .withMetricName("CPUUtilization")
                                .withStatistics("Average")
                                .withDimensions(instanceDimension)
                                .withEndTime(new Date());
                        for (Datapoint dp : cloudWatch.getMetricStatistics(request).getDatapoints()) {
                            Double instanceAvg = dp.getAverage();
                            System.out.println(" CPU utilization for instance " + iid + " = " + instanceAvg);
                            totalAvg += instanceAvg;
                        }
                        System.out.println("Total average: " + totalAvg);
                    }
                }
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
