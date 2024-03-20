package ch.epfl.systemf.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;

public class RuntimeAgent implements ClassFileTransformer {

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("hello world");
    }
}
