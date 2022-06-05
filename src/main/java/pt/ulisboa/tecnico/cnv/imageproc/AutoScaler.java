package pt.ulisboa.tecnico.cnv.imageproc;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceNetworkInterface;
import com.amazonaws.services.ec2.model.RunInstancesResult;

public class AutoScaler implements Runnable {

    private AmazonEC2 ec2;
    private AmazonCloudWatch cloudWatch;

    private static final int OBS_TIME_MINUTES = 2;
    private static final int OBS_TIME_MS = 1000 * 60 * OBS_TIME_MINUTES;
    private static final int MIN_VM_AMOUNT = 1; // TODO: Validate that min is less than max
    private static final int MAX_VM_AMOUNT = 3;
    private static final float DECREASE_VMS_THRESHOLD = 3f; // Must be number between 0 and 100;
    private static final float INCREASE_VMS_THRESHOLD = 5; // Must be number between 0 and 100;

    public static Map<String, VM> vms = new ConcurrentHashMap<>();

    public AutoScaler(AmazonEC2 ec2, AmazonCloudWatch cloudWatch) {
        this.ec2 = ec2;
        this.cloudWatch = cloudWatch;
        validateConstants();
        initialize();
    }

    private void validateConstants() {
        boolean vmAmountsSatisfyConstraints = MIN_VM_AMOUNT >= 0 && MAX_VM_AMOUNT >= MIN_VM_AMOUNT;
        boolean thresholdsSatisfyConstraints = DECREASE_VMS_THRESHOLD >= 0
                && INCREASE_VMS_THRESHOLD > DECREASE_VMS_THRESHOLD && INCREASE_VMS_THRESHOLD < 100;
        if (!vmAmountsSatisfyConstraints && thresholdsSatisfyConstraints)
            throw new IllegalStateException("AS constants do not satisfy constraints");
    }

