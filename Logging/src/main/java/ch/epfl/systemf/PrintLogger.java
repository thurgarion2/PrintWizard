package ch.epfl.systemf;


import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONWriter;

import java.io.*;
import java.util.*;

public class PrintLogger implements Logger {
    private static record Context(String nodeId, long eventId) {
    }

    ;
    private static final Stack<Context> parent = new Stack<>();

    private static long eventCounter = 0;
    private final static String fileName = "eventTrace.json";

    private final static OutputStream out;
    private final static OutputStreamWriter writer;

    private final static BufferedWriter print;
    private final static JSONWriter json;


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
        json = new JSONWriter(print);
        json.object();

        // trace field an array
        json.key("trace");
        json.array();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (json != null) {
                    json.endArray();
                    json.endObject();
                }

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

    public static int enterFlow(String nodeId) {
        long eventId = enterScope(nodeId);
        logEvent(eventId, nodeId, Logger.EventTypes.FLOW_ENTER.name);
        return 0;
    }

    public static int exitFlow(String nodeId) {
        long eventId = exitScope(nodeId);
        logEvent(eventId, nodeId, Logger.EventTypes.FLOW_EXIT.name);
        return 0;
    }

    /**************
     ********* Statement methods
     **************/

    public static int enterStatement(String nodeId) {
        long eventId = enterScope(nodeId);
        logEvent(eventId, nodeId, Logger.EventTypes.STATEMENT_ENTER.name);
        return 0;
    }

    public static int exitStatement(String nodeId) {
        long eventId = exitScope(nodeId);
        logEvent(eventId, nodeId, Logger.EventTypes.STATEMENT_EXIT.name);
        return 0;
    }

    /**************
     ********* Expression methods
     **************/

    public static int enterExpression(String nodeId) {
        long eventId = enterScope(nodeId);
        logEvent(eventId, nodeId, Logger.EventTypes.EXPRESSION_ENTER.name);
        return 0;
    }

    public static int exitExpression(String nodeId, Object result) {
        long eventId = exitScope(nodeId);
        //result should use  toValue function
        logEvent(eventId, nodeId, Logger.EventTypes.EXPRESSION_EXIT.name, valueRepr(result));
        return 0;
    }

    /**************
     ********* Update methods
     **************/

    public static int update(String nodeId, String varName, Object value) {
        long eventId = nextId();

        logEvent(eventId, nodeId, Logger.EventTypes.UPDATE.name, localIdentifier(varName), valueRepr(value));
        return 0;
    }

    /*******************************************************
     **************** Log methods ******************
     *******************************************************/

    /**************
     ********* event
     **************/
    private static void logEvent(long eventId, String nodeId, String kind,  Object ... data){
        JSONArray array = new JSONArray(List.of(eventId, nodeId, kind));
        array.putAll(data);
        json.array();
        List.of(eventId, kind, nodeId).forEach(json::value);
        Arrays.stream(data).forEach(json::value);
        json.endArray();
    }

    /**************
     ********* value
     **************/

    private static JSONObject valueRepr(Object value){
        return switch (value){
            case null -> {
                yield simpleValue("null", "");
            }
            case Integer i -> {
                yield simpleValue("int", i);
            }
            case Long l ->  {
                yield  simpleValue("long", l);
            }
            default -> throw new UnsupportedOperationException();
        };
    }

    private static JSONObject simpleValue(String type, Object value){
        return new JSONObject(Map.of("type", type, "value", value));
    }


    /**************
     ********* identifier
     **************/

    private static JSONObject localIdentifier(String name){
        return new JSONObject(Map.of("type", "local", "name", name));
    }

    /*******************************************************
     **************** Context API ******************
     *******************************************************/

    private static long parent() {
        return parent.peek().eventId;
    }

    private static long enterScope(String nodeId) {
        long eventId = nextId();
        parent.push(new Context(nodeId, eventId));
        return eventId;
    }

    private static long exitScope(String nodeId) {
        Context scope = parent.pop();
        if (scope.nodeId != nodeId)
            throw new IllegalStateException("Invalid exit event " + nodeId + " parent " + scope.nodeId);
        return scope.eventId;
    }

    private static long nextId() {
        return eventCounter++;
    }
}
