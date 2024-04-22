package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;

public interface TraceLogger {

    public JCTree.JCStatement logReturn(NodeId nodeId, NodeId method, JCTree.JCReturn ret, Symbol.MethodSymbol currMethod);
    
    public JCTree.JCStatement logVarDecl(NodeId nodeId, JCTree.JCVariableDecl varDecl, Symbol.MethodSymbol currMethod);

    public JCTree.JCStatement logExec(NodeId nodeId, JCTree.JCExpressionStatement statement, Symbol.MethodSymbol currMethod);

    public JCTree.JCStatement logForLoop(NodeId forLoop, NodeId init, NodeId iter, NodeId cond, JCTree.JCForLoop loop, Symbol.MethodSymbol currMethod);

    public JCTree.JCStatement logWhileLoop(NodeId whileLoop, NodeId iter, NodeId cond, JCTree.JCWhileLoop loop, Symbol.MethodSymbol currMethod);

    public JCTree.JCStatement logIf(NodeId iF, NodeId cond, NodeId theN, NodeId elsE, JCTree.JCIf branch, Symbol.MethodSymbol currMethod);

    public JCTree.JCExpression logCall(NodeId nodeId, JCTree.JCMethodInvocation call, Symbol.MethodSymbol currMethod);

    public JCTree.JCExpression logUnary(NodeId nodeId, JCTree.JCUnary unary, Symbol.MethodSymbol currMethod);

    public JCTree.JCMethodDecl logMethod(NodeId nodeId, JCTree.JCMethodDecl method, Symbol.MethodSymbol currMethod);
}
