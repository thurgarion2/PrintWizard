package ch.epfl.systemf;

import java.io.*;
import java.util.Arrays;
import java.util.Stack;
import java.util.stream.Collectors;

public class PrintLogger implements Logger{
    private static record Context(int nodeId, long eventId){};
    private static final Stack<Context> parent = new Stack<>();

    private static long eventCounter = 0;
    private final static String fileName = "eventTrace.txt";

    private final static OutputStream out;
    private final static OutputStreamWriter writer;

    private final static BufferedWriter print;

    static {
        parent.push(new Context(-1, nextId()));


        try {
            out = new FileOutputStream(fileName);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        writer = new OutputStreamWriter(out);
        print = new BufferedWriter(writer);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if(print!=null){
                    print.close();
                }
                if(writer!=null){
                    writer.close();
                }
                if(out!=null){
                    out.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }


    public static  int enter(int nodeId){
        long eventId = nextId();
        parent.push(new Context(nodeId, eventId));
        print(Long.toString(eventId), "enter", Integer.toString(nodeId));
        return 0;
    }
    public  static int exitEvaluation(int nodeId, Evaluation eval){
        long eventId = exitScope(nodeId);


        print(Long.toString(eventId),
                Integer.toString(nodeId),
                "eval",
                eval.hasResult() ? safeToString(eval.result()) : "noResult",
                eval.hasAssign() ? eval.varName() : "noAssign",
                eval.hasAssign() ? safeToString(eval.value()) : "noAssign");
        return 0;
    }

    private static String safeToString(Object o){
        if(o==null)
            return "null";
        return o.toString();
    }


    public static int exitLogical(int nodeId, String description) {
        long eventId = exitScope(nodeId);

        print(Long.toString(eventId),
                Integer.toString(nodeId),
                "syntax",
                Long.toString(parent()),
                description);
        return 0;
    }

    private static long parent(){
        return parent.peek().eventId;
    }

    private static long exitScope(int nodeId){
        Context scope = parent.pop();
        if(scope.nodeId!= nodeId)
            throw new IllegalStateException("Invalid exit event "+nodeId+" parent "+scope.nodeId);
        return scope.eventId;
    }

    private static void print(String ... args){
        String joined = String.join(", ", args);

        try {
            print.write(joined);
            print.newLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static long nextId(){
        return eventCounter++;
    }
}
