package ch.epfl.systemf;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class GenEventTree1 {

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

        TraceParser<String> innerIf =
                new Or(new IF(4, true), new Seq(new IF(4, false), new Or(new IF(5, true), new IF(5, false))));
        TraceParser<String> forIt =
                new SeqSubLevel(new ForIt(3, true),new Seq(innerIf, new Store()));
        TraceParser<String> forLoop =
                new SeqSubLevel(new For(), new Seq(new Store(), new Seq(new Many(forIt), new ForIt(3, false))));
        TraceParser<String> whileIt  =
                new SeqSubLevel(new WhileIt(1, true), new Seq(forLoop, new Store()));
        TraceParser<String> whileLoop =
                new SeqSubLevel(new While(), new Seq(new Many(whileIt), new WhileIt(1, false)));
        TraceParser<String> parser =
                new SeqSubLevel(new Start(), new Seq(new Store(), whileLoop));
        System.out.println(parser.parse(trace, "") instanceof Success<List<String>>);
    }

    record Pair<A,B>(A first, B second){};

    sealed interface Result<A>{}
    record Success<A>(A output) implements Result<A>{};
    record Failure<A>() implements Result<A>{};

    sealed interface TraceParser<A>{
        public Result<List<String>> parse(List<String> trace, A state);
    }

    public static boolean firstToken(List<String> trace, String type){
        if(!trace.isEmpty()){
            String[] parts = trace.get(0).split(", ");
            return parts[1].equals(type);
        }
        return false;
    }


    public static boolean ifToken(List<String> trace, int node, boolean value){
        if(!trace.isEmpty()){
            String[] parts = trace.get(0).split(", ");
            return parts[1].equals("if")
                    && Integer.parseInt(parts[2])==node
                    && Boolean.parseBoolean(parts[3])==value;
        }
        return false;
    }
    static final class Start implements TraceParser<String>{
        @Override
        public Result<List<String>> parse(List<String> trace, String state) {
            System.out.println(state+"start");
            return new Success<>(trace);
        }
    }

    static final class Many implements TraceParser<String>{
        public final TraceParser<String> parser;

        public Many(TraceParser<String> parser){
            this.parser = parser;
        }
        @Override
        public Result<List<String>> parse(List<String> trace, String state) {
            Result<List<String>> res = parser.parse(trace, state);
            Result<List<String>> lastRes = res;

            while(res instanceof Success<List<String>>){
                lastRes = res;
                res = parser.parse(((Success<List<String>>) res).output, state);
            }
            return lastRes;
        }
    }

    static final class SeqSubLevel implements TraceParser<String>{
        private final TraceParser<String> parent;
        private final TraceParser<String> child;
        public SeqSubLevel(TraceParser<String> parent, TraceParser<String> child){
            this.parent = parent;
            this.child = child;
        }
        @Override
        public Result<List<String>> parse(List<String> trace, String state) {
            Result<List<String>> res = parent.parse(trace, state);
            if(res instanceof Success<List<String>>){
                return child.parse( ((Success<List<String>>) res).output, state+"|--");
            }
            return new Failure<>();
        }
    }

    static final class Seq implements TraceParser<String>{
        private final TraceParser<String> first;
        private final TraceParser<String> second;
        public Seq(TraceParser<String> first, TraceParser<String> second){
            this.first = first;
            this.second = second;
        }
        @Override
        public Result<List<String>> parse(List<String> trace, String state) {
            Result<List<String>> res = first.parse(trace, state);
            if(res instanceof Success<List<String>>){
                return second.parse( ((Success<List<String>>) res).output, state);
            }
            return new Failure<>();
        }
    }

    static final class Or implements TraceParser<String>{
        private final TraceParser<String> first;
        private final TraceParser<String> second;
        public Or(TraceParser<String> first, TraceParser<String> second){
            this.first = first;
            this.second = second;
        }
        @Override
        public Result<List<String>> parse(List<String> trace, String state) {
            Result<List<String>> res = first.parse(trace, state);
            if(res instanceof Failure<List<String>>){
                return second.parse(trace, state);
            }
            return res;
        }
    }

    static final class Store implements TraceParser<String>{
        @Override
        public Result<List<String>> parse(List<String> trace, String state) {
            if(firstToken(trace, "store")){
                System.out.println(state+"store");
                return new Success<>(trace.subList(1,trace.size()));
            }
            return new Failure<>();
        }
    }

    static final class IF implements TraceParser<String>{
        public final int node;
        public final boolean branch;
        public IF(int node, boolean branch){
            this.node = node;
            this.branch = branch;
        }
        @Override
        public Result<List<String>> parse(List<String> trace, String state) {
            if(ifToken(trace, node, branch)){
                System.out.println(state+"if");
                return new Success<>(trace.subList(1,trace.size()));
            }
            return new Failure<>();
        }
    }


    static final class While implements TraceParser<String>{
        @Override
        public Result<List<String>> parse(List<String> trace, String state) {
            System.out.println(state+"While");
            return new Success<>(trace);
        }
    }



    static final class WhileIt implements TraceParser<String>{
        private final int node;
        private final boolean branch;

        public WhileIt(int node, boolean branch){
            this.node = node;
            this.branch = branch;
        }
        @Override
        public Result<List<String>> parse(List<String> trace, String state) {
            if(ifToken(trace, node, branch)){
                if(branch){
                    System.out.println(state+"WhileIt");
                }
                return new Success<>(trace.subList(1,trace.size()));
            }
            return new Failure<>();
        }
    }



    static final class For implements TraceParser<String>{
        @Override
        public Result<List<String>> parse(List<String> trace, String state) {
            System.out.println(state+"For");
            return new Success<>(trace);
        }
    }

    static final class ForIt implements TraceParser<String>{
        private final int node;
        private final boolean branch;

        public ForIt(int node, boolean branch){
            this.node = node;
            this.branch = branch;
        }
        @Override
        public Result<List<String>> parse(List<String> trace, String state) {
            if(ifToken(trace, node, branch)){
                if(branch){
                    System.out.println(state+"ForIt");
                }
                return new Success<>(trace.subList(1,trace.size()));
            }
            return new Failure<>();
        }
    }


}
