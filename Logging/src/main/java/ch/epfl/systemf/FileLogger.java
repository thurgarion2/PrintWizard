package ch.epfl.systemf;


import org.json.JSONArray;
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
        public static int logLabel(EventType type, LabelPos pos, String nodeId, List<Data> data) {
            long eventId = switch (pos) {
                case START -> enterEvent();
                case END -> exitEvent();
                case CALL -> currentEvent();
                case UPDATE -> nextId();
            };


            json.array();
            pos.serializeToJson(type, new DynamicEventInfo(eventId, nodeId), data).forEach(json::value);
            json.endArray();
            return 0;
        }

    }

    public record DynamicEventInfo(long eventId, String nodeId) {
    }

    public record EventType(String type, String kind) {
    }

    /********
     **** simple
     ********/

    public static final class SimpleFlow extends Event {
        private static final EventType type = new EventType("flow", "simple");


        public static int enter(String nodeId) {
            return Event.logLabel(type, LabelPos.START, nodeId, List.of());
        }

        public static int exit(String nodeId) {
            return Event.logLabel(type, LabelPos.END, nodeId, List.of());
        }
    }

    public static final class SimpleStatement extends Event {
        private static final EventType type = new EventType("statement", "simple");


        public static int enter(String nodeId) {
            return Event.logLabel(type, LabelPos.START, nodeId, List.of());
        }

        public static int exit(String nodeId, Object result) {
            return Event.logLabel(type, LabelPos.END, nodeId, List.of(new Result(result)));
        }
    }

    public static final class SimpleExpression extends Event {
        private static final EventType type = new EventType("expression", "simple");


        public static int enter(String nodeId) {
            return Event.logLabel(type, LabelPos.START, nodeId, List.of());
        }

        public static int exit(String nodeId, Object result) {
            return Event.logLabel(type, LabelPos.END, nodeId, List.of(new Result(result)));
        }
    }

    public static final class Update extends Event {
        private static final EventType type = new EventType("update", "simple");


        public static int writeLocal(String nodeId, String name, Object result) {
            return Event.logLabel(type, LabelPos.UPDATE, nodeId, List.of(
                    new Write(new LocalIdentifier("shouldBeParent", name),
                            result)));
        }

        public static int writeField(String nodeId, Object owner, String name, Object result) {
            return Event.logLabel(type, LabelPos.UPDATE, nodeId, List.of(
                    new Write(new FieldIdentifier(Reference.write("", owner), name),
                            result)));
        }
    }

    /********
     **** calls
     ********/

    public static final class ResultCall extends Event {
        private static final EventType type = new EventType("statement", "resultCall");

        public static int enter(String nodeId) {

            return Event.logLabel(type, LabelPos.START, nodeId, List.of());
        }

        //exactly one value of className and owner should be null
        public static int call(String nodeId, String className, Object owner, Object[] argValues) {
            Reference ownerRef = Reference.read(className, owner);
            return Event.logLabel(type, LabelPos.CALL, nodeId, List.of(ownerRef, new ArgsValues(List.of(argValues))));
        }

        public static int exit(String nodeId, Object result) {
            return Event.logLabel(type, LabelPos.END, nodeId, List.of(new Result(result)));
        }
    }

    public static final class VoidCall extends Event {
        private static final EventType type = new EventType("statement", "callVoid");

        public static int enter(String nodeId) {
            return Event.logLabel(type, LabelPos.START, nodeId, List.of());
        }

        //exactly one value of className and owner should be null
        public static int call(String nodeId, String className, Object owner, Object[] argValues) {
            Reference ownerRef = Reference.read(className, owner);
            return Event.logLabel(type, LabelPos.CALL, nodeId, List.of(ownerRef, new ArgsValues(List.of(argValues))));
        }

        public static int exit(String nodeId) {
            return Event.logLabel(type, LabelPos.END, nodeId, List.of());
        }
    }


    /**************
     ********* Label pos
     **************/


    public enum LabelPos {
        START("start"),
        UPDATE("update"),
        CALL("call"),
        END("end");

        private final String token;

        LabelPos(String token) {
            this.token = token;
        }

        public List<Object> serializeToJson(EventType type, DynamicEventInfo info, List<Data> data) {
            return Stream.concat(
                            Stream.of(token, info.eventId, info.nodeId, type.kind, type.type),
                            data.stream().map(Data::json))
                    .toList();
        }
    }

    /*******************************************************
     **************** data structures ******************
     *******************************************************/

    private record EventInfo(long eventId) {
    }

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
     **************** Data ******************
     *******************************************************/

    public sealed interface Data {
        Object json();
    }


    record Result(Object value) implements Data {
        @Override
        public JSONObject json() {
            return new JSONObject(Map.of(
                    "dataType", "result",
                    "result", valueRepr(value)));
        }
    }

    record ArgsValues(List<Object> argValues) implements Data {

        @Override
        public JSONObject json() {
            return new JSONObject(Map.of(
                    "dataType", "argsValues",
                    "values", new JSONArray(argValues.stream().map(FileLogger::valueRepr).toList())));

        }
    }

    record Write(Identifier identifier, Object value) implements Data {
        @Override
        public JSONObject json() {
            return new JSONObject(Map.of(
                    "dataType", "write",
                    "identifier", identifier.json(),
                    "value", valueRepr(value)));
        }
    }

    /*******************************************************
     **************** Reference ******************
     *******************************************************/


    public sealed interface Reference extends Data {
        //call if the field of a reference is only read
        static Reference read(String fullClassName, Object ref) {
            return reference(fullClassName, ref, 0);
        }

        //call if we write to the field of a reference
        static Reference write(String fullClassName, Object ref) {
            return reference(fullClassName, ref, 0);
        }

        private static Reference reference(String fullClassName, Object ref, int version) {
            if ((fullClassName == null && ref == null) || (fullClassName != null && ref != null)) {
                throw new IllegalArgumentException();
            }

            if (fullClassName != null) {
                int index = fullClassName.lastIndexOf('.');
                index = index == -1 ? 0 : index;
                return new StaticReference(
                        new ClassIdentifier(fullClassName.substring(0, index), fullClassName.substring(index)),
                        version);
            } else {
                Class<?> clazz = ref.getClass();

                return new InstanceReference(
                        new ClassIdentifier(clazz.getPackageName(), clazz.getSimpleName()),
                        System.identityHashCode(ref),
                        version);
            }
        }
    }

    //should only be instanced from Reference.read or Reference.write
    record StaticReference(ClassIdentifier clazz, int version) implements Reference {
        public JSONObject json() {
            return new JSONObject(Map.of(
                    "dataType", "staticRef",
                    "className", clazz.json(),
                    "version", version));
        }
    }

    //should only be instanced from Reference.read or Reference.write
    record InstanceReference(ClassIdentifier clazz, int hashPointer, int version) implements Reference {

        public JSONObject json() {
            return new JSONObject(Map.of(
                    "dataType", "instanceRef",
                    "object", clazz.json(),
                    "pointer", hashPointer,
                    "version", version));
        }
    }

    record ClassIdentifier(String packageName, String className) {
        public JSONObject json() {
            return new JSONObject(Map.of(
                    "packageName", packageName,
                    "className", className));
        }
    }

    /*******************************************************
     **************** Object Data ******************
     *******************************************************/

    //we should also have a mechanism to save class variables
    static ObjectData saveObject(Object obj) {
        Reference self = Reference.write("", obj);
        List<Field> fs = fields(obj.getClass())
                .map(f -> {
                    try {
                        return new Field(
                                new FieldIdentifier(self, f.getName()),
                                valueRepr(f.get(obj)));
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
        return new ObjectData(self, fs);
    }

    // store all data from an object
    record ObjectData(Reference self, List<Field> fields) {
    }

    record Field(FieldIdentifier identifier, Object value) {
    }

    public static Stream<java.lang.reflect.Field> fields(Class<?> clazz) {
        return Stream.empty();
    }


    /*******************************************************
     **************** Identifier ******************
     *******************************************************/

    public sealed interface Identifier extends Data {
    }

    public record LocalIdentifier(String parentNodeId, String name) implements Identifier {
        @Override
        public Object json() {
            return new JSONObject(Map.of(
                    "dataType", "localIdentifier",
                    "parent", parentNodeId,
                    "name", name));
        }
    }

    public record FieldIdentifier(Reference owner, String name) implements Identifier {
        @Override
        public Object json() {
            return new JSONObject(Map.of(
                    "dataType", "fieldIdentifier",
                    "owner", owner.json(),
                    "name", name));
        }
    }

    /*******************************************************
     **************** Value representation ******************
     *******************************************************/

    private static Object valueRepr(Object value) {
        return switch (value) {
            case null -> simpleValue("null", "");
            case Integer i -> simpleValue("int", i);
            case Long l -> simpleValue("long", l);
            case Boolean b -> simpleValue("bool", b);
            case String s -> simpleValue("string", s);
            case Character c -> simpleValue("char", c);
            case Byte b -> simpleValue("byte", b);
            case Short s -> simpleValue("short", s);
            case Float f -> simpleValue("float", f);
            case Double d -> simpleValue("double", d);
            case Object obj -> Reference.read(null, obj).json();
        };
    }

    private static JSONObject simpleValue(String type, Object value) {
        return new JSONObject(Map.of("dataType", type, "value", value));
    }

    /*******************************************************
     **************** event id ******************
     *******************************************************/

    private static long enterEvent() {
        long id = nextId();
        context.push(new EventInfo(id));
        return id;
    }

    private static long currentEvent() {
        EventInfo eventInfo = context.peek();
        return eventInfo.eventId;
    }

    private static long exitEvent() {
        EventInfo eventInfo = context.pop();
        return eventInfo.eventId;
    }


    private static long nextId() {
        return eventCounter++;
    }

}
