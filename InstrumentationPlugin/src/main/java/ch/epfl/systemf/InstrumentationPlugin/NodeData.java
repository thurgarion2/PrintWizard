package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;

public interface NodeData {
    public void dataForLoop(NodeId forLoop, NodeId init, NodeId iter, NodeId cond, JCTree.JCForLoop loop);

    public void dataWhileLoop(NodeId whileLoop, NodeId iter, NodeId cond, JCTree.JCWhileLoop loop);

    public void dataIf(NodeId iF, NodeId cond, NodeId theN, NodeId elsE, JCTree.JCIf branch);

    public void dataUnary(NodeId nodeId, JCTree.JCUnary unary);

    public void dataVarDecl(NodeId nodeId, JCTree.JCVariableDecl varDecl);

    public void dataCall(NodeId nodeId, JCTree.JCMethodInvocation call);

    public void dataReturn(NodeId nodeId, JCTree.JCReturn ret);

    public void end();

}
