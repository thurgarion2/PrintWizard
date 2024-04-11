package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;

public interface TraceLogger {

    public JCTree.JCStatement logReturn(JCTree.JCReturn ret, Symbol.MethodSymbol currMethod);
    
    public JCTree.JCStatement logVarDecl(JCTree.JCVariableDecl varDecl, Symbol.MethodSymbol currMethod);

    public JCTree.JCStatement logExec(JCTree.JCExpressionStatement statement, Symbol.MethodSymbol currMethod);

    public JCTree.JCStatement logForLoop(JCTree.JCForLoop loop, Symbol.MethodSymbol currMethod);

    public JCTree.JCStatement logWhileLoop(JCTree.JCWhileLoop loop, Symbol.MethodSymbol currMethod);

    public JCTree.JCStatement logIf(JCTree.JCIf branch, Symbol.MethodSymbol currMethod);

    public JCTree.JCExpression logCall(JCTree.JCMethodInvocation call, Symbol.MethodSymbol currMethod);

    public JCTree.JCExpression logUnary(JCTree.JCUnary unary, Symbol.MethodSymbol currMethod);

    public JCTree.JCMethodDecl logMethod(JCTree.JCMethodDecl method, Symbol.MethodSymbol currMethod);
}
