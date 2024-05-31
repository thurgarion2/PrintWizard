package ch.epfl.systemf;


import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONWriter;

import java.io.*;
import java.util.*;
import java.util.stream.Stream;

public class FileLogger {

    interface JsonSerializable {
        JSONObject json();
    }

    /*******************************************************
     **************** Event construct ******************
     *******************************************************/


    /**************
     ********* Group Event
     **************/

    /********
     **** api
     ********/

    public static ControlFlow controlFlow() {
        return new ControlFlow();
    }

    public static Statment statment() {
        return new Statment();
    }

    public static SubStatment subStatment() {
        return new SubStatment();
    }

    /********
     ****  group events definitions
     ********/

    public enum GroupEventType {
        ControlFlow("controlFlow"),
        Statement("statement"),
        SubStatement("subStatement");

        public final String repr;

        GroupEventType(String repr) {
            this.repr = repr;
        }
    }

    private final static Stack<GroupEvent> eventStack = new Stack<>();

    private static long nextId() {
        return eventCounter++;
    }

    private static long eventCounter = 0;


    public sealed interface GroupEvent {
        long eventId();

        GroupEventType type();

        static void enter(GroupEvent event) {
            eventStack.push(event);
            traceWriter.value(eventRep(event, "start"));
        }

        static JSONObject eventRep(GroupEvent event, String pos) {
            return new JSONObject(Map.of(
                    "type", "GroupEvent",
                    "pos", pos,
                    "eventId", event.eventId(),
                    "eventType", event.type().repr
            ));
        }

        static void exit(GroupEvent event) {
            traceWriter.value(eventRep(event, "end"));
            GroupEvent top = eventStack.pop();
            if (!top.equals(event))
                throw new IllegalStateException();
        }
    }


    public static final class ControlFlow implements GroupEvent {
        private final long eventId;

        private ControlFlow() {
            eventId = nextId();
        }

        public int enter() {
            GroupEvent.enter(this);
            return 0;
        }

        public int exit() {
            GroupEvent.exit(this);
            return 0;
        }

        @Override
        public long eventId() {
            return eventId;
        }

        @Override
        public GroupEventType type() {
            return GroupEventType.ControlFlow;
        }
    }

    public static final class Statment implements GroupEvent {
        private final long eventId;

        private Statment() {
            eventId = nextId();
        }

        public int enter() {
            GroupEvent.enter(this);
            return 0;
        }

        public int exit() {
            GroupEvent.exit(this);
            return 0;
        }

        @Override
        public long eventId() {
            return eventId;
        }

        @Override
        public GroupEventType type() {
            return GroupEventType.Statement;
        }
    }


    public static final class SubStatment implements GroupEvent {
        private final long eventId;

        private SubStatment() {
            eventId = nextId();
        }

        public int enter() {
            GroupEvent.enter(this);
            return 0;
        }

        public int exit() {
            GroupEvent.exit(this);
            return 0;
        }

        @Override
        public long eventId() {
            return eventId;
        }

        @Override
        public GroupEventType type() {
            return GroupEventType.SubStatement;
        }
    }

    /**************
     ********* execution step
     **************/

    /********
     **** api
     ********/

    public static int logSimpleExpression(String nodeKey, Value result, Write[] assigns) {
        traceWriter.value(new Expression(nodeKey, result, List.of(assigns)).json());
        return 0;
    }

    public static Call call() {
        return new Call(0);
    }

    public static VoidCall voidCall() {
        return new VoidCall(0);
    }

    /********
     **** Execution step definitions
     ********/

    public sealed interface ExecutionStep {
        String ExecutionStep = "ExecutionStep";
    }

    public record Expression(String nodeKey, Value result, List<Write> assigns) implements ExecutionStep {
        JSONObject json() {
            return new JSONObject(Map.of(
                    "type", ExecutionStep,
                    "result", result.json(),
                    "nodeKey", nodeKey,
                    "assigns", new JSONArray(assigns.stream().map(Write::json).toList())
            ));
        }
    }

    public static final class Call implements ExecutionStep {
        private final long id;

        private Call(long id) {
            this.id = id;
        }

        public int logCall(Value[] argValues) {
            return 0;
        }

        public int logReturn(Value result) {
            return 0;
        }
    }

    public static final class VoidCall implements ExecutionStep {
        private final long id;

        private VoidCall(long id) {
            this.id = id;
        }

        public int logCall(Value[] argValues) {
            return 0;
        }

        public int logReturn() {
            return 0;
        }
    }

    /*******************************************************
     **************** File logger ******************
     *******************************************************/

    static class JsonFileWriter implements AutoCloseable {
        private final FileWriter file;
        private final BufferedWriter writer;
        public final JSONWriter json;

        public JsonFileWriter(String fileName) throws IOException {
            file = new FileWriter(fileName);
            writer = new BufferedWriter(file);
            json = new JSONWriter(writer);
        }

        @Override
        public void close() throws Exception {
            writer.close();
            file.close();
        }
    }

    /*******************************************************
     **************** data structures ******************
     *******************************************************/


    private final static String traceFileName = "eventTrace.json";
    private final static String objectDataFileName = "objectData.json";

    private final static JSONWriter traceWriter;
    private final static JSONWriter objectDataWriter;


    /*******************************************************
     **************** INITIALIZATION ******************
     *******************************************************/

