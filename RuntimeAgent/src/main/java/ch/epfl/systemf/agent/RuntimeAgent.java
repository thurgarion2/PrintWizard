package ch.epfl.systemf.agent;

import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Optional;

public class RuntimeAgent implements ClassFileTransformer {

    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(
                new ClassFileTransformer() {
                    @Override
                    public byte[] transform(ClassLoader loader,
                                            String className,
                                            Class<?> classBeingRedefined,
                                            ProtectionDomain protectionDomain,
                                            byte[] classfileBuffer) throws IllegalClassFormatException {
                        String path = Optional.ofNullable(protectionDomain.getCodeSource())
                                        .map(CodeSource::getLocation)
                                        .map(URL::toString)
                                        .orElse("Unknown");

                        System.out.println("Path to "+className+" : "+path);
                        return null;
                    }
                }
        );
    }
}
