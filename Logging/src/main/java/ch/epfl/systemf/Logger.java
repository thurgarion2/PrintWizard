package ch.epfl.systemf;


//all methods return an integer, because otherwise we cannot call the in let bindings
//for now we assume that all the parent relationship can be express with the start parent scope
//and with the end parent scope. All event in the scope are children of the parent

public interface Logger {
    public sealed interface Event {}

    // we could use an option, but code gen will be simpler with boolean
    public record Evaluation(boolean hasResult,
                             Object result,
                             boolean hasAssign,

                             String varName,
                             Object value) implements Event{};


    public  static int exitEvaluation(int nodeId, Evaluation eval){
        throw new UnsupportedOperationException();
    }
    public static  int enter(int nodeId){
        throw new UnsupportedOperationException();
    }
    public  static int exitLogical(int nodeId, String description){
        throw new UnsupportedOperationException();
    }

}
