package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
public class TreeInstrumenter extends TreeTranslator {
    private final TraceLogger traceLogger;
    private final TreeHelper helper;
    private Symbol.MethodSymbol currentMethod = null;

    public TreeInstrumenter(TraceLogger traceLogger, TreeHelper helper){
        super();
        this.traceLogger = traceLogger;
        this.helper = helper;
    }

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
        super.visitApply(tree);
        this.result = traceLogger.logCall((JCTree.JCMethodInvocation) this.result, currentMethod);
    }

    @Override
    public void visitExec(JCTree.JCExpressionStatement tree){
        JCTree.JCExpression expr = tree.getExpression();
        if(expr instanceof JCTree.JCMethodInvocation call){

            if(call.type.equals(helper.voidP)){
                super.visitApply(call);
                this.result = traceLogger.logExec(helper.mkTree.Exec((JCTree.JCExpression) this.result), currentMethod);
            }else{
                super.visitExec(tree);
            }

            return;
        }
        super.visitExec(tree);
        if(expr instanceof JCTree.JCUnary)
            return;
        throw new UnsupportedOperationException();
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
        super.visitForLoop(tree);
        this.result = traceLogger.logForLoop((JCTree.JCForLoop) this.result, currentMethod);
    }

    @Override
    public void visitIf(JCTree.JCIf tree) {
        super.visitIf(tree);
        this.result = traceLogger.logIf((JCTree.JCIf) tree, currentMethod);
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
        this.result = traceLogger.logMethod((JCTree.JCMethodDecl) this.result, currentMethod);
    }

    @Override
    public void visitNewArray(JCTree.JCNewArray tree) {
        System.out.println("visitNewArray");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitNewClass(JCTree.JCNewClass tree) {
        System.out.println("visitNewClass");
        throw new UnsupportedOperationException();
    }
    @Override
    public void visitReference(JCTree.JCMemberReference tree) {
        System.out.println("visitReference");
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitReturn(JCTree.JCReturn tree) {
        super.visitReturn(tree);
        this.result = traceLogger.logReturn((JCTree.JCReturn) this.result, currentMethod);
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
    public void visitTypeApply(JCTree.JCTypeApply tree) {
        System.out.println("visitTypeApply");
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
    public void visitTypeParameter(JCTree.JCTypeParameter tree) {
        System.out.println("visitTypeParameter");
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
        this.result = traceLogger.logUnary((JCTree.JCUnary) this.result, currentMethod);
        int x = 0;
    }

    @Override
    public void visitVarDef(JCTree.JCVariableDecl tree) {
        super.visitVarDef(tree);
        //we are in a method parameter
        if(tree.init==null)
            return;
        this.result = traceLogger.logVarDecl((JCTree.JCVariableDecl) this.result, currentMethod);
    }

    @Override
    public void visitWhileLoop(JCTree.JCWhileLoop tree) {
        super.visitWhileLoop(tree);
        this.result = traceLogger.logWhileLoop((JCTree.JCWhileLoop) this.result, currentMethod);
    }
}
