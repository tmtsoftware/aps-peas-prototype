package peas.computationassembly;

import peas.computationassembly.metadata.ComputationParameter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
public class Configuration {
    private static final Configuration CONFIGURATION = new Configuration();
    private final Map<String, Object> data = new ConcurrentHashMap<>();

    public Configuration() {

        float stepCount = 9;
        float stepSizeNm = 10;

        int[] mirrorConfig = new int[36];
        for (int i=0; i<36; i++) {
            mirrorConfig[i] = 1;
        }

        data.put("stepCount", stepCount);
        data.put("stepSizeNm", stepSizeNm);
        data.put("mirrorConfig", mirrorConfig);
    }

    public static Configuration getInstance() {
        return CONFIGURATION;
    }

    public Object get(String key) {
        return data.get(key);
    }

    private class SplitArray {
        public float[] xArray;
        public float[] yArray;
    }



}


