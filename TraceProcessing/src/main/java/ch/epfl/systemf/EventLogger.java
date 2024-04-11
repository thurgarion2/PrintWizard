package ch.epfl.systemf;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public class EventLogger {

    static Writer writer;

    static {
        try {
            writer = new BufferedWriter(new FileWriter("./trace.txt"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static long eventId=0;

    private static long newId(){
        return eventId++;
    }

    public static void store(int nodeId, int value){
        long id = newId();
        try {
            writer.write(id+", store, "+nodeId+", "+value+"\n");
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void branch(int nodeId, boolean value){
        long id = newId();
        try {
            writer.write(id+", if, "+nodeId+", "+value+"\n");
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



}
