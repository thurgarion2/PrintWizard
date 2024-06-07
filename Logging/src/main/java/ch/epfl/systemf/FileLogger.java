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

    public static DefaultControlFlow controlFlow() {
        return new DefaultControlFlow();
    }

    public static FunctionContext functionFlow(String fullName) {
        return new FunctionContext(fullName);
    }

    public static TryCatch tryCatch() {
        return new TryCatch();
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
            JSONObject base = new JSONObject(Map.of(
                    "type", "GroupEvent",
                    "pos", pos,
                    "eventId", event.eventId(),
                    "eventType", event.type().repr
            ));

            switch(event){
                case ControlFlow flow:
                    return switch (flow.kind()) {
                        case ControlFlowKind.Default def -> {
                            base.put("kind", new JSONObject(Map.of("type", "DefaultContext")));
                            yield base;
                        }
                        case ControlFlowKind.FunctionContext fun -> {
                            base.put("kind", new JSONObject(Map.of(
                                    "type", "FunctionContext",
                                    "functionName", fun.fullName)));
                            yield base;
                        }
                    };
                default:
                    return base;
            }
        }

        static void exitUpTo(GroupEvent event){
            while(!eventStack.peek().equals(event)){
                exit(eventStack.peek());
            }
            exit(event);
        }

        static void exitAll() {
           while(!eventStack.empty()){
               exit(eventStack.peek());
           }
        }

        static void exit(GroupEvent event) {
            traceWriter.value(eventRep(event, "end"));
            GroupEvent top = eventStack.pop();
            if (!top.equals(event))
                throw new IllegalStateException();
        }

        public default int enter() {
            GroupEvent.enter(this);
            return 0;
        }

        public default int exit() {
            GroupEvent.exit(this);
            return 0;
        }
    }

    /********
     ****  Control Flow
     ********/



    public sealed interface ControlFlow extends GroupEvent {
        ControlFlowKind kind();

        @Override
        default GroupEventType type() {
            return GroupEventType.ControlFlow;
        }
    }

    public sealed interface ControlFlowKind{
        record Default() implements ControlFlowKind{}
        record FunctionContext(String fullName) implements ControlFlowKind{}
    }

    public final static class DefaultControlFlow implements ControlFlow {
        private final long eventId = nextId();

        @Override
        public long eventId() {
            return eventId;
        }

        @Override
        public ControlFlowKind kind() {
            return new ControlFlowKind.Default();
        }
    }

    public final static class FunctionContext implements ControlFlow {
        private final long eventId = nextId();
        private final String fullFunctionName;

        public FunctionContext(String fullFunctionName){
            this.fullFunctionName = fullFunctionName;
        }


        @Override
        public long eventId() {
            return eventId;
        }

        @Override
        public ControlFlowKind kind() {
            return new ControlFlowKind.FunctionContext(fullFunctionName);
        }
    }

    /********
     ****  Try catch
     ********/


    public static final class TryCatch {
        private final ControlFlow tryFlow = new DefaultControlFlow();
        private final ControlFlow catchFlow = new DefaultControlFlow();


        public int enterTry() {
            GroupEvent.enter(tryFlow);
            return 0;
        }

        public int exitTry() {
            GroupEvent.exit(tryFlow);
            return 0;
        }

        public int enterCatch() {
            GroupEvent.exitUpTo(TryCatch.this.tryFlow);
            GroupEvent.enter(catchFlow);
            return 0;
        }

        public int exitCatch() {
            GroupEvent.exit(catchFlow);
            return 0;
        }
    }

    /********
     ****  Statment
     ********/

    public static final class Statment implements GroupEvent {
        private final long eventId;

        private Statment() {
            eventId = nextId();
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

    /********
     ****  SubStatment
     ********/

    public static final class SubStatment implements GroupEvent {
        private final long eventId;

        private SubStatment() {
            eventId = nextId();
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

    public static int logSimpleExpression(String nodeKey, Write[] assigns) {
        traceWriter.value(new JSONObject(Map.of(
                "type", ExecutionStep.ExecutionStep,
                "kind", "expressionWithoutReturn",
                "nodeKey", nodeKey,
                "assigns", new JSONArray(Arrays.stream(assigns).map(Write::json).toList())
        )));
        return 0;
    }

    public static Call call(String nodeKey) {

        return new Call(nodeKey,nextId());
    }

    public static VoidCall voidCall(String nodeKey) {
        return new VoidCall(nodeKey,0);
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
                    "kind", "expression",
                    "result", result.json(),
                    "nodeKey", nodeKey,
                    "assigns", new JSONArray(assigns.stream().map(Write::json).toList())
            ));
        }
    }

    public static final class Call implements ExecutionStep {
        private final long id;
        private final String nodeKey;

        private Call(String nodeKey, long id) {
            this.id = id;
            this.nodeKey = nodeKey;
        }

        public int logCall(Value[] argValues) {
            traceWriter.value(new JSONObject(Map.of(
                    "type", ExecutionStep,
                    "kind", "logCall",
                    "nodeKey", nodeKey,
                    "stepId", id,
                    "argsValues", new JSONArray(
                            Arrays.stream(argValues).map(JsonSerializable::json).toList())
            )));
            return 0;
        }

        public int logReturn(Value result) {
            traceWriter.value(new JSONObject(Map.of(
                    "type", ExecutionStep,
                    "kind", "logReturn",
                    "nodeKey", nodeKey,
                    "stepId", id,
                    "result", result.json()
            )));
            return 0;
        }
    }

    public static final class VoidCall implements ExecutionStep {
        private final long id;
        private final String nodeKey;

        private VoidCall(String nodeKey, long id) {
            this.id = id;
            this.nodeKey = nodeKey;
        }

        public int logCall(Value[] argValues) {
            traceWriter.value(new JSONObject(Map.of(
                    "type", ExecutionStep,
                    "kind", "logVoidCall",
                    "nodeKey", nodeKey,
                    "stepId", id,
                    "argsValues", new JSONArray(
                            Arrays.stream(argValues).map(JsonSerializable::json).toList())
            )));
            return 0;
        }

        public int logReturn() {
            throw new UnsupportedOperationException();
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
            GroupEvent.exitAll();
            
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
