package ch.epfl.systemf.InstrumentationPlugin;

//automatically generated code do not edit refer to ApiGenerator

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;

public class Logger {

    /*******************************************************
     **************** CONSTANTS ********
     *******************************************************/

    final SimpleFlow simpleFlow = this.new SimpleFlow();
    final SimpleStatement simpleStatement = this.new SimpleStatement();
    final SimpleExpression simpleExpression = this.new SimpleExpression();
    final VoidCall voidCall = this.new VoidCall();
    final Update update = this.new Update();
    final ResultCall resultCall = this.new ResultCall();


    /**************
     ********* simple Event
     **************/

    sealed interface Event {
    }

    final class SimpleFlow implements Event {
        private static final TreeHelper.SimpleClass clazz = innerClass("SimpleFlow");

        public JCTree.JCExpression enter(String nodeId) {
            return callSimpleEnter(clazz, nodeId);
        }

        public JCTree.JCExpression exit(String nodeId) {
            return callSimpleExit(clazz, nodeId);
        }
    }

    final class SimpleStatement implements Event {
        private static final TreeHelper.SimpleClass clazz = innerClass("SimpleStatement");

        public JCTree.JCExpression enter(String nodeId) {
            return callSimpleEnter(clazz, nodeId);
        }

        public JCTree.JCExpression exit(String nodeId, Symbol result) {
            return callExitResult(clazz, nodeId, result);
        }

    }

    final class SimpleExpression implements Event {
        private static final TreeHelper.SimpleClass clazz = innerClass("SimpleExpression");

        public JCTree.JCExpression enter(String nodeId) {
            return callSimpleEnter(clazz, nodeId);
        }

        public JCTree.JCExpression exit(String nodeId, Symbol result) {
            return callExitResult(clazz, nodeId, result);
        }

    }

    final class Update implements Event {
        private static final TreeHelper.SimpleClass clazz = innerClass("Update");


        public JCTree.JCExpression write(String nodeId, Identifier identifier, Symbol value) {
            switch (identifier) {
                case LocalIdentifier local:
                    return helper.callStaticMethod(
                            clazz,
                            "writeLocal",
                            List.of(helper.string, helper.string, helper.objectP),
                            helper.intP,
                            List.of(mkTree.Literal(nodeId), mkTree.Literal(local.name), mkTree.Ident(value)));
                case FieldIdentifier field:
                    return helper.callStaticMethod(
                            clazz,
                            "writeField",
                            List.of(helper.string, helper.objectP, helper.string, helper.objectP),
                            helper.intP,
                            List.of(mkTree.Literal(nodeId), mkTree.Ident(field.fieldOwner), mkTree.Literal(field.fieldName), mkTree.Ident(value)));

            }

        }

    }

    sealed interface Identifier {
    }

    record LocalIdentifier(String name) implements Identifier {
    }

    record FieldIdentifier(Symbol fieldOwner, String fieldName) implements Identifier {
    }

    /**************
     ********* call events
     **************/

    abstract sealed class Call implements Event {
        abstract TreeHelper.SimpleClass clazz();

        public final JCTree.JCExpression enter(String nodeId) {
            return callSimpleEnter(clazz(), nodeId);
        }


        public final JCTree.JCExpression callStatic(String nodeId, String className, List<? extends Symbol> argValues) {
            return callStaticExp(clazz(), nodeId, className, argValues);
        }

        public final JCTree.JCExpression callInstance(String nodeId, Symbol objRef, List<? extends Symbol> argValues) {
            return callInstanceExp(clazz(), nodeId, objRef, argValues);
        }

    }

    final class ResultCall extends Call {
        private static final TreeHelper.SimpleClass clazz = innerClass("ResultCall");


        public JCTree.JCExpression exit(String nodeId, Symbol result) {
            return callExitResult(clazz, nodeId, result);
        }

        @Override
        TreeHelper.SimpleClass clazz() {
            return clazz;
        }
    }

    final class VoidCall extends Call {
        private static final TreeHelper.SimpleClass clazz = innerClass("VoidCall");

        public JCTree.JCExpression exit(String nodeId) {
            return callSimpleExit(clazz, nodeId);
        }

        @Override
        TreeHelper.SimpleClass clazz() {
            return clazz;
        }
    }


    /**************
     ********* Helpers
     **************/

    private static TreeHelper.SimpleClass innerClass(String name) {
        return new TreeHelper.SimpleClass("ch.epfl.systemf", "FileLogger$" + name);
    }

    private JCTree.JCExpression callInstanceExp(TreeHelper.SimpleClass clazz, String nodeId, Symbol instanceRef, List<? extends Symbol> argValues) {
        JCTree.JCExpression arr = helper.objectArray(argValues);
        JCTree.JCExpression method = callMethod(clazz, arr);
        return helper.callFun(
                method,
                List.of(mkTree.Literal(nodeId), mkTree.Literal(""), mkTree.Ident(instanceRef), arr)
        );
    }

    private JCTree.JCExpression callStaticExp(TreeHelper.SimpleClass clazz, String nodeId, String className, List<? extends Symbol> argValues) {
        JCTree.JCExpression arr = helper.objectArray(argValues);
        JCTree.JCExpression method = callMethod(clazz, arr);
        return helper.callFun(
                method,
                List.of(mkTree.Literal(nodeId), mkTree.Literal(className), helper.nullLiteral, arr)
        );

    }

    private JCTree.JCExpression callMethod(TreeHelper.SimpleClass clazz, JCTree.JCExpression arr) {
        return helper.staticMethod(clazz,
                "call",
                List.of(helper.string, helper.string, helper.objectP, arr.type),
                helper.intP);
    }

    private JCTree.JCExpression callSimpleEnter(TreeHelper.SimpleClass clazz, String nodeId) {
        return helper.callStaticMethod(
                clazz,
                "enter",
                List.of(helper.string),
                helper.intP,
                List.of(mkTree.Literal(nodeId)));
    }

    private JCTree.JCExpression callExitResult(TreeHelper.SimpleClass clazz, String nodeId, Symbol result) {

        return helper.callStaticMethod(
                clazz,
                "exit",
                List.of(helper.string, helper.objectP),
                helper.intP,
                List.of(mkTree.Literal(nodeId), mkTree.Ident(result)));
    }


    private JCTree.JCExpression callSimpleExit(TreeHelper.SimpleClass clazz, String nodeId) {

        return helper.callStaticMethod(
                clazz,
                "exit",
                List.of(helper.string),
                helper.intP,
                List.of(mkTree.Literal(nodeId)));
    }

    private TreeHelper helper;
    private TreeMaker mkTree;

    public Logger(TreeHelper helper) {
        this.helper = helper;
        mkTree = helper.mkTree;


        Type nodeId = helper.string;

    }

}
