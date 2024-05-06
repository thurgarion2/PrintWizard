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
    final VoidStatement voidStatement = this.new VoidStatement();
    final Update update = this.new Update();


    /**************
     ********* events
     **************/

    sealed interface Event {}

    final class SimpleFlow implements Event{
        private static final TreeHelper.SimpleClass clazz = innerClass("SimpleFlow");

        public JCTree.JCExpression enter(String nodeId){
           return callSimpleEnter(clazz, nodeId);
        }

        public JCTree.JCExpression exit(String nodeId){
            return callSimpleExit(clazz, nodeId);
        }
    }

    final class SimpleStatement implements Event{
        private static final TreeHelper.SimpleClass clazz = innerClass("SimpleStatement");

        public JCTree.JCExpression enter(String nodeId){
            return callSimpleEnter(clazz, nodeId);
        }

        public JCTree.JCExpression exit(String nodeId, Symbol result){
            return callExitResult(clazz, nodeId, result);
        }

    }

    final class VoidStatement implements Event{
        private static final TreeHelper.SimpleClass clazz = innerClass("VoidStatement");

        public JCTree.JCExpression enter(String nodeId){
            return callSimpleEnter(clazz, nodeId);
        }

        public JCTree.JCExpression exit(String nodeId){
            return callSimpleExit(clazz, nodeId);
        }

    }

    final class Update implements Event{
        private static final TreeHelper.SimpleClass clazz = innerClass("Update");


        public JCTree.JCExpression write(String nodeId, String name, Symbol value){
            return helper.callStaticMethod(
                    clazz,
                    "write",
                    List.of(helper.string, helper.string, helper.objectP),
                    helper.intP,
                    List.of(mkTree.Literal(nodeId), mkTree.Literal(name), mkTree.Ident(value)));
        }
    }


    /**************
     ********* Helpers
     **************/

    private static TreeHelper.SimpleClass innerClass(String name){
        return new TreeHelper.SimpleClass("ch.epfl.systemf", "FileLogger$"+name);
    }

    private JCTree.JCExpression callSimpleEnter(TreeHelper.SimpleClass clazz, String nodeId){
        return helper.callStaticMethod(
                clazz,
                "enter",
                List.of(helper.string),
                helper.intP,
                List.of(mkTree.Literal(nodeId)));
    }

    private JCTree.JCExpression callExitResult(TreeHelper.SimpleClass clazz, String nodeId, Symbol result){

        return helper.callStaticMethod(
                clazz,
                "enter",
                List.of(helper.string, helper.objectP),
                helper.intP,
                List.of(mkTree.Literal(nodeId), mkTree.Ident(result)));
    }


    private JCTree.JCExpression callSimpleExit(TreeHelper.SimpleClass clazz, String nodeId){

        return helper.callStaticMethod(
                clazz,
                "enter",
                List.of(helper.string),
                helper.intP,
                List.of(mkTree.Literal(nodeId)));
    }

    private  TreeHelper helper;
    private  TreeMaker mkTree;

    public Logger(TreeHelper helper) {
        this.helper = helper;
        mkTree = helper.mkTree;


        Type nodeId = helper.string;

    }

}