    static {
        JsonFileWriter trace;
        JsonFileWriter objectData;
        try {
            trace = new JsonFileWriter(traceFileName);
            objectData = new JsonFileWriter(objectDataFileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        traceWriter = trace.json;
        traceWriter.object();
        // trace field an array
        traceWriter.key("trace");
        traceWriter.array();

        objectDataWriter = objectData.json;
        objectDataWriter.object();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                traceWriter.endArray();
                traceWriter.endObject();

                objectDataWriter.endObject();

                trace.close();
                objectData.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
    }

    /*******************************************************
     **************** Data ******************
     *******************************************************/

    public static Write write(Identifier identifier, Value value) {
        return new Write(identifier, value);
    }

    public record Write(Identifier identifier, Value value) implements JsonSerializable {

        public JSONObject json() {
            return new JSONObject(Map.of(
                    "dataType", "write",
                    "identifier", identifier.json(),
                    "value", value.json()));
        }
    }

    /*******************************************************
     **************** Reference ******************
     *******************************************************/

    /********
     **** api
     ********/


    // add a mechanism to save object
    // either on first read or for each write
    public static InstanceReference readReference(Object obj) {
        Class<?> clazz = obj.getClass();

        return new InstanceReference(
                new ClassIdentifier(clazz.getPackageName(), clazz.getSimpleName()),
                System.identityHashCode(obj),
                ++InstanceReference.timeStampCounter);
    }

    public static InstanceReference writeReference(Object obj) {
        return readReference(obj);
    }

    /********
     **** Instance reference definition
     ********/


    //should only be instanced from Reference.read or Reference.write
    public record InstanceReference(ClassIdentifier clazz, int hashPointer, long timeStamp) implements Value {

        static long timeStampCounter = 0;

        public JSONObject json() {
            return new JSONObject(Map.of(
                    "dataType", "instanceRef",
                    "className", clazz.json(),
                    "pointer", hashPointer,
                    "version", timeStamp));
        }
    }

    record ClassIdentifier(String packageName, String className) implements JsonSerializable {
        public JSONObject json() {
            return new JSONObject(Map.of(
                    "packageName", packageName,
                    "className", className));
        }
    }

    public record ObjectData(InstanceReference self, List<Field> fields) implements JsonSerializable {

        public static void saveObject(InstanceReference ref, Object obj) {
            List<Field> fs = fields(obj.getClass())
                    .map(f -> {
                        try {
                            f.setAccessible(true);
                            Object value = f.get(obj);
                            f.setAccessible(false);
                            return new Field(
                                    new FieldIdentifier(ref, f.getName()),
                                    value);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
            ObjectData data = new ObjectData(ref, fs);
            objectDataWriter.key(ref.hashPointer + "-" + ref.timeStamp);
            objectDataWriter.value(data.json());
        }


        //TODO return field from interface but who use static interface fields
        public static Stream<java.lang.reflect.Field> fields(Class<?> clazz) {
            if (clazz == null) {
                return Stream.empty();
            } else {
                return Stream.concat(Arrays.stream(clazz.getDeclaredFields()), fields(clazz.getSuperclass()));
            }
        }

        record Field(FieldIdentifier identifier, Object value) {
            public JSONObject json() {
                return new JSONObject()
                        .put("identifier", identifier.json())
                        .put("value", valueRepr(value));
            }
        }

        public JSONObject json() {
            return new JSONObject()
                    .put("self", self.json())
                    .put("fields", new JSONArray(fields.stream().map(Field::json).toList()));
        }
    }


    /*******************************************************
     **************** Identifier ******************
     *******************************************************/

    /********
     **** api
     ********/


    public static LocalIdentifier localIdentifier(String parentNodeId, String name) {
        return new LocalIdentifier(parentNodeId, name);
    }

    public static StaticIdentifier staticIdentifier(String packageName, String className, String name) {
        return new StaticIdentifier(new ClassIdentifier(packageName, className), name);
    }

    public static FieldIdentifier fieldIdentifier(InstanceReference owner, String name) {
        return new FieldIdentifier(owner, name);
    }

    /********
     **** identifier definition
     ********/


    public sealed interface Identifier extends JsonSerializable {
    }

    public record LocalIdentifier(String parentNodeId, String name) implements Identifier {
        @Override
        public JSONObject json() {
            return new JSONObject(Map.of(
                    "dataType", "localIdentifier",
                    "parent", parentNodeId,
                    "name", name));
        }
    }


    public record StaticIdentifier(ClassIdentifier clazz, String name) implements Identifier {
        @Override
        public JSONObject json() {
            return new JSONObject(Map.of(
                    "dataType", "staticIdentifier",
                    "clazz", clazz.json(),
                    "name", name));
        }
    }


    public record FieldIdentifier(InstanceReference owner, String name) implements Identifier {
        @Override
        public JSONObject json() {
            return new JSONObject(Map.of(
                    "dataType", "fieldIdentifier",
                    "owner", owner.json(),
                    "name", name));
        }
    }

    /*******************************************************
     **************** Value representation ******************
     *******************************************************/

    /********
     **** api
     ********/


    public static Value valueRepr(Object value) {
        return switch (value) {
            case null -> new Literal("null", "");
            case Integer i -> new Literal("int", i);
            case Long l -> new Literal("long", l);
            case Boolean b -> new Literal("bool", b);
            case String s -> new Literal("string", s);
            case Character c -> new Literal("char", c);
            case Byte b -> new Literal("byte", b);
            case Short s -> new Literal("short", s);
            case Float f -> new Literal("float", f);
            case Double d -> new Literal("double", d);
            case Object obj -> readReference(value);
        };
    }

    /********
     **** value definition
     ********/


    public sealed interface Value extends JsonSerializable {
    }

    public record Literal(String type, Object value) implements Value {

        @Override
        public JSONObject json() {
            return new JSONObject(Map.of("dataType", type, "value", value));
        }
    }


}
