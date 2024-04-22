package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;

import java.util.function.Function;

public class PrintTraceLogger implements TraceLogger{
    private final TreeHelper helper;
    private final TreeMaker mkTree;

    private final JCTree.JCExpression enterLogicalEvent;
    private final JCTree.JCExpression exitLogicalEvent;
    private final JCTree.JCExpression enterEval;
    private final JCTree.JCExpression exitLogEvent;


    public PrintTraceLogger(TreeHelper instr) {
        this.helper = instr;
        this.mkTree = instr.mkTree;
        TreeHelper.SimpleClass printLogger = new TreeHelper.SimpleClass("ch.epfl.systemf", "PrintLogger");



        this.enterEval = helper.staticMethod(printLogger, "enterEval",
                List.of(helper.intP)
                , helper.intP);
        this.exitLogEvent = helper.staticMethod(printLogger,
                "exitEvaluation",
                List.of(instr.intP,instr.type(helper.evaluation)),
                helper.intP);

        this.enterLogicalEvent = helper.staticMethod(printLogger, "enterLogical",
                List.of(helper.intP)
                , helper.intP);
        this.exitLogicalEvent = helper.staticMethod(printLogger, "exitLogical",
                List.of(helper.intP, helper.string)
                , helper.intP);
    }

    @Override
    public JCTree.JCStatement logReturn(NodeId nodeId, NodeId method, JCTree.JCReturn ret, Symbol.MethodSymbol currMethod) {
        ret.expr  = enterExitEvent(nodeId,
                ret.expr,
                currMethod.getReturnType(),
                currMethod);

        ret.expr = exitLogicalEvent(ret.expr,
                method,
                currMethod);
        return ret;
    }

    @Override
    public JCTree.JCStatement logVarDecl(NodeId nodeId, JCTree.JCVariableDecl varDecl, Symbol.MethodSymbol currMethod) {
        if(varDecl.init==null)
            throw new IllegalArgumentException();

        varDecl.init = enterExitEvent(
                varDecl.init,
                varDecl.type,
                nodeId,
                result -> helper.newEvaluation(false,null, true, varDecl.name.toString(),result),
                currMethod);
        return varDecl;
    }

    @Override
    public JCTree.JCStatement logExec(NodeId nodeId, JCTree.JCExpressionStatement statement, Symbol.MethodSymbol currMethod) {
        return enterExitLogicalEvent(nodeId,statement);
    }

    private JCTree.JCExpression enterExitEvent(NodeId id, JCTree.JCExpression expr, Type exprType, Symbol.MethodSymbol currMethod) {
        return enterExitEvent(expr,
                exprType,
                id,
                result -> helper.newEvaluation(true, result, false, null, null),
                currMethod);
    }


    @Override
    public JCTree.JCStatement logForLoop(NodeId forLoop, NodeId init, NodeId iter, NodeId cond,  JCTree.JCForLoop loop, Symbol.MethodSymbol currMethod) {
        loop.init = enterExitLogicalEvent(init,loop.init);
        loop.body = enterExitLogicalEvent(iter,loop.body);
        loop.cond = enterExitEvent(cond, loop.cond, helper.boolP, currMethod);
        return enterExitLogicalEvent(forLoop,loop);
    }

    @Override
    public JCTree.JCStatement logWhileLoop(NodeId whileLoop, NodeId iter, NodeId cond, JCTree.JCWhileLoop loop, Symbol.MethodSymbol currMethod) {
        loop.body = enterExitLogicalEvent(iter,loop.body);
        loop.cond = enterExitEvent(cond, loop.cond, helper.boolP, currMethod);
        return enterExitLogicalEvent(whileLoop ,loop);
    }

    @Override
    public JCTree.JCStatement logIf(NodeId iF, NodeId cond, NodeId theN, NodeId elsE, JCTree.JCIf branch, Symbol.MethodSymbol currMethod) {
        JCTree.JCStatement then = branch.thenpart;
        JCTree.JCStatement elsePart = branch.elsepart;
        if(then!=null){
            branch.thenpart = enterExitLogicalEvent(theN,then);
        }
        if(elsePart!=null){
            branch.elsepart = enterExitLogicalEvent(elsE,elsePart);
        }

        branch.cond = enterExitEvent(cond, branch.cond, helper.boolP, currMethod);

        return enterExitLogicalEvent(iF, branch);
    }

