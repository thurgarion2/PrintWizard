package ch.epfl.systemf;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GenEventTree2 {

    public static void main(String[] args) {
        List<String> trace = new ArrayList<>();
        try (BufferedReader br
                     = new BufferedReader(new InputStreamReader(new FileInputStream("./trace.txt")))) {
            String line;
            while ((line = br.readLine()) != null) {
                trace.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Map<Integer, EventDescription> desc = Map.of(
                0, new STOREDESCR(0),
                1, new LOOP(1,"while", 3),
                2, new STOREDESCR(2),
                3, new LOOP(3,"for", 2),
                4, new IFDESCR(4, 0, true, 1, true),
                5, new IFDESCR(5, 0, true, 0, false),
                6, new STOREDESCR(6),
                7, new STOREDESCR(7)
        );
        List<EventWithLimit> events = addEventLimits(trace, desc);
        events.add(new END());
        System.out.println("Start");
        block(events, 1);
    }

    static int eventPointer;

    static boolean block(List<EventWithLimit> events, int level){
        while(blockStmt(events, level)){};
        return true;
    }

    static boolean blockStmt(List<EventWithLimit> events, int level){
        return for_(events, level)
                || while_(events, level)
                || if_(events, level)
                || store(events, level);
    }

    static boolean while_(List<EventWithLimit> events, int level){
        if(events.get(eventPointer) instanceof WHILEITSTART){
            System.out.println(ident(level)+"while");
            while(whileIt(events, level+1)){}
            return true;
        }
        return false;
    }

    static boolean whileIt(List<EventWithLimit> events, int level){
        if(events.get(eventPointer) instanceof WHILEITSTART start){
            long eventId = start.id;
            System.out.println(ident(level)+"whileIteration");
            ++eventPointer;
            block(events, level+1);
            assert(events.get(eventPointer) instanceof WHILEITEND);
            ++eventPointer;
            return true;
        }
        return false;
    }

    static boolean for_(List<EventWithLimit> events, int level){
        if(events.get(eventPointer) instanceof FORITSTART){
            System.out.println(ident(level)+"for");
            while(forIt(events, level+1)){}
            return true;
        }
        return false;
    }

    static boolean forIt(List<EventWithLimit> events, int level){
        if(events.get(eventPointer) instanceof FORITSTART start){
            long eventId = start.id;
            System.out.println(ident(level)+"forIteration");
            ++eventPointer;
            block(events, level+1);
            assert(events.get(eventPointer) instanceof FORITEND);
            ++eventPointer;
            return true;
        }
        return false;
    }
    static boolean if_(List<EventWithLimit> events, int level){
        if(events.get(eventPointer) instanceof IFSTART){
            System.out.println(ident(level)+"if");
            ++eventPointer;
            block(events, level+1);
            assert(events.get(eventPointer) instanceof IFEND);
            ++eventPointer;
            return true;
        }
        return false;
    }
    static boolean store(List<EventWithLimit> events, int level){
        if(events.get(eventPointer) instanceof STORE){
            System.out.println(ident(level)+"store");
            ++eventPointer;
            return true;
        }
        return false;
    }

    static void block(List<EventWithLimit> events){
        int level = 0;
        System.out.println("Start");
        level = 1;
        for(EventWithLimit event : events){

        }
    }

    static String ident(int level){
        String ident = "";
        for(int i=0; i<level;++i){
            ident = ident+"|--";
        }
        return ident;
    }



    public static List<EventWithLimit> addEventLimits(List<String> trace, Map<Integer, EventDescription> description){
        List<EventWithLimit> events = new ArrayList<>();
        List<StackElem> stack = new ArrayList<>();

        for(String event : trace){
            String[] parts = event.split(", ");
            int nodeId = Integer.parseInt(parts[2]);
            long eventId = Long.parseLong(parts[0]);
            EventDescription desc = description.get(nodeId);

            decreaseContext(stack);

            if(desc instanceof STOREDESCR){
                events.add(new STORE(eventId, nodeId));
            } else if (desc instanceof IFDESCR desc_) {
                boolean branch = Boolean.parseBoolean(parts[3]);
                if(branch && desc_.displayTrue){
                    events.add(new IFSTART(eventId, nodeId, true));
                    stack.add(new StackElem(desc_.contextTrue, "if", nodeId, eventId));
                }else if(!branch && desc_.displayFalse){
                    events.add(new IFSTART(eventId, nodeId, true));
                    stack.add(new StackElem(desc_.contextFalse, "if", nodeId, eventId));
                }
            } else if (desc instanceof LOOP desc_) {
                boolean branch = Boolean.parseBoolean(parts[3]);

                if(branch && Objects.equals(desc_.name, "for")){
                    events.add(new FORITSTART(eventId, nodeId));
                    stack.add(new StackElem(desc_.context, "for", nodeId, eventId));
                }else if(branch && Objects.equals(desc_.name, "while")){
                    events.add(new WHILEITSTART(eventId, nodeId));
                    stack.add(new StackElem(desc_.context, "while", nodeId, eventId));
                }
            }

            closeContext(events, stack);


        }

        return events;


    }

    static class StackElem{
        public int context;
        public final String nodeName;
        public final int nodeId;
        public final long eventId;
        public StackElem(int context, String nodeName, int nodeId, long eventId){
            this.context = context;
            this.eventId = eventId;
            this.nodeId = nodeId;
            this.nodeName = nodeName;
        }
    };

    public static void closeContext(List<EventWithLimit> events, List<StackElem> stack){
        while(!stack.isEmpty()){
            StackElem top = stack.get(stack.size()-1);
            if(top.context>0)
                return;

            switch (top.nodeName){
                case "if":
                    events.add(new IFEND(top.eventId, top.nodeId));
                    break;
                case "for":
                    events.add(new FORITEND(top.eventId, top.nodeId));
                    break;
                case "while":
                    events.add(new WHILEITEND(top.eventId, top.nodeId));
                    break;
            }
            stack.remove(stack.size()-1);
        }
    }

    public static void decreaseContext(List<StackElem> stack){
        if(!stack.isEmpty()){
            StackElem top = stack.get(stack.size()-1);
            top.context = top.context>0 ? top.context-1 : top.context;
        }
    }

    sealed interface EventDescription{};
    record STOREDESCR(int nodeId) implements EventDescription {};
    record IFDESCR(int nodeId, int contextTrue, boolean displayTrue, int contextFalse, boolean displayFalse) implements EventDescription {};
    record LOOP(int nodeId, String name, int context) implements EventDescription {};

    sealed interface EventWithLimit{};
    record END() implements EventWithLimit{};
    record STORE(long id, int nodeId) implements EventWithLimit{};
    record IFSTART(long id, int nodeId, boolean branch) implements EventWithLimit{};
    record IFEND(long id, int nodeId) implements EventWithLimit{};
    record FORITSTART(long id, int nodeId) implements EventWithLimit{};
    record FORITEND(long id, int nodeId) implements EventWithLimit{};
    record WHILEITSTART(long id, int nodeId) implements EventWithLimit{};
    record WHILEITEND(long id, int nodeId) implements EventWithLimit{};


}
