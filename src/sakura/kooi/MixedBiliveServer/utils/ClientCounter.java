package sakura.kooi.MixedBiliveServer.utils;

import com.google.common.base.Joiner;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class ClientCounter {
    private TreeMap<String, AtomicLong> counter = new TreeMap<>();

    public long getCount(String type) {
        return counter.getOrDefault(type, new AtomicLong()).get();
    }

    public Set<Map.Entry<String, AtomicLong>> entrySet() {
        return counter.entrySet();
    }

    public void increment(String type) {
        counter.computeIfAbsent(type, key -> new AtomicLong()).incrementAndGet();
    }

    public String report() {
        return Joiner.on(" | ").join(counter.entrySet().stream().map(entry -> entry.getKey()+" x"+entry.getValue()).collect(Collectors.toList()));
    }
}