    @Override
    public JCTree.JCExpression logCall(NodeId nodeId, JCTree.JCMethodInvocation call, Symbol.MethodSymbol currMethod) {
        if(call.type.equals(helper.voidP))
            throw new IllegalArgumentException("cannot assign void to variable");
        return enterExitEvent(nodeId, call, call.type, currMethod);
    }

    @Override
    public JCTree.JCExpression logUnary(NodeId nodeId, JCTree.JCUnary unary, Symbol.MethodSymbol currMethod) {
        JCTree.JCExpression expr = unary.getExpression();

        return enterExitEvent(unary,
                unary.type,
                nodeId,
                result -> helper.newEvaluation(true,
                        result,
                        true,
                        expr.toString(),
                        ((JCTree.JCIdent)expr).sym),
                currMethod);
    }

    @Override
    public JCTree.JCMethodDecl logMethod(NodeId nodeId, JCTree.JCMethodDecl method, Symbol.MethodSymbol currMethod) {
        String methodName = method.name.toString();
        JCTree.JCBlock body = method.getBody();

        body.stats = enterExitLogicalEvent(nodeId, body.getStatements());
        return method;
    }

    private List<JCTree.JCStatement> enterExitLogicalEvent(NodeId id, List<JCTree.JCStatement> stats){

        return stats
                .prepend(enterLogical(id))
                .append(exitLogical(id));
    }

    private JCTree.JCStatement enterExitLogicalEvent(NodeId id, JCTree.JCStatement stat){

        return mkTree.Block(0, List.of(
                enterLogical(id),
                stat,
                exitLogical(id)
        ));
    }

    private JCTree.JCExpression exitLogicalEvent(JCTree.JCExpression expr, NodeId id, Symbol.MethodSymbol method){
        Symbol.VarSymbol exit = new Symbol.VarSymbol(0,
                helper.name("----"),
                helper.intP,
                method);
        Symbol.VarSymbol retValue = new Symbol.VarSymbol(0,
                helper.name("retValue"),
                expr.type,
                method);

        return  mkTree.LetExpr(
                List.of(mkTree.VarDef(retValue, expr),
                        mkTree.VarDef(exit, callExitLogical(id))
                ),
                mkTree.Ident(retValue)
        ).setType(expr.type);
    }

    private JCTree.JCStatement enterLogical(NodeId id){
        return mkTree.Exec(callEnterLogical(id));
    }

    private JCTree.JCStatement exitLogical(NodeId id){

        return mkTree.Exec(callExitLogical(id));
    }

    private JCTree.JCExpression enterExitEvent(JCTree.JCExpression expr,
                                               Type exprType,
                                               NodeId id,
                                               Function<Symbol, JCTree.JCExpression> evaluation,
                                               Symbol.MethodSymbol method) {

        Symbol.VarSymbol enter = new Symbol.VarSymbol(0,
                helper.name("****"),
                helper.intP,
                method);
        Symbol.VarSymbol exit = new Symbol.VarSymbol(0,
                helper.name("----"),
                helper.intP,
                method);
        Symbol.VarSymbol retValue = new Symbol.VarSymbol(0,
                helper.name("+++++"),
                exprType,
                method);


        return mkTree.LetExpr(
                List.of(
                        mkTree.VarDef(enter, callEnterEval(id)),
                        mkTree.VarDef(retValue, expr),
                        mkTree.VarDef(exit, callExitEval(
                                id,
                                evaluation.apply(retValue)))
                ),
                mkTree.Ident(retValue)
        ).setType(expr.type);
    }



    private JCTree.JCExpression callEnterEval(NodeId id){
        return helper.callFun(this.enterEval, List.of(mkTree.Literal(id.nodeId())));
    }

    private JCTree.JCExpression callExitEval(NodeId id, JCTree.JCExpression eval){
        return helper.callFun(this.exitLogEvent, List.of(
                mkTree.Literal(id.nodeId()),
                eval));
    }

    private JCTree.JCExpression callEnterLogical(NodeId id){
        return helper.callFun(this.enterLogicalEvent, List.of(mkTree.Literal(id.nodeId())));
    }

    private JCTree.JCExpression callExitLogical(NodeId id){
        return helper.callFun(this.exitLogicalEvent,
                List.of(
                        mkTree.Literal(id.nodeId()),
                        mkTree.Literal("")));
    }

}
