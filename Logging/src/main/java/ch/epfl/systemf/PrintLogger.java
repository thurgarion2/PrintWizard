package ch.epfl.systemf;

import java.io.*;
import java.util.Arrays;
import java.util.Stack;
import java.util.stream.Collectors;

public class PrintLogger implements Logger {
    private static record Context(int nodeId, long eventId) {
    }

    ;
    private static final Stack<Context> parent = new Stack<>();

    private static long eventCounter = 0;
    private final static String fileName = "eventTrace.csv";

    private final static OutputStream out;
    private final static OutputStreamWriter writer;

    private final static BufferedWriter print;

    /*******************************************************
     **************** INITIALIZATION ******************
     *******************************************************/

    static {
        try {
            out = new FileOutputStream(fileName);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        writer = new OutputStreamWriter(out);
        print = new BufferedWriter(writer);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (print != null) {
                    print.close();
                }
                if (writer != null) {
                    writer.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    /*******************************************************
     **************** API methods ******************
     *******************************************************/


    /**************
     ********* Flow methods
     **************/

    public static int enterFlow(int nodeId) {
        long eventId = enterScope(nodeId);
        print(eventId, nodeId, Logger.EventTypes.FLOW_ENTER.name);
        return 0;
    }

    public static int exitFlow(int nodeId) {
        long eventId = exitScope(nodeId);
        print(eventId, nodeId, Logger.EventTypes.FLOW_EXIT.name);
        return 0;
    }

    /**************
     ********* Statement methods
     **************/

    public static int enterStatement(int nodeId) {
        long eventId = enterScope(nodeId);
        print(eventId, nodeId, Logger.EventTypes.STATEMENT_ENTER.name);
        return 0;
    }

    public static int exitStatement(int nodeId) {
        long eventId = exitScope(nodeId);
        print(eventId, nodeId, Logger.EventTypes.STATEMENT_EXIT.name);
        return 0;
    }

    /**************
     ********* Expression methods
     **************/

    public static int enterExpression(int nodeId) {
        long eventId = enterScope(nodeId);
        print(eventId, nodeId, Logger.EventTypes.EXPRESSION_ENTER.name);
        return 0;
    }

    public static int exitExpression(int nodeId, Object result) {
        long eventId = exitScope(nodeId);
        //result should use  toValue function
        print(eventId, nodeId, Logger.EventTypes.EXPRESSION_EXIT.name, result);
        return 0;
    }

    /**************
     ********* Update methods
     **************/

    public static int update(int nodeId, String varName, Object value) {
        long eventId = nextId();
        // value should use toValue function
        print(eventId, nodeId, Logger.EventTypes.UPDATE.name, varName, value);
        return 0;
    }

    /*******************************************************
     **************** Log methods ******************
     *******************************************************/

    private static void print(Object... args) {
        String joined = Arrays.stream(args).map(PrintLogger::safeToString).collect(Collectors.joining(", "));

        try {
            print.write(joined);
            print.newLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String safeToString(Object obj) {
        return obj==null ? "null" : obj.toString();
    }

    /*******************************************************
     **************** Context API ******************
     *******************************************************/

    private static long parent() {
        return parent.peek().eventId;
    }

    private static long enterScope(int nodeId) {
        long eventId = nextId();
        parent.push(new Context(nodeId, eventId));
        return eventId;
    }

    private static long exitScope(int nodeId) {
        Context scope = parent.pop();
        if (scope.nodeId != nodeId)
            throw new IllegalStateException("Invalid exit event " + nodeId + " parent " + scope.nodeId);
        return scope.eventId;
    }

    private static long nextId() {
        return eventCounter++;
    }
}
