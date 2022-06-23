package pt.ulisboa.tecnico.cnv.imageproc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DynamoResultsCache {

    private static Map<String, Map<Long, Long>> resToBBLs = new ConcurrentHashMap<>();

    public static void AddResult(String path, long pixels, long bbls) {
        if (!resToBBLs.containsKey(path)) {
            System.out.println("Adding new key");
            resToBBLs.put(path, new ConcurrentHashMap<>());
        }

        resToBBLs.get(path).put(pixels, bbls);
    }

    public static long GetTotalAvg(String path, long pixels, float threshold) {
        if (!resToBBLs.containsKey(path))
            return 0;
        long totalAvg = 0;
        Predicate<Long> resultsFilter = k -> pixels * (1 - threshold) < k && k < pixels * (1 + threshold);
        Map<Long, Long> mapForPath = resToBBLs.get(path);
        totalAvg = mapForPath.keySet().stream()
                .filter(resultsFilter)
                .map(key -> mapForPath.get(key))
                .reduce((long) 0, (acc, val) -> acc += val);

        return totalAvg;
    }

    public static double GetHits(String path, long pixels, float threshold) {
        if (!resToBBLs.containsKey(path))
            return 0;
        double totalHits = 0;
        Predicate<Long> resultsFilter = k -> pixels * (1 - threshold) < k && k < pixels * (1 + threshold);
        Map<Long, Long> mapForPath = resToBBLs.get(path);
        totalHits = (double) mapForPath.keySet().stream()
                .filter(resultsFilter)
                .count();

        return totalHits;
    }

}
