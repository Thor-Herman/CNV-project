package pt.ulisboa.tecnico.cnv.imageproc;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    private static final int MIN_VM_AMOUNT = 1;
    private static final int MAX_VM_AMOUNT = 3;
    private static final float DECREASE_VMS_THRESHOLD = 3f; // Must be number between 0 and 100;
    private static final float INCREASE_VMS_THRESHOLD = 90f; // Must be number between 0 and 100;
    private static final int HEALTH_CHECK_FREQUENCY = 3;

    private final String ipOfThisVM;

    public static Map<String, VM> vms = new ConcurrentHashMap<>();

    public AutoScaler(AmazonEC2 ec2, AmazonCloudWatch cloudWatch, String ipOfThisVM) {
        this.ec2 = ec2;
        this.cloudWatch = cloudWatch;
        this.ipOfThisVM = ipOfThisVM;
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

                if (instances.size() < MIN_VM_AMOUNT) { // Fault prevention in case VM is suddenly killed
                    launchVMsUntilMinimumReached();
                }

                Double totalAvg = 0.0;
                Double highestInstanceAvg = 0.0;
                String highestInstanceAvgId = "";

                for (Instance instance : instances) {
                    if (ipOfThisVM.equals(instance.getPublicIpAddress()))
                        continue;
                    Double instanceAvg = processInstanceInRoutine(instance, instanceDimension);
                    boolean unhealthyVm = instanceAvg == -1.0f;
                    if (unhealthyVm)
                        markInstanceForDeletion(instance.getInstanceId());
                    else if (instanceAvg > highestInstanceAvg) {
                        highestInstanceAvg = instanceAvg;
                        highestInstanceAvgId = instance.getInstanceId();
                    }
                    totalAvg += unhealthyVm ? 0 : instanceAvg / OBS_TIME_MINUTES;
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

    private boolean healthCheckInstance(Instance instance) {
        VM vm = vms.get(instance.getInstanceId());
        if (vm.cyclesSinceHealthCheck >= HEALTH_CHECK_FREQUENCY) {
            return sendHealthCheckAndReturnResponse(instance);
        } else {
            vm.cyclesSinceHealthCheck++;
            return true;
        }
    }

    private boolean sendHealthCheckAndReturnResponse(Instance instance) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + instance.getPublicIpAddress() + ":" + 8000 + "/healthcheck"))
                .GET()
                .build();
        System.out.println(request);
        int statusCode;
        try {
            statusCode = client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
            vms.get(instance.getInstanceId()).cyclesSinceHealthCheck = 0;
        } catch (Exception e) {
            return false;
        }
        return statusCode == 200;
    }

    private Double processInstanceInRoutine(Instance instance, Dimension instanceDimension) {
        String iid = instance.getInstanceId();
        String state = instance.getState().getName();
        Double instanceAvg = 0.0;

        if (state.equals("running")) {
            System.out.println("Instance " + iid);
            VM correspondingVM = vms.get(iid);
            boolean healthyVM = healthCheckInstance(instance);
            if (!healthyVM)
                return -1.0;
            checkAndHandleChangedStateSinceLastUpdate(instance, correspondingVM);
            checkAndHandleMarkedForDeletion(iid, correspondingVM);

            instanceDimension.setValue(iid);
            instanceAvg = getInstanceAvg(iid, instanceDimension);
            correspondingVM.cpuUtilization = instanceAvg;
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
        boolean vmHasChangedStateSinceLastUpdate = correspondingVM.state == VMState.PENDING
                && instance.getState().getName().equals("running");
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

    private void markInstanceForDeletion(String iid) {
        if (iid != "") {
            VM vm = vms.get(iid);
            if (vm != null) {
                vm.markedForDeletion = true;
                System.out.println("Marked VM with Id " + vm.id + " for deletion");
            } else
                System.out.println(
                        "Tried to get VM with highestInstanceAvg of " + iid + " but got null");
        }
        // Have to let LB know that it's marked for deletion?
    }

    private void loadVMsFromAmazon() throws Exception {
        List<Instance> instances = EC2Utility.getRunningInstances(ec2);
        for (Instance instance : instances) {
            System.out.println(instance);
            String iid = instance.getInstanceId();
            String ip = instance.getPublicIpAddress();
            if (ip.equals(ipOfThisVM))
                continue;
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

    public static List<VM> getVMsRunning() {
        return vms.values().stream().filter(vm -> vm.state == VMState.RUNNING).collect(Collectors.toList());
    }

}
