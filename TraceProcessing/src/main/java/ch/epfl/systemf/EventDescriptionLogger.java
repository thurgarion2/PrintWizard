package ch.epfl.systemf;

import java.io.*;

public class EventDescriptionLogger {
    static Writer writer;

    static {
        try {
            writer = new BufferedWriter(new FileWriter("./instructions_bytecode.txt"));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void store(int id, int varIndex, String className, String funName, int line) {
        try {
            writer.write(id+", store, "+varIndex+"\n");
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void branch(int id, String className, String funName, int line)  {
        try {
            writer.write(id+", if"+"\n");
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
