package ch.epfl.systemf;


import org.json.JSONObject;
import org.json.JSONWriter;

import java.io.*;
import java.util.*;
import java.util.stream.Stream;

public class FileLogger {

    /*******************************************************
     **************** Event construct ******************
     *******************************************************/

    /**************
     ********* Event
     **************/
    public static sealed abstract class Event {
        public static int logLabel(EventType type, LabelPos pos, String nodeId, Data data) {
            long eventId = switch (pos){
                case START -> enterEvent();
                case END -> exitEvent();
                case UPDATE -> nextId();
            };

            LabelDescription label = type.label(pos, eventId, nodeId, data);
            json.array();
            label.toJsonArray().forEach(json::value);
            json.endArray();
            return 0;
        }

    }

    public record EventType(String type, String kind) {
        public LabelDescription label(LabelPos pos, long eventId, String nodeId, Data data) {
            return new LabelDescription(pos, eventId, nodeId, type, kind, data);
        }
    }

    public static final class SimpleFlow extends Event {
        private static final EventType type = new EventType("flow", "simple");



        public static int enter(String nodeId) {
            return Event.logLabel(type, LabelPos.START, nodeId, new Empty());
        }

        public static int exit(String nodeId) {
            return Event.logLabel(type, LabelPos.END, nodeId, new Empty());
        }
    }

    public static final class SimpleStatement extends Event {
        private static final EventType type = new EventType("statement", "simple");



        public static int enter(String nodeId) {
            return Event.logLabel(type, LabelPos.START, nodeId, new Empty());
        }

        public static int exit(String nodeId, Object result) {
            return Event.logLabel(type, LabelPos.END, nodeId, new Result(result));
        }
    }

    public static final class VoidStatement extends Event {

        public static Data data(LabelPos pos) {
            return new Empty();
        }

        private static final EventType type = new EventType("statement", "void");

        public static int enter(String nodeId) {
            return Event.logLabel(type, LabelPos.START, nodeId, new Empty());
        }

        public static int exit(String nodeId) {
            return Event.logLabel(type, LabelPos.END, nodeId, new Empty());
        }
    }

    public static final class SimpleExpression extends Event {
        private static final EventType type = new EventType("expression", "simple");



        public static int enter(String nodeId) {
            return Event.logLabel(type, LabelPos.START, nodeId, new Empty());
        }

        public static int exit(String nodeId, Object result) {
            return Event.logLabel(type, LabelPos.END, nodeId, new Result(result));
        }
    }

    public static final class Update extends Event {
        private static final EventType type = new EventType("update", "simple");



        public static int write(String nodeId, String name, Object result) {
            return Event.logLabel(type, LabelPos.UPDATE, nodeId, new Write(name, result));
        }

    }

    /**************
     ********* LabelDescription
     **************/

    public record LabelDescription(LabelPos pos, long eventId, String nodeId, String kind, String type, Data data) {
        List<Object> toJsonArray() {
            return Stream.concat(
                            Stream.of(pos.token, eventId, nodeId, kind, type),
                            data.jsonArray().stream())
                    .toList();
        }
    }

    /**************
     ********* Data types
     **************/

    public sealed interface Data {
        public List<Object> jsonArray();
    }

    static final class Empty implements Data {
        @Override
        public List<Object> jsonArray() {
            return List.of();
        }
    }

    static final class Result implements Data {
        private final Object value;

        public Result(Object value) {
            this.value = value;
        }

        @Override
        public List<Object> jsonArray() {
            return List.of(valueRepr(value));
        }
    }

    static final class Write implements Data {
        final String name;
        final Object value;

        public Write(String name, Object value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public List<Object> jsonArray() {
            return List.of(name, valueRepr(value));
        }
    }

    /**************
     ********* Label pos
     **************/


    public enum LabelPos {
        START("start"),
        UPDATE("update"),
        END("end");

        private final String token;

        LabelPos(String token) {
            this.token = token;
        }
    }

    /*******************************************************
     **************** data structures ******************
     *******************************************************/

    private record EventInfo(long eventId){}
    private static Stack<EventInfo> context = new Stack<>();



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
     **************** Log methods ******************
     *******************************************************/

    /**************
     ********* value
     **************/

    private static JSONObject valueRepr(Object value) {
        return switch (value) {
            case null -> {
                yield simpleValue("null", "");
            }
            case Integer i -> {
                yield simpleValue("int", i);
            }
            case Long l -> {
                yield simpleValue("long", l);
            }
            default -> throw new UnsupportedOperationException();
        };
    }

    private static JSONObject simpleValue(String type, Object value) {
        return new JSONObject(Map.of("type", type, "value", value));
    }


    /**************
     ********* identifier
     **************/

    private static JSONObject localIdentifier(String name) {
        return new JSONObject(Map.of("type", "local", "name", name));
    }


    /**************
     ********* event id
     **************/

    private static long enterEvent(){
        long id = nextId();
        context.push(new EventInfo(id));
        return id;
    }

    private static long currentEvent(){
        EventInfo eventInfo = context.peek();
        return eventInfo.eventId;
    }

    private static long exitEvent(){
        EventInfo eventInfo = context.pop();
        return eventInfo.eventId;
    }


    private static long nextId() {
        return eventCounter++;
    }

}
