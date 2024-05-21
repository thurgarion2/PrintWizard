package ch.epfl.systemf;


import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONWriter;

import java.io.*;
import java.lang.ref.WeakReference;
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


            traceWriter.array();
            pos.serializeToJson(type, new DynamicEventInfo(eventId, nodeId), data).forEach(traceWriter::value);
            traceWriter.endArray();
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
                    new Write(new FieldIdentifier(Reference.write(null, owner), name),
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
            return Event.logLabel(type, LabelPos.CALL, nodeId, List.of(ownerRef, new ArgsValues(argValues)));
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
            return Event.logLabel(type, LabelPos.CALL, nodeId, List.of(ownerRef, new ArgsValues(argValues)));
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
     **************** File logger ******************
     *******************************************************/

    static class JsonFileWriter implements AutoCloseable{
        private final  FileWriter  file;
        private final  BufferedWriter writer;
        public final  JSONWriter json;

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

    private record EventInfo(long eventId) {
    }

    private static Stack<EventInfo> context = new Stack<>();


    private static long eventCounter = 0;
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

    record ArgsValues(Object[] argValues) implements Data {

        @Override
        public JSONObject json() {
            return new JSONObject(Map.of(
                    "dataType", "argsValues",
                    "values", new JSONArray(Arrays.stream(argValues).map(FileLogger::valueRepr).toList())));

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
        Map<Object, Integer> versions = new HashMap<>();

        //call if the field of a reference is only read
        static Reference read(String fullClassName, Object ref) {
            return reference(fullClassName, ref);
        }

        //call if we write to the field of a reference
        //we only save an object when we write to its fields for now, if you want to change it you should also change valueRepr
        //to avoid infinite loop
        //Bug : a function that we have not instrumented could return an object then we would not have access to its values
        static Reference write(String fullClassName, Object ref) {
            if(ref!=null){
                versions.put(ref, versions.getOrDefault(ref,0)+1);
                InstanceReference r = (InstanceReference) reference(fullClassName, ref);
                saveObject(r, ref);
                return r;
            }

            return reference(fullClassName, ref);
        }

        private static void saveObject(InstanceReference ref, Object obj) {
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
            objectDataWriter.key(ref.hashPointer+"-"+ref.version);
            objectDataWriter.value(data.json());
        }

        record ObjectData(Reference self, List<Field> fields) {
            public JSONObject json(){
                return new JSONObject()
                        .put("self", self.json())
                        .put("fields", new JSONArray(fields.stream().map(Field::json).toList()));
            }
        }

        record Field(FieldIdentifier identifier, Object value) {
            public JSONObject json(){
                return new JSONObject()
                        .put("identifier", identifier.json())
                        .put("value", valueRepr(value));
            }
        }

        //TODO return field from interface but who use static interface fields
        private static Stream<java.lang.reflect.Field> fields(Class<?> clazz) {
            if(clazz==null){
                return Stream.empty();
            }else{
                return Stream.concat(Arrays.stream(clazz.getDeclaredFields()), fields(clazz.getSuperclass()));
            }
        }



        private static Reference reference(String fullClassName, Object ref) {
            if ((fullClassName == null && ref == null) || (fullClassName != null && ref != null)) {
                throw new IllegalArgumentException();
            }

            if (fullClassName != null) {
                int index = fullClassName.lastIndexOf('.');
                index = index == -1 ? 0 : index;
                return new StaticReference(
                        new ClassIdentifier(fullClassName.substring(0, index), fullClassName.substring(index)),
                        0);
            } else {
                Class<?> clazz = ref.getClass();

                return new InstanceReference(
                        new ClassIdentifier(clazz.getPackageName(), clazz.getSimpleName()),
                        System.identityHashCode(ref),
                        versions.getOrDefault(ref,0));
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
                    "className", clazz.json(),
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
