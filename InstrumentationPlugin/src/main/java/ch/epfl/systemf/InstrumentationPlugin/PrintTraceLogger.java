package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class PrintTraceLogger implements TraceLogger{
    private final TreeHelper helper;
    private final TreeMaker mkTree;
    private int nodeCounter;

    private final JCTree.JCExpression enterLogicalEvent;
    private final JCTree.JCExpression exitLogicalEvent;
    private final JCTree.JCExpression enterEvent;
    private final JCTree.JCExpression exitLogEvent;
    private final Map<Symbol.MethodSymbol, Integer> mehtodId;


    public PrintTraceLogger(TreeHelper instr) {
        this.helper = instr;
        this.mkTree = instr.mkTree;
        this.nodeCounter = 0;
        this.mehtodId = new HashMap<>();

        TreeHelper.SimpleClass printLogger = new TreeHelper.SimpleClass("ch.epfl.systemf", "PrintLogger");



        this.enterEvent = helper.staticMethod(printLogger, "enter",
                List.of(helper.intP)
                , helper.intP);
        this.exitLogEvent = helper.staticMethod(printLogger,
                "exitEvaluation",
                List.of(instr.intP,instr.type(helper.evaluation)),
                helper.intP);

        this.enterLogicalEvent = helper.staticMethod(printLogger, "enter",
                List.of(helper.intP)
                , helper.intP);
        this.exitLogicalEvent = helper.staticMethod(printLogger, "exitLogical",
                List.of(helper.intP, helper.string)
                , helper.intP);
    }

    @Override
    public JCTree.JCStatement logReturn(JCTree.JCReturn ret, Symbol.MethodSymbol currMethod) {
        ret.expr  = enterExitEvent(ret.expr, currMethod.getReturnType(), currMethod);
        ret.expr = exitLogicalEvent(ret.expr,
                methodNodeId(currMethod),
                "exit-method",
                currMethod);
        return ret;
    }

    @Override
    public JCTree.JCStatement logVarDecl(JCTree.JCVariableDecl varDecl, Symbol.MethodSymbol currMethod) {
        if(varDecl.init==null)
            throw new IllegalArgumentException();

        varDecl.init = enterExitEvent(
                varDecl.init,
                varDecl.type,
                nextNodeId(),
                result -> helper.newEvaluation(false,null, true, varDecl.name.toString(),result),
                currMethod);
        return varDecl;
    }

    @Override
    public JCTree.JCStatement logExec(JCTree.JCExpressionStatement statement, Symbol.MethodSymbol currMethod) {
        return enterExitLogicalEvent(nextNodeId(),statement, "VOID-FUN-CALL");
    }

    private JCTree.JCExpression enterExitEvent(JCTree.JCExpression expr, Type exprType, Symbol.MethodSymbol currMethod) {
        return enterExitEvent(expr,
                exprType,
                nextNodeId(),
                result -> helper.newEvaluation(true, result, false, null, null),
                currMethod);
    }


    @Override
    public JCTree.JCStatement logForLoop(JCTree.JCForLoop loop, Symbol.MethodSymbol currMethod) {
        loop.init = enterExitLogicalEvent(nextNodeId(),loop.init, "FOR-INIT");
        loop.body = enterExitLogicalEvent(nextNodeId(),loop.body, "FOR-BODY");
        loop.cond = enterExitEvent(loop.cond, helper.boolP, currMethod);
        return enterExitLogicalEvent(nextNodeId(),loop, "FOR-LOOP");
    }

    @Override
    public JCTree.JCStatement logWhileLoop(JCTree.JCWhileLoop loop, Symbol.MethodSymbol currMethod) {
        loop.body = enterExitLogicalEvent(nextNodeId(),loop.body, "WHILE-BODY");
        loop.cond = enterExitEvent(loop.cond, helper.boolP, currMethod);
        return enterExitLogicalEvent(nextNodeId(),loop, "WHILE-LOOP");
    }

    @Override
    public JCTree.JCStatement logIf(JCTree.JCIf branch, Symbol.MethodSymbol currMethod) {
        JCTree.JCStatement then = branch.thenpart;
        JCTree.JCStatement elsePart = branch.elsepart;
        if(then!=null){
            branch.thenpart = enterExitLogicalEvent(nextNodeId(),then,"TRUE-BRANCH");
        }
        if(elsePart!=null){
            branch.elsepart = enterExitLogicalEvent(nextNodeId(),elsePart,"FALSE-BRANCH");
        }

        branch.cond = enterExitEvent(branch.cond, helper.boolP, currMethod);

        return enterExitLogicalEvent(nextNodeId(), branch, "IF");
    }

    @Override
    public JCTree.JCExpression logCall(JCTree.JCMethodInvocation call, Symbol.MethodSymbol currMethod) {
        if(call.type.equals(helper.voidP))
            throw new IllegalArgumentException("cannot assign void to variable");
        return enterExitEvent(call, call.type, currMethod);
    }

    @Override
    public JCTree.JCExpression logUnary(JCTree.JCUnary unary, Symbol.MethodSymbol currMethod) {
        JCTree.JCExpression expr = unary.getExpression();

        return enterExitEvent(unary,
                unary.type,
                nextNodeId(),
                result -> helper.newEvaluation(true,
                        result,
                        true,
                        expr.toString(),
                        ((JCTree.JCIdent)expr).sym),
                currMethod);
    }

    @Override
    public JCTree.JCMethodDecl logMethod(JCTree.JCMethodDecl method, Symbol.MethodSymbol currMethod) {
        String methodName = method.name.toString();
        JCTree.JCBlock body = method.getBody();
        int nodeId =  methodNodeId(method.sym);
        this.mehtodId.put(currMethod,nodeId);

        body.stats = enterExitLogicalEvent(nodeId, body.getStatements(), "METHOD("+methodName+")");
        return method;
    }

    private List<JCTree.JCStatement> enterExitLogicalEvent(int nodeId, List<JCTree.JCStatement> stats, String event){

        return stats
                .prepend(enterLogical(nodeId))
                .append(exitLogical(nodeId, event));
    }

    private JCTree.JCStatement enterExitLogicalEvent(int nodeId, JCTree.JCStatement stat, String event){

        return mkTree.Block(0, List.of(
                enterLogical(nodeId),
                stat,
                exitLogical(nodeId, event)
        ));
    }

    private JCTree.JCExpression exitLogicalEvent(JCTree.JCExpression expr, int nodeId, String description, Symbol.MethodSymbol method){
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
                        mkTree.VarDef(exit, callExitLogical(nodeId, description))
                ),
                mkTree.Ident(retValue)
        ).setType(expr.type);
    }

    private JCTree.JCStatement enterLogical(int nodeId){
        return mkTree.Exec(callEnterLogical(nodeId));
    }

    private JCTree.JCStatement exitLogical(int nodeId, String description){

        return mkTree.Exec(callExitLogical(nodeId, description));
    }

    private JCTree.JCExpression enterExitEvent(JCTree.JCExpression expr,
                                               Type exprType,
                                               int nodeId,
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
                        mkTree.VarDef(enter, callEnterEval(nodeId)),
                        mkTree.VarDef(retValue, expr),
                        mkTree.VarDef(exit, callExitEval(
                                nodeId,
                                evaluation.apply(retValue)))
                ),
                mkTree.Ident(retValue)
        ).setType(expr.type);
    }



    private JCTree.JCExpression callEnterEval(int nodeId){
        return helper.callFun(this.enterEvent, List.of(mkTree.Literal(nodeId)));
    }

    private JCTree.JCExpression callExitEval(int nodeId, JCTree.JCExpression eval){
        return helper.callFun(this.exitLogEvent, List.of(
                mkTree.Literal(nodeId),
                eval));
    }

    private JCTree.JCExpression callEnterLogical(int nodeId){
        return helper.callFun(this.enterLogicalEvent, List.of(mkTree.Literal(nodeId)));
    }

    private JCTree.JCExpression callExitLogical(int nodeId, String description){
        return helper.callFun(this.exitLogicalEvent,
                List.of(
                        mkTree.Literal(nodeId),
                        mkTree.Literal(description)));
    }

    private int nextNodeId(){
        return nodeCounter++;
    }

    private Integer methodNodeId(Symbol.MethodSymbol method) {
        if(!this.mehtodId.containsKey(method))
            this.mehtodId.put(method, nextNodeId());
        return this.mehtodId.get(method);
    }

}
