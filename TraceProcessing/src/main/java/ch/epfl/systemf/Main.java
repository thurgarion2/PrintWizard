package ch.epfl.systemf;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.*;

public class Main {

    public static Writer instructions;
    public static  Writer eventTree;


    public static void main(String[] args) throws IOException {
        File file = new File("./Exemple.java");
        JavaParser parser = new JavaParser();
        ParseResult<CompilationUnit> result = parser.parse(file);
        CompilationUnit tree = result.getResult().get();

        instructions = new BufferedWriter(new FileWriter("./instructions_ast.txt"));
        eventTree = new BufferedWriter(new FileWriter("./tree.txt"));

        ByteCodeVisitor v = new ByteCodeVisitor();
        tree.accept(v, 0);
        eventTree.close();
        instructions.close();

    }

    public static class ByteCodeVisitor extends VoidVisitorAdapter<Integer> {
        int id;
        public ByteCodeVisitor(){
           super();
           id = 6;
        }

        private int newId(){
            return this.id++;
        }
        @Override
        public void  visit(VariableDeclarator n, Integer arg){
            int storeId = newId();

            try {
                instructions.write(storeId+", store, "+n.getNameAsString()+"\n");
                eventTree.write(storeId+", store, "+n.getNameAsString()+", "+arg+"\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void  visit(AssignExpr n, Integer arg){
            int storeId = newId();

            try {
                instructions.write(storeId+", store, "+n.getTarget().toString()+"\n");
                eventTree.write(storeId+", store, "+n.getTarget().toString()+", "+arg+"\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void  visit(UnaryExpr n, Integer arg){
            if(n.getOperator()== UnaryExpr.Operator.PREFIX_INCREMENT){
                int storeId = newId();

                try {
                    instructions.write(storeId+", store, "+n.getExpression().toString()+"\n");
                    eventTree.write(storeId+", store, "+n.getExpression().toString()+", "+arg+"\n");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void  visit(WhileStmt n, Integer arg){
            int storeId = newId();

            try {
                instructions.write(storeId+", if\n");
                eventTree.write(storeId+", while, "+arg+"\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            n.getBody().accept(this, storeId);
        }

        @Override
        public void  visit(ForStmt n, Integer arg){
            int storeId = newId();
            n.getInitialization().forEach((p) -> {
                p.accept(this, storeId);
            });

            try {
                instructions.write(storeId+", if\n");
                eventTree.write(storeId+", for, "+arg+"\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            n.getBody().accept(this, storeId);
            n.getUpdate().forEach((p) -> {
                p.accept(this,storeId);
            });
        }

        @Override
        public void visit(MethodCallExpr n, Integer arg){
//            int id = newId();
//            try {
//                instructions.write("call-"+n.getName()+"-"+id+"\n");
//                eventTree.write("call, "+n.getName()+", "+id+", "+arg+"\n");
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
        }

        @Override
        public void visit(IfStmt n, Integer arg){
            int id = newId();
            try {
                instructions.write(id+", if\n");
                eventTree.write(id+", if, "+arg+"\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            n.getThenStmt().accept(this, id);
            n.getElseStmt().ifPresent((l) -> {
                l.accept(this, id);
            });
        }

    }
}