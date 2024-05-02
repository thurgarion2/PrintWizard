package ch.epfl.systemf;

import java.util.List;
import java.util.stream.Stream;

public class TraceFormat {

    /*******************************************************
     **************** Trace Format ******************
     *******************************************************/

    /**************
     ********* FLOW
     **************/

    static final Label START_FLOW = new Label("enterFlow", LABEL_PREFIX("startFlow"));
    static final Label END_FLOW = new Label("exitFlow",LABEL_PREFIX("exitFlow"));

    static final Event SIMPLE_FLOW = new Event("simpleStatement", List.of(START_FLOW, END_FLOW));


    /**************
     ********* statement
     **************/

    static final Label START_STAT = new Label("enterStatement", LABEL_PREFIX("startStat"));
    static final Label END_STAT = new Label("exitStatement",LABEL_PREFIX("exitStat"));

    static final Event SIMPLE_STATEMENT = new Event("simpleStatement", List.of(START_STAT, END_STAT));

    /**************
     ********* expression
     **************/

    static final Label START_EXPR = new Label("enterExpression", LABEL_PREFIX("startExpr"));
    static final Label END_EXPR = new Label("exitExpression", concat(
            LABEL_PREFIX("exitExpr"),
            List.of(new LabelField("result", new CObjectType()))));

    static final Event SIMPLE_EXPR = new Event("simpleExpression", List.of(START_EXPR, END_EXPR));

    /**************
     ********* update
     **************/

    static final Label UPDATE_LABEL = new Label("update", concat(
            LABEL_PREFIX("update"),
            List.of(
                    new LabelField("varName", new RStringType()),
                    new LabelField("value", new CObjectType())
            )));
    static final Event UPDATE = new Event("update", List.of(UPDATE_LABEL));

    /**************
     ********* all events format
     **************/

    static final List<Event> EVENTS = List.of(UPDATE, SIMPLE_EXPR, SIMPLE_STATEMENT, SIMPLE_FLOW);

    /**************
     ********* helpers
     **************/
    private static List<LabelField> LABEL_PREFIX(String labelType){
        return List.of(
                new LabelField("eventId", new CLongType()),
                new LabelField("labelType", new CLiteral(labelType)),
                new LabelField("nodeId", new RStringType())
        );
    }
    private static <A> List<A> concat(List<A> l1, List<A> l2) {
        return Stream.concat(l1.stream(), l2.stream()).toList();
    }

    /*******************************************************
     **************** Type definition ******************
     *******************************************************/

    sealed interface Type {
    }

    sealed interface RuntimeType extends Type {
    }

    record RObjectType() implements RuntimeType {
    }

    record RStringType() implements RuntimeType {
    }


    record RIntType() implements RuntimeType {
    }

    sealed interface ComplieTimeType extends Type {
    }

    record CLiteral(String value) implements ComplieTimeType {
    }

    record CLongType() implements ComplieTimeType {
    }

    record CObjectType() implements ComplieTimeType {
    }

    /*******************************************************
     **************** Label definition ******************
     *******************************************************/


    record Label(String name, List<LabelField> fields) {
    }

    record LabelField(String name, Type type) {
    }

    /*******************************************************
     **************** Event definition ******************
     *******************************************************/

    record Event(String name, List<Label> labels) {
    }


}