    private void initialize() {
        try {
            loadVMsFromAmazon();
            if (vms.values().size() < MIN_VM_AMOUNT) {
                launchVMsUntilMinimumReached();
            }
            printVMs();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e);
        }
    }

    private void launchVMsUntilMinimumReached() throws InterruptedException {
        int currentNumOfVMs = vms.values().size();
        int numOfVMsToStart = MIN_VM_AMOUNT - currentNumOfVMs;
        System.out.println(String.format("Launching %s number of instances", numOfVMsToStart));
        if (0 < numOfVMsToStart && numOfVMsToStart + currentNumOfVMs < MAX_VM_AMOUNT) {
            startNewInstances(numOfVMsToStart);
        }
    }

    private void startNewInstances(int numOfVMsToStart) {
        RunInstancesResult res = EC2Utility.runNewInstances(ec2, numOfVMsToStart);

        for (Instance instance : res.getReservation().getInstances()) {
            String iid = instance.getInstanceId();
            System.out.println("Started new instance: " + iid);
            vms.put(iid,
                    new VM(iid, null, false, VMState.PENDING));
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                System.out.println("Monitoring...");

                Dimension instanceDimension = getInstanceDimension();
                Set<Instance> instances = EC2Utility.getInstances(ec2);

                Double totalAvg = 0.0;
                Double highestInstanceAvg = 0.0;
                String highestInstanceAvgId = "";

                for (Instance instance : instances) {
                    Double instanceAvg = processInstanceInRoutine(instance, instanceDimension);
                    if (instanceAvg > highestInstanceAvg) {
                        highestInstanceAvg = instanceAvg;
                        highestInstanceAvgId = instance.getInstanceId();
                    }
                    totalAvg += instanceAvg / OBS_TIME_MINUTES;
                }
                System.out.println("Total average: " + totalAvg);
                scaleVMsAccordingly(totalAvg, highestInstanceAvgId);

                Thread.sleep(OBS_TIME_MS);

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println(e);
            }
        }
    }

    private Double processInstanceInRoutine(Instance instance, Dimension instanceDimension) {
        String iid = instance.getInstanceId();
        String state = instance.getState().getName();
        Double instanceAvg = 0.0;

        if (state.equals("running")) {
            System.out.println("Instance " + iid);
            VM correspondingVM = vms.get(iid);
            checkAndHandleChangedStateSinceLastUpdate(instance, correspondingVM);
            checkAndHandleMarkedForDeletion(iid, correspondingVM);

            instanceDimension.setValue(iid);
            instanceAvg = getInstanceAvg(iid, instanceDimension);
        }
        return instanceAvg;
    }

    private void scaleVMsAccordingly(Double totalAvg, String highestInstanceAvgId) {
        if (totalAvg < DECREASE_VMS_THRESHOLD && getVMsNotMarkedForDeletion().size() > MIN_VM_AMOUNT)
            markInstanceForDeletion(highestInstanceAvgId);
        else if (totalAvg > INCREASE_VMS_THRESHOLD && getVMsNotMarkedForDeletion().size() < MAX_VM_AMOUNT)
            startNewInstances(1);
    }

    private Dimension getInstanceDimension() {
        Dimension instanceDimension = new Dimension();
        instanceDimension.setName("InstanceId");
        return instanceDimension;
    }

    private Double getInstanceAvg(String iid, Dimension instanceDimension) {
        GetMetricStatisticsRequest request = createMetricsStatisticsRequest(instanceDimension);
        Double instanceAvg = 0.0; // FOR SOME REASON QUERYING FOR JUST 1 MINUTE DOESNT WORK. SO HAVE TO
        // GET SEVERAL DATA POINTS AND DIVIDE.
        for (Datapoint dp : cloudWatch.getMetricStatistics(request).getDatapoints()) {
            instanceAvg += dp.getAverage();
            System.out.println(" CPU utilization for instance " + iid + " = " + instanceAvg);
        }

        return instanceAvg;
    }

    private void checkAndHandleChangedStateSinceLastUpdate(Instance instance, VM correspondingVM) {
        boolean vmHasChangedStateSinceLastUpdate = correspondingVM.state == VMState.PENDING;
        if (vmHasChangedStateSinceLastUpdate) {
            correspondingVM.state = VMState.RUNNING;
            correspondingVM.ipAddress = instance.getPublicIpAddress();
        }
    }

    private void checkAndHandleMarkedForDeletion(String iid, VM correspondingVM) {
        boolean canShutDownVM = correspondingVM.markedForDeletion
                && correspondingVM.currentAmountOfRequests == 0;
        if (canShutDownVM) {
            EC2Utility.terminateInstance(iid, ec2);
            correspondingVM.state = VMState.TERMINATED;
        }
    }

    private GetMetricStatisticsRequest createMetricsStatisticsRequest(Dimension instanceDimension) {
        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                .withStartTime(new Date(new Date().getTime() - OBS_TIME_MS))
                .withNamespace("AWS/EC2")
                .withPeriod(60)
                .withMetricName("CPUUtilization")
                .withStatistics("Average")
                .withDimensions(instanceDimension)
                .withEndTime(new Date());
        return request;
    }

    private void markInstanceForDeletion(String highestInstanceAvgId) {
        if (highestInstanceAvgId != "") {
            VM vm = vms.get(highestInstanceAvgId);
            if (vm != null) {
                vm.markedForDeletion = true;
                System.out.println("Marked VM with Id " + vm.id + " for deletion");
            } else
                System.out.println(
                        "Tried to get VM with highestInstanceAvg of " + highestInstanceAvgId + " but got null");
        }
        // Have to let LB know that it's marked for deletion?
    }

    private void loadVMsFromAmazon() throws Exception {
        List<Instance> instances = EC2Utility.getRunningInstances(ec2);
        for (Instance instance : instances) {
            String iid = instance.getInstanceId();
            String ip = instance.getPublicIpAddress();
            if (!vms.containsKey(iid) && instance.getState().getName() != "terminated") {
                vms.put(iid, new VM(iid, ip, false, VMState.RUNNING));
            }
        }
    }

    private void printVMs() {
        System.out.println("Printing vms: ");
        vms.keySet().stream().forEach(vm -> System.out.println(vms.get(vm)));
    }

    public static List<VM> getVMsNotMarkedForDeletion() {
        return vms.values().stream().filter(vm -> !vm.markedForDeletion).collect(Collectors.toList());
    }

}
