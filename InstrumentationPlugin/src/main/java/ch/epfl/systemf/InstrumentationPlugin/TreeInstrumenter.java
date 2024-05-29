package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;

import java.util.HashMap;
import java.util.Map;

public class TreeInstrumenter extends TreeTranslator {
    private final TraceLogger traceLogger;
    private final TreeHelper helper;
    private Symbol.MethodSymbol currentMethod = null;
    private final SourceFormat makeNodeId;


    public TreeInstrumenter(TraceLogger traceLogger, TreeHelper helper, SourceFormat makeNodeId) {
        super();
        this.traceLogger = traceLogger;
        this.helper = helper;
        this.makeNodeId = makeNodeId;
    }

    /*******************************************************
     **************** visit node ******************
     *******************************************************/

    @Override
    public void visitAnnotatedType(JCTree.JCAnnotatedType tree) {
        System.out.println("visitAnnotatedType");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitAnnotation(JCTree.JCAnnotation tree) {
        System.out.println("visitAnnotation");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        makeNodeId.nodeId(tree, tree.args);
        super.visitApply(tree);
        this.result = traceLogger.logCallStatement(
                (JCTree.JCMethodInvocation) this.result,
                currentMethod);
    }

    @Override
    public void visitExec(JCTree.JCExpressionStatement tree) {
        if (tree.expr instanceof JCTree.JCMethodInvocation call && call.type.equals(helper.voidP)) {
            result = traceLogger.logVoidCallStatement(call, currentMethod);
        } else {
            tree.expr = visitStatement(tree.expr);
            result = tree;
        }
    }

    @Override
    public void visitAssign(JCTree.JCAssign tree) {
        super.visitAssign(tree);
        System.out.println("visitAssign --- TODO");
        //TODO
    }

    @Override
    public void visitAssignop(JCTree.JCAssignOp tree) {
        super.visitAssignop(tree);
        System.out.println("visitAssignop --- TODO");
        //TODO
    }

    @Override
    public void visitBreak(JCTree.JCBreak tree) {
        System.out.println("visitBreak");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitCase(JCTree.JCCase tree) {
        System.out.println("visitCase");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitCatch(JCTree.JCCatch tree) {
        System.out.println("visitCatch");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitConditional(JCTree.JCConditional tree) {
        super.visitConditional(tree);
        System.out.println("visitConditional --- TODO");
        //TODO
    }

    @Override
    public void visitContinue(JCTree.JCContinue tree) {
        System.out.println("visitContinue");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitDoLoop(JCTree.JCDoWhileLoop tree) {
        super.visitDoLoop(tree);
        //TODO
        System.out.println("visitDoLoop --- TODO");
    }

    @Override
    public void visitErroneous(JCTree.JCErroneous tree) {
        System.out.println("visitErroneous");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitForeachLoop(JCTree.JCEnhancedForLoop tree) {
        super.visitForeachLoop(tree);
        System.out.println("visitForeachLoop --- TODO");
        //TODO
    }

    @Override
    public void visitForLoop(JCTree.JCForLoop tree) {
        tree.init = translate(tree.init);
        tree.cond = visitStatement(tree.cond);
        tree.step = translate(tree.step);
        tree.body = translate(tree.body);

        this.result = traceLogger.logForLoop(
                tree,
                currentMethod);
    }

    @Override
    public void visitIf(JCTree.JCIf tree) {
        tree.cond = visitStatement(tree.cond);
        tree.thenpart = translate(tree.thenpart);

        if (tree.elsepart instanceof JCTree.JCIf treeIf) {
            visitIf(treeIf);

            this.result = traceLogger.logIfElse(
                    tree,
                    currentMethod);
        } else {
            tree.elsepart = translate(tree.elsepart);
            this.result = traceLogger.logIf(
                    tree,
                    currentMethod);
        }
    }

    @Override
    public void visitIndexed(JCTree.JCArrayAccess tree) {
        System.out.println("visitIndexed");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitLabelled(JCTree.JCLabeledStatement tree) {
        System.out.println("visitLabelled");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitLambda(JCTree.JCLambda tree) {
        System.out.println("visitLambda");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        this.currentMethod = tree.sym;
        super.visitMethodDef(tree);
        this.result = traceLogger.logMethod(
                (JCTree.JCMethodDecl) this.result,
                currentMethod);
    }

    @Override
    public void visitNewArray(JCTree.JCNewArray tree) {
        System.out.println("visitNewArray");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitNewClass(JCTree.JCNewClass tree) {
        super.visitNewClass(tree);
        this.result = traceLogger.logNewClassStatement((JCTree.JCNewClass) this.result,
                currentMethod);
    }

    @Override
    public void visitReference(JCTree.JCMemberReference tree) {
        System.out.println("visitReference");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitReturn(JCTree.JCReturn tree) {
        tree.expr = visitStatement(tree.expr);

        this.result = traceLogger.logReturn(
                tree, currentMethod);
    }

    @Override
    public void visitSkip(JCTree.JCSkip tree) {
        System.out.println("visitSkip");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitSwitch(JCTree.JCSwitch tree) {
        System.out.println("visitSwitch");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitThrow(JCTree.JCThrow tree) {
        System.out.println("visitThrow");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitTry(JCTree.JCTry tree) {
        System.out.println("visitTry");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitTypeBoundKind(JCTree.TypeBoundKind tree) {
        System.out.println("visitTypeBoundKind");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitTypeCast(JCTree.JCTypeCast tree) {
        System.out.println("visitTypeCast");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitTypeIntersection(JCTree.JCTypeIntersection tree) {
        System.out.println("visitTypeIntersection");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitTypeTest(JCTree.JCInstanceOf tree) {
        System.out.println("visitTypeTest");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitTypeUnion(JCTree.JCTypeUnion tree) {
        System.out.println("visitTypeUnion");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitUnary(JCTree.JCUnary tree) {
        super.visitUnary(tree);
        this.result = traceLogger.logUnaryExpr(
                (JCTree.JCUnary) this.result,
                currentMethod);

    }

    @Override
    public void visitBinary(JCTree.JCBinary tree){
        makeNodeId.nodeId(tree);
        super.visitBinary(tree);
        this.result = traceLogger.logExpression(
                (JCTree.JCExpression) this.result,
                currentMethod
        );
    }

    @Override
    public void visitVarDef(JCTree.JCVariableDecl tree) {
        super.visitVarDef(tree);
        //we are in a method parameter
        if (tree.init == null)
            return;
        this.result = traceLogger.logVarDecl((JCTree.JCVariableDecl) this.result, currentMethod);
    }

    @Override
    public void visitWhileLoop(JCTree.JCWhileLoop tree) {
        tree.cond = visitStatement(tree.cond);
        assertBlock(tree.body);
        tree.body = translate(tree.body);

        this.result = traceLogger.logWhileLoop(
                tree, currentMethod);
    }

    private static void assertBlock(JCTree.JCStatement statement) {
        if (!(statement instanceof JCTree.JCBlock))
            throw new IllegalArgumentException("we expect only block for this node");
    }

    /*******************************************************
     **************** visit Expression ******************
     *******************************************************/

    private JCTree.JCExpression visitStatement(JCTree.JCExpression statement) {
        return switch (statement) {
            case JCTree.JCMethodInvocation call -> {
                makeNodeId.nodeId(call, call.args);
                super.visitApply(call);
                yield traceLogger.logCallStatement(
                        (JCTree.JCMethodInvocation) this.result,
                        currentMethod);
            }
            case JCTree.JCNewClass call -> {
                super.visitNewClass(call);
                yield traceLogger.logNewClassStatement(
                        (JCTree.JCNewClass) this.result,
                        currentMethod);
            }
            case JCTree.JCAssignOp assign -> {
                super.visitAssignop(assign);
                yield traceLogger.logAssignOpStatement(
                        (JCTree.JCAssignOp) this.result,
                        currentMethod);
            }
            case JCTree.JCIdent ident -> {
                super.visitIdent(ident);
                yield traceLogger.logStatement((JCTree.JCExpression) this.result, currentMethod);
            }
            case JCTree.JCUnary unary -> {
                super.visitUnary(unary);
                yield traceLogger.logUnaryStatement(
                        (JCTree.JCUnary) this.result,
                        currentMethod);
            }
            case JCTree.JCAssign assign -> {
                super.visitAssign(assign);
                yield traceLogger.logAssignStatement(
                        (JCTree.JCAssign) this.result,
                        currentMethod);
            }
            case JCTree.JCBinary op -> {
                super.visitBinary(op);
                yield traceLogger.logStatement(
                        (JCTree.JCExpression) this.result,
                        currentMethod);
            }
            case JCTree.JCParens parens -> {
                super.visitParens(parens);
                yield traceLogger.logStatement(
                        (JCTree.JCExpression) this.result,
                        currentMethod);
            }
            default -> throw new IllegalStateException("Unexpected value: " + statement);

        };
    }
}
