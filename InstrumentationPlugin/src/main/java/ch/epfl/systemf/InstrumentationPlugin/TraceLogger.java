package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;

public interface TraceLogger {

    //all methods add labels about the structure of the current node not the children

    /*******************************************************
     **************** Flow ******************
     *******************************************************/


    public JCTree.JCStatement logForLoop(JCTree.JCForLoop loop, Symbol.MethodSymbol currMethod);

    public JCTree.JCStatement logWhileLoop(JCTree.JCWhileLoop loop, Symbol.MethodSymbol currMethod);

    //before an If node
    //after an If node with else and then branch labeled
    public JCTree.JCStatement logIf(JCTree.JCIf branch, Symbol.MethodSymbol currMethod);
    public JCTree.JCStatement logIfElse(JCTree.JCIf branch, Symbol.MethodSymbol currMethod);

    public JCTree.JCMethodDecl logMethod(JCTree.JCMethodDecl method, Symbol.MethodSymbol currMethod);

    //before return node
    //after return node with exit method flow
    public JCTree.JCStatement logReturn(JCTree.JCReturn ret, Symbol.MethodSymbol currMethod);

    /*******************************************************
     **************** Statement ******************
     *******************************************************/


    //add information about update
    public JCTree.JCStatement logVarDecl(JCTree.JCVariableDecl varDecl, Symbol.MethodSymbol currMethod);

    public JCTree.JCExpression logCallStatement(JCTree.JCMethodInvocation call, Symbol.MethodSymbol currMethod);

    //necessary
    public JCTree.JCStatement logVoidCallStatement(JCTree.JCMethodInvocation call, Symbol.MethodSymbol currMethod);

    public JCTree.JCExpression logUnaryStatement(JCTree.JCUnary unary, Symbol.MethodSymbol currMethod);

    public JCTree.JCExpression logAssignStatement(JCTree.JCAssign result, Symbol.MethodSymbol currentMethod);

    public JCTree.JCExpression logStatement(JCTree.JCExpression statement, Symbol.MethodSymbol currentMethod);

    public JCTree.JCExpression logNewClassStatement(JCTree.JCNewClass call, Symbol.MethodSymbol currMethod);

    public JCTree.JCExpression logAssignOpStatement(JCTree.JCAssignOp assign, Symbol.MethodSymbol currMethod);

    /*******************************************************
     **************** Expression ******************
     *******************************************************/

    public JCTree.JCExpression logUnaryExpr(JCTree.JCUnary unary, Symbol.MethodSymbol currMethod);

}
