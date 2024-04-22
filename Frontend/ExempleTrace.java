package Frontend;

import java.util.function.Supplier;

public class ExempleTrace {
    private static int eventId = 0;


    public static void main(String[] args) {
        int rootId = nextEventId();
        flowEnter(rootId, "root", "");


        Object x = null;
        stat("Object x = null", "", () -> {
            update("Object x = null", "x, null");
            return null;
        });


        int i = 0;
        stat("int i = 0;", "", () -> {
            update("int i = 0;", "i, 0");
            return null;
        });


        while (i < 5) {
            stat("while(i<5)", "", () -> {
                return null;
            });

            int flowId3 = nextEventId();
            flowEnter(flowId3, "while(i<5){", "loopBody");



            stat("for(int j=0; j<5; ++j)", "", () -> {
                update("for(int j=0; j<5; ++j);", "j, 0");
                return null;
            });
            for (int j = 0; j < 5; ++j) {
                stat("for(int j=0; j<5; ++j)", "", () -> {
                    return null;
                });
                int flowId4 = nextEventId();
                flowEnter(flowId4, "for(int j=0; j<5; ++j){", "loopBody");



                stat("if(i==j)", "", () -> {
                    return null;
                });
                if (i == j) {
                    int flowId1 = nextEventId();
                    flowEnter(flowId1, "if(i==j){", "ifBody");
                    //System.out.println(i);

                    stat("System.out.println(i);", "", () -> {
                        return null;
                    });
                    flowExit(flowId1, "if(i==j){", "ifBody");
                } else {

                    stat("if(i==j+1)", "", () -> {
                        return null;
                    });
                    if (i == j + 1) {
                        //System.out.println(j);
                        int flowId2 = nextEventId();
                        flowEnter(flowId2, "if(i==j+1){", "ifBody");

                        stat("System.out.println(j);", "", () -> {
                            return null;
                        });
                        flowExit(flowId2,"if(i==j+1){", "ifBody");
                    }
                }

                int j_ = j;
                int i__ = i;

                stat("foo(i*10| j)", "", () -> {
                    foo(i__*10, j_);
                    return null;
                });

                stat("for(int j=0; j<5; ++j)", "", () -> {
                    update("for(int j=0; j<5; ++j);", "j, " + j_);
                    return null;
                });

                flowExit( flowId4, "for(int j=0; j<5; ++j){", "loopBody");
            }

            ++i;
            int i_ = i;
            stat("++i", "", () -> {
                update("int i = 0;", "i, " + i_);
                return null;
            });

            flowExit(flowId3,"while(i<5){", "loopBody");
        }
        flowExit(rootId, "root", "");
    }

    public static int foo(int a, int b) {
        int eventId = nextEventId();
        flowEnter(eventId, "function foo", "");
        stat("return a + b * b", "", () -> {
            expr("b * b", ""+b*b, () -> {return null;});
            return null;
        });
        flowExit(eventId, "function foo", "");
        return a + b * b;
    }


    private static void update(String nodeId, String data) {
        event(nextEventId(), "update", nodeId, data);
    }

    private static void flow(String nodeId, String data, Supplier<Void> inside) {
        int eventId = nextEventId();
        event(eventId, "flowEnter", nodeId, data);
        inside.get();
        event(eventId, "flowExit", nodeId, data);
    }

    private static void flowEnter(int eventId, String nodeId, String data) {
        event(eventId, "flowEnter", nodeId, data);
    }

    private static void flowExit(int eventId, String nodeId, String data) {
        event(eventId, "flowExit", nodeId, data);
    }


    private static void stat(String nodeId, String data, Supplier<Void> inside) {
        int eventId = nextEventId();
        event(eventId, "statEnter", nodeId, data);
        inside.get();
        event(eventId, "statExit", nodeId, data);
    }

    private static void expr(String nodeId, String data, Supplier<Void> inside) {
        int eventId = nextEventId();
        event(eventId, "exprEnter", nodeId, data);
        inside.get();
        event(eventId, "exprExit", nodeId, data);
    }

    private static void event(int eventId, String kind, String nodeId, String data) {
        System.out.println(eventId + ", " + kind + ", " + nodeId + (data.isEmpty() ? "" : ", " + data));
    }

    private static int nextEventId() {
        return ++eventId;
    }
}
