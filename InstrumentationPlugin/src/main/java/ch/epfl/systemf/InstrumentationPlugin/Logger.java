package ch.epfl.systemf.InstrumentationPlugin;

//automatically generated code do not edit refer to ApiGenerator

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;

public class Logger {

    private final TreeHelper.SimpleClass loggerClass;
    private final TreeHelper helper;
    private final TreeMaker mkTree;

    private final JCTree.JCExpression enterFlow;
    private final JCTree.JCExpression exitFlow;
    private final JCTree.JCExpression enterStatement;
    private final JCTree.JCExpression exitStatement;
    private final JCTree.JCExpression enterExpression;
    private final JCTree.JCExpression exitExpression;
    private final JCTree.JCExpression update;


    public Logger(TreeHelper.SimpleClass loggerClass, TreeHelper helper) {
        this.helper = helper;
        this.mkTree = helper.mkTree;
        this.loggerClass = loggerClass;

        Type nodeId = helper.string;

        this.enterFlow = methodTemplate("enterFlow", List.of(nodeId));
        this.exitFlow = methodTemplate("exitFlow", List.of(nodeId));
        this.enterStatement = methodTemplate("enterStatement", List.of(nodeId));
        this.exitStatement = methodTemplate("exitStatement", List.of(nodeId));
        this.enterExpression = methodTemplate("enterExpression", List.of(nodeId));
        this.exitExpression = methodTemplate("exitExpression", List.of(nodeId, helper.objectP));
        this.update = methodTemplate("update", List.of(nodeId, helper.string, helper.objectP));
    }

    private JCTree.JCExpression methodTemplate(String name, List<Type> argsTypes) {
        return helper.staticMethod(loggerClass,
                name,
                argsTypes,
                helper.intP);
    }


    /*******************************************************
     **************** API methods ******************
     *******************************************************/

    /**************
     ********* Flow methods
     **************/

    public JCTree.JCExpression enterFlow(String nodeId) {
        return helper.callFun(enterFlow, List.of(mkTree.Literal(nodeId)));
    }

    public JCTree.JCExpression exitFlow(String nodeId) {
        return helper.callFun(exitFlow, List.of(mkTree.Literal(nodeId)));
    }

    /**************
     ********* Statement methods
     **************/

    public JCTree.JCExpression enterStatement(String nodeId) {
        return helper.callFun(enterStatement, List.of(mkTree.Literal(nodeId)));
    }

    public JCTree.JCExpression exitStatement(String nodeId) {
        return helper.callFun(exitStatement, List.of(mkTree.Literal(nodeId)));
    }

    /**************
     ********* Expression methods
     **************/

    public JCTree.JCExpression enterExpression(String nodeId) {
        return helper.callFun(enterExpression, List.of(mkTree.Literal(nodeId)));
    }

    public JCTree.JCExpression exitExpression(String nodeId, Symbol result) {
        return helper.callFun(exitExpression, List.of(mkTree.Literal(nodeId), mkTree.Ident(result)));
    }

    /**************
     ********* Update methods
     **************/

    public JCTree.JCExpression update(String nodeId, String varName, Symbol value) {
        return helper.callFun(update, List.of(mkTree.Literal(nodeId), mkTree.Literal(varName), mkTree.Ident(value)));
    }
}
